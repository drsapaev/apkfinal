package com.aistudio.clinicsystem.data.session

import android.content.Context
import com.aistudio.clinicsystem.data.db.ClinicDatabase
import com.aistudio.clinicsystem.data.db.UserEntity
import com.aistudio.clinicsystem.data.model.UserRole
import com.aistudio.clinicsystem.utils.SessionManager
import com.aistudio.clinicsystem.utils.TokenManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stage 2.2: SessionRepository — the SINGLE source of truth for auth state.
 *
 * Design goals (closes audit findings H-6, M-5, NET-1, NET-19, PERF-1):
 *
 * 1. **One StateFlow<SessionState> per application.** ViewModels observe
 *    this; they no longer maintain their own `_currentUser` flow.
 *
 * 2. **No mutable global vars on ApiClient.** The token provider is a
 *    lambda that reads directly from this repository — there is no
 *    `ApiClient.tokenProvider = ...` assignment.
 *
 * 3. **Async session restore on construction.** The repository starts in
 *    `Loading`, attempts to verify any cached tokens against the backend
 *    via [AuthRepository.verifyCurrentSession], and transitions to
 *    `Authenticated` / `Unauthenticated` accordingly. This avoids the
 *    ClinicViewModel.init synchronous network call (PERF-1).
 *
 * 4. **Distinguished SessionExpired state.** When the refresh token is
 *    rejected, the state becomes `SessionExpired` (not `Unauthenticated`)
 *    so the UI can show a "Your session has expired" dialog before
 *    routing to AuthScreen.
 *
 * 5. **Single coroutine scope.** All session-internal work runs in an
 *    application-scoped [SupervisorJob] — independent of any ViewModel's
 *    lifecycle. This fixes M-5 (ApiClient.onUnauthorized capturing a dead
 *    viewModelScope).
 *
 * Thread safety: [MutableStateFlow] is thread-safe; all mutations go
 * through [_sessionState.value = ...] or [_sessionState.update { ... }].
 */
