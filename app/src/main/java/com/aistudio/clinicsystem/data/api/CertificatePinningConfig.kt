package com.aistudio.clinicsystem.data.api

import okhttp3.CertificatePinner
import java.util.concurrent.TimeUnit

/**
 * Stage 4.3 (H-3 build fix): Certificate Pinning configuration.
 *
 * Pins are SHA-256 hashes of the server's public key (subjectPublicKeyInfo).
 * Even if an attacker obtains a valid certificate from a trusted CA (e.g.
 * via a compromised CA, a corporate MDM CA, or a government CA), they
 * cannot MITM the connection without the matching private key.
 *
 * Pin management:
 *   - Pins are read from BuildConfig fields, which are set via gradle
 *     properties `clinic.certPin1` and `clinic.certPin2` (backup pin).
 *   - The backup pin is for the NEXT key rotation — when the server
 *     rotates its certificate, the new pin is already in the APK, so
 *     the app keeps working without an update.
 *   - If the gradle properties are NOT set, NO pins are configured.
 *     This is the development mode (debug builds against 10.0.2.2 don't
 *     need pinning). Release builds MUST set the pins via CI secrets —
 *     the release-smoke CI job verifies this.
 *
 * To extract the current pin from a server:
 *   echo | openssl s_client -connect api.clinic.tld:443 2>/dev/null | \
 *     openssl x509 -pubkey -noout | \
 *     openssl pkey -pubin -outform der | \
 *     openssl dgst -sha256 -binary | \
 *     openssl enc -base64
 *
 * Reference: https://square.github.io/okhttp/4.x/okhttp/okhttp3/-certificate-pinner/
 */
object CertificatePinningConfig {

    /**
     * Builds a [CertificatePinner] from the configured pins. Returns null
     * if no pins are configured (development mode).
     *
     * The hostnames are derived from the production BASE_URL — we pin
     * to whatever host the release build points at. Multiple hostnames
     * can share the same pin set (e.g. `api.clinic.tld` and
     * `www.clinic.tld` if both serve the API).
     */
    fun buildPinner(): CertificatePinner? {
        val pin1 = com.aistudio.clinicsystem.BuildConfig.CERT_PIN_PRIMARY
            .takeIf { it.isNotBlank() && it != "UNSET" } ?: return null
        val pin2 = com.aistudio.clinicsystem.BuildConfig.CERT_PIN_BACKUP
            .takeIf { it.isNotBlank() && it != "UNSET" }
        val host = extractHost(com.aistudio.clinicsystem.BuildConfig.BASE_URL)
            ?: return null

        return CertificatePinner.Builder()
            .apply {
                add(host, "sha256/$pin1")
                // Backup pin — for the next key rotation. Without a backup
                // pin, rotating the server cert would brick every installed
                // copy of the app until users update.
                if (pin2 != null) {
                    add(host, "sha256/$pin2")
                }
            }
            .build()
    }

    /**
     * Extracts the hostname from a URL string. Returns null if the URL
     * is malformed or is a placeholder (e.g. `INVALID.unset-base-url.example`).
     */
    private fun extractHost(url: String): String? {
        return try {
            val uri = java.net.URI(url)
            val host = uri.host ?: return null
            // Reject placeholder hosts — they don't have real pins.
            if (host.contains("example.com") || host.contains("INVALID") ||
                host.contains("10.0.2.2") || host.contains("localhost")
            ) {
                null
            } else {
                host
            }
        } catch (e: Exception) {
            null
        }
    }
}
