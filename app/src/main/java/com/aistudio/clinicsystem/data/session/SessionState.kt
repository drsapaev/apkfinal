package com.aistudio.clinicsystem.data.session

import com.aistudio.clinicsystem.data.model.UserRole
import com.aistudio.clinicsystem.data.db.UserEntity

/**
 * Stage 2.2: Sealed hierarchy of session states.
 *
 * This is the SINGLE source of truth for "what is the user's auth status?".
 * ViewModels and UI observe [SessionRepository.sessionState] and react to
 * state transitions — they no longer maintain their own `_currentUser`
 * MutableStateFlow.
 *
 * The previous design had four independent sources of "is the user logged
 * in?": ClinicViewModel._currentUser, AuthViewModel._phoneInput,
 * ApiClient.tokenProvider, and SessionManager.isLoggedIn(). Any of them
 * could drift out of sync — see audit findings H-6, M-5, NET-1.
 *
 * Transitions:
 *   Loading  →  Authenticated | RequiresTwoFactor | Unauthenticated
 *   Authenticated  →  SessionExpired  →  Unauthenticated
 *   RequiresTwoFactor  →  Authenticated | Unauthenticated
 *   Unauthenticated  →  Loading (on login attempt)
 */
sealed interface SessionState {
    /**
     * Initial state and any state where we're waiting for an async result
     * (e.g. restoring session from disk, calling /profile to verify token).
     * The UI shows a splash / spinner; navigation is parked.
     */
    data object Loading : SessionState

    /**
     * No valid session. The UI routes to AuthScreen.
     */
    data object Unauthenticated : SessionState

    /**
     * Login succeeded but the backend returned a 2FA challenge token.
     * The UI routes to the 2FA verification screen. [challengeToken] is the
     * short-lived token the backend issued; it must be exchanged for full
     * access+refresh tokens via /api/v1/2fa/verify.
     */
    data class RequiresTwoFactor(val challengeToken: String, val phone: String) : SessionState

    /**
     * Fully authenticated. [user] is the cached Room entity (may be null on
     * first login before the profile is fetched); [accessToken] /
     * [refreshToken] are the current JWTs.
     *
     * [user] being null here means "we have valid tokens but haven't loaded
     * the user profile yet" — the UI should show a spinner.
     */
    data class Authenticated(
        val user: UserEntity?,
        val accessToken: String,
        val refreshToken: String,
    ) : SessionState

    /**
     * The backend rejected the refresh token (401 on /refresh, or the
     * access token expired and refresh failed). The UI shows a
     * "Your session has expired" dialog before transitioning to
     * Unauthenticated. Distinct from Unauthenticated so the UI can show a
     * different message than "you logged out manually".
     */
    data object SessionExpired : SessionState
}

/**
 * The role derived from the current session state. Convenience accessor
 * for code that only cares about role-based branching.
 */
val SessionState.role: UserRole?
    get() = when (this) {
        is SessionState.Authenticated -> this.user?.role?.let { UserRole.fromBackend(it) }
        else -> null
    }

/**
 * True if the user is fully authenticated (not in 2FA, not loading, not
 * expired). Convenience for code that just needs a boolean gate.
 */
val SessionState.isAuthenticated: Boolean
    get() = this is SessionState.Authenticated

/**
 * True if the access token is valid AND a user profile has been loaded
 * into the cache. The UI may show the dashboard.
 */
val SessionState.isReady: Boolean
    get() = this is SessionState.Authenticated && this.user != null
