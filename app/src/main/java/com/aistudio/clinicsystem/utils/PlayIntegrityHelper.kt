package com.aistudio.clinicsystem.utils

import android.content.Context
import com.google.android.play.integrity.IntegrityManager
import com.google.android.play.integrity.IntegrityManagerFactory
import com.google.android.play.integrity.IntegrityTokenRequest
import com.google.android.play.integrity.IntegrityTokenResponse
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Stage 4.6 (M-7, M-8 build fix): PlayIntegrityHelper — wraps the Play
 * Integrity API to verify device integrity at sensitive moments.
 *
 * Use cases:
 *   - At login — block rooted/modified devices from authenticating
 *   - Before creating a medical record — block tampered clients from
 *     injecting fake PHI
 *   - Before token refresh — block devices with revoked Play protection
 *
 * Flow:
 *   1. App requests an integrity token from the Play Integrity API.
 *   2. App sends the token to the backend.
 *   3. Backend decrypts the token (using the Google Play Console
 *      Integrity API decryption key) and verifies:
 *        - MEETS_DEVICE_INTEGRITY (not rooted, not emulator)
 *        - MEETS_BASIC_INTEGRITY (Play Protect certified)
 *        - appPackageName matches
 *        - appVersionCode matches
 *   4. Backend rejects the request if any check fails.
 *
 * The decryption + verification MUST happen on the backend — the client
 * cannot verify its own integrity (a tampered client could lie).
 *
 * This helper ONLY requests the token. The backend ticket for verification
 * is tracked separately (Stage 9 — Enterprise).
 *
 * Reference: https://developer.android.com/google/play/integrity
 */
class PlayIntegrityHelper(private val context: Context) {

    /**
     * Requests a Play Integrity token. The token is opaque to the client —
     * it's a signed, encrypted blob that only the backend (with the
     * decryption key from Play Console) can read.
     *
     * @param nonce a server-generated random string (>= 16 bytes, base64-encoded).
     *   The backend verifies the nonce inside the decrypted token to prevent
     *   replay attacks. The nonce MUST be generated on the server, not on
     *   the client.
     *
     * @return the integrity token string, or null if the request failed
     *   (Play services unavailable, network error, etc.). The caller should
     *   treat null as "integrity check failed" and refuse the operation.
     */
    suspend fun requestIntegrityToken(nonce: String): String? {
        return try {
            val integrityManager: IntegrityManager =
                IntegrityManagerFactory.create(context)

            val tokenResponse: IntegrityTokenResponse = suspendCancellableCoroutine { cont ->
                val request = IntegrityTokenRequest.builder()
                    .setNonce(nonce)
                    .build()

                integrityManager.requestIntegrityToken(request)
                    .addOnSuccessListener { response ->
                        if (cont.isActive) cont.resume(response)
                    }
                    .addOnFailureListener { e ->
                        Timber.e(e, "Play Integrity token request failed")
                        if (cont.isActive) cont.resumeWithException(e)
                    }
            }

            tokenResponse.token()
        } catch (e: Exception) {
            Timber.e(e, "Play Integrity token request failed — treating as integrity failure")
            null
        }
    }

    /**
     * Convenience method: requests an integrity token with NO nonce.
     * Use this only for non-sensitive operations (e.g. startup attestation).
     * For sensitive operations (login, create medical record), the backend
     * MUST generate a nonce and pass it to [requestIntegrityToken].
     *
     * Returns true if the token was successfully requested (does NOT
     * verify the token — that's the backend's job).
     */
    suspend fun isIntegrityAvailable(): Boolean {
        return try {
            val token = requestIntegrityToken(nonce = "")
            token != null
        } catch (e: Exception) {
            false
        }
    }
}
