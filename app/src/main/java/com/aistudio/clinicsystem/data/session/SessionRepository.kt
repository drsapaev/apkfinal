package com.aistudio.clinicsystem.data.session

import com.aistudio.clinicsystem.data.model.UserRole
import com.aistudio.clinicsystem.utils.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * M3B.1: SessionRepository — Single Source of Truth for auth state.
 *
 * Replaces the fragmented session management where:
 *   - SessionManager (singleton) stored tokens in EncryptedSharedPreferences
 *   - ClinicViewModel held _currentUser / _currentRole StateFlows
 *   - ApiClient.tokenProvider was a separate lambda
 *   - ClinicWebSocketClient read tokens via SessionManagerImpl.getInstance()
 *   - SyncWorker read tokens via SessionManagerImpl.getInstance()
 *   - AuthRepository called sessionManager.saveSession / setTokens / clearSession
 *
 * Now ALL session state flows through SessionRepository:
 *   - Token storage (delegates to SessionManager → TokenManager → EncryptedSharedPreferences)
 *   - Current user state (StateFlow<UserRole?>)
 *   - Login state (StateFlow<LoginState>)
 *   - Token provider for OkHttp interceptors (via tokenProvider property)
 *
 * ViewModels, Workers, and WebSocketClient observe [sessionState] instead of
 * each maintaining their own copy of "is the user logged in?".
 *
 * Thread-safe: all mutations go through synchronized blocks or StateFlow.
 */
class SessionRepository(
    private val sessionManager: SessionManager
) {
    private val _sessionState = MutableStateFlow<SessionState>(SessionState.LoggedOut)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    /** Dynamic token provider for OkHttp AuthInterceptor. */
    val tokenProvider: () -> String? = { sessionManager.getToken() }

    /** Current access token (or null if not logged in). */
    val accessToken: String? get() = sessionManager.getToken()

    /** Current refresh token (or null if not logged in). */
    val refreshToken: String? get() = sessionManager.getRefreshToken()

    /** Current user phone (or null if not logged in). */
    val phone: String? get() = sessionManager.getPhone()

    /** Current user role (or null if not logged in). */
    val role: String? get() = sessionManager.getRole()

    /** Whether the user is currently logged in. */
    val isLoggedIn: Boolean get() = sessionManager.isLoggedIn()

    /**
     * Called after successful login / token refresh.
     * Stores both tokens and updates the session state.
     */
    fun onTokensRefreshed(accessToken: String, refreshToken: String) {
        sessionManager.setTokens(accessToken, refreshToken)
        _sessionState.value = SessionState.LoggedIn(
            phone = sessionManager.getPhone() ?: "",
            role = UserRole.fromBackend(sessionManager.getRole())
        )
    }

    /**
     * Called after profile is fetched (login or session restore).
     * Stores phone + role and updates the session state.
     */
    fun onProfileLoaded(phone: String, role: String) {
        sessionManager.saveSession(
            token = sessionManager.getToken() ?: "",
            phone = phone,
            role = role
        )
        _sessionState.value = SessionState.LoggedIn(
            phone = phone,
            role = UserRole.fromBackend(role)
        )
    }

    /**
     * Called on logout / session expiry.
     * Clears all stored tokens and user data.
     */
    fun clearSession() {
        sessionManager.clearSession()
        _sessionState.value = SessionState.LoggedOut
    }

    /**
     * Called on app startup to restore session from EncryptedSharedPreferences.
     * If tokens exist, sets state to LoggedIn (will be verified by backend).
     */
    fun restoreSession() {
        if (sessionManager.isLoggedIn()) {
            _sessionState.value = SessionState.LoggedIn(
                phone = sessionManager.getPhone() ?: "",
                role = UserRole.fromBackend(sessionManager.getRole())
            )
        } else {
            _sessionState.value = SessionState.LoggedOut
        }
    }
}

/**
 * Represents the current authentication state.
 * ViewModels observe this to drive navigation and UI.
 */
sealed class SessionState {
    object LoggedOut : SessionState()
    data class LoggedIn(
        val phone: String,
        val role: UserRole
    ) : SessionState()
}