@Singleton
class SessionRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val sessionManager: SessionManager,
    private val database: ClinicDatabase,
    // AuthRepository is injected lazily through a provider to break the
    // circular dependency: AuthRepository needs SessionRepository (for
    // token refresh callbacks), and SessionRepository needs AuthRepository
    // (for verifyCurrentSession on restore). Hilt resolves this with
    // Lazy<AuthRepository> — see AppModule.provideSessionRepository.
) {
    companion object {
        private const val TAG = "SessionRepository"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Loading)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    /**
     * Dynamic token provider for OkHttp AuthInterceptor.
     * Reads the current access token from [SessionManager] — no separate
     * mutable var on ApiClient. Closes M-5.
     */
    val tokenProvider: () -> String? = { sessionManager.getToken() }

    /** Current access token (or null). Read by TokenAuthenticator. */
    val accessToken: String? get() = sessionManager.getToken()

    /** Current refresh token (or null). Read by TokenAuthenticator. */
    val refreshToken: String? get() = sessionManager.getRefreshToken()

    /** Current user phone (or null). */
    val phone: String? get() = sessionManager.getPhone()

    /** Convenience: current user role (or null). */
    val role: String? get() = sessionManager.getRole()

    /** Convenience: is the user fully logged in? */
    val isLoggedIn: Boolean get() = sessionManager.isLoggedIn()

    /**
     * Called by [com.aistudio.clinicsystem.ClinicSystemApplication] at app
     * startup. Attempts to restore the session from EncryptedSharedPreferences
     * and verifies it against the backend.
     *
     * If no cached tokens exist → transitions to Unauthenticated immediately.
     * If tokens exist → transitions to Authenticated with cached Room user,
     * then asynchronously verifies; on verification failure (non-network
     * error) transitions to SessionExpired.
     *
     * This MUST be called from Application.onCreate, NOT from a ViewModel.
     * Closes PERF-1 (no synchronous network call in ClinicViewModel.init).
     */
    fun restoreSession(authRepository: com.aistudio.clinicsystem.data.repository.AuthRepository) {
        if (!sessionManager.isLoggedIn()) {
            _sessionState.value = SessionState.Unauthenticated
            return
        }

        // Optimistic: show cached user immediately
        val cachedPhone = sessionManager.getPhone()
        val cachedUser: UserEntity? = cachedPhone?.let {
            kotlinx.coroutines.runBlocking { database.userDao().getUserByPhone(it) }
        }
        val accessToken = sessionManager.getToken() ?: ""
        val refreshToken = sessionManager.getRefreshToken() ?: ""
        _sessionState.value = SessionState.Authenticated(
            user = cachedUser,
            accessToken = accessToken,
            refreshToken = refreshToken,
        )

        // Async verify — if the cached token is dead, downgrade to SessionExpired.
        // Network errors do NOT log the user out (offline-first).
        scope.launch {
            try {
                val result = authRepository.verifyCurrentSession()
                result.onSuccess { userDto ->
                    val updated = database.userDao().getUserByPhone(userDto.phone)
                    _sessionState.value = SessionState.Authenticated(
                        user = updated,
                        accessToken = sessionManager.getToken() ?: accessToken,
                        refreshToken = sessionManager.getRefreshToken() ?: refreshToken,
                    )
                }.onFailure { error ->
                    val isNetworkError = error is java.io.IOException
                    if (isNetworkError) {
                        // Keep the cached session — offline-first
                        android.util.Log.i(TAG, "Network error during session verify; keeping cached session")
                    } else {
                        // Token definitively rejected by server
                        android.util.Log.w(TAG, "Session rejected by server: ${error.localizedMessage}")
                        invalidate()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Session restore failed", e)
                // Keep cached session on any unexpected error — don't log user out
            }
        }
    }

    /**
     * Called after successful login (no 2FA required).
     * Stores tokens, fetches user profile, transitions to Authenticated.
     */
    fun onLoginSuccess(
        accessToken: String,
        refreshToken: String,
        user: UserEntity,
    ) {
        sessionManager.setTokens(accessToken, refreshToken)
        sessionManager.saveSession(accessToken, user.phone, user.role)
        _sessionState.value = SessionState.Authenticated(
            user = user,
            accessToken = accessToken,
            refreshToken = refreshToken,
        )
    }

    /**
     * Called when the backend returns a 2FA challenge after login.
     * Stores the challenge token; transitions to RequiresTwoFactor.
     */
    fun onTwoFAChallenge(challengeToken: String, phone: String) {
        _sessionState.value = SessionState.RequiresTwoFactor(
            challengeToken = challengeToken,
            phone = phone,
        )
    }

    /**
     * Called after successful 2FA verification.
     * Exchanges the challenge token for full access+refresh tokens.
     */
    fun onTwoFAVerified(
        accessToken: String,
        refreshToken: String,
        user: UserEntity,
    ) {
        onLoginSuccess(accessToken, refreshToken, user)
    }

    /**
     * Called after token refresh succeeds (from TokenAuthenticator).
     * Updates tokens WITHOUT changing the cached user — the user is
     * still valid; only the access token was rotated.
     */
    fun onTokensRefreshed(newAccessToken: String, newRefreshToken: String) {
        sessionManager.setTokens(newAccessToken, newRefreshToken)
        // Update only the tokens in the current Authenticated state
        val current = _sessionState.value
        if (current is SessionState.Authenticated) {
            _sessionState.value = current.copy(
                accessToken = newAccessToken,
                refreshToken = newRefreshToken,
            )
        }
    }

    /**
     * Called after profile is re-fetched (e.g. after biometric login, or
     * after profile update). Updates the cached user.
     */
    fun onProfileLoaded(user: UserEntity) {
        val current = _sessionState.value
        if (current is SessionState.Authenticated) {
            _sessionState.value = current.copy(user = user)
        } else {
            // First profile load after token-only restore
            _sessionState.value = SessionState.Authenticated(
                user = user,
                accessToken = sessionManager.getToken() ?: "",
                refreshToken = sessionManager.getRefreshToken() ?: "",
            )
        }
    }

    /**
     * Called by TokenAuthenticator when the refresh token is rejected
     * by the backend (401 on /refresh). Transitions to SessionExpired.
     *
     * The UI observes this and shows a "Your session has expired" dialog
     * before transitioning to Unauthenticated (via [acknowledgeSessionExpired]).
     */
    fun invalidate() {
        sessionManager.clearSession()
        _sessionState.value = SessionState.SessionExpired
    }

    /**
     * Called by the UI after the "Session expired" dialog has been shown
     * and acknowledged. Transitions to Unauthenticated so the UI routes
     * to AuthScreen.
     */
    fun acknowledgeSessionExpired() {
        if (_sessionState.value is SessionState.SessionExpired) {
            _sessionState.value = SessionState.Unauthenticated
        }
    }

    /**
     * Called on explicit user logout. Clears tokens and transitions to
     * Unauthenticated (NOT SessionExpired — the user chose to log out,
     * so no "session expired" dialog).
     */
    fun clearSession() {
        sessionManager.clearSession()
        _sessionState.value = SessionState.Unauthenticated
    }
}
