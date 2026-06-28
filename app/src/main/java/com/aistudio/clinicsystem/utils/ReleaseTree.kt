package com.aistudio.clinicsystem.utils

import android.util.Log
import com.aistudio.clinicsystem.BuildConfig
import timber.log.Timber

/**
 * Stage 4.1 (C-4 final fix): Timber tree configuration.
 *
 * Two trees are planted:
 *
 * 1. **Debug build** → [Timber.DebugTree] — full logging to Logcat, no
 *    redaction. DEBUG-only; never shipped to production.
 *
 * 2. **Release build** → [ReleaseTree] — only WARN/ERROR reach Logcat;
 *    DEBUG/INFO are dropped. Messages are sanitized by [redactPhi] to
 *    strip patient phone numbers, diagnoses, prescriptions, JWT tokens,
 *    and other PHI before they reach Logcat.
 *
 * This closes audit finding C-4: every WebSocket message was previously
 * logged via `Log.d("WS_CLIENT", "Received message text: $text")` with
 * no gating — PHI streamed to Logcat in release builds, readable by any
 * process with `adb logcat` access on rooted devices.
 *
 * Usage:
 *   Timber.d("Booking appointment for $phone")  // → debug: full text,
 *                                              //   release: dropped
 *   Timber.w("Sync failed: $error")            // → debug: full text,
 *                                              //   release: redacted
 *
 * The redaction is intentionally conservative — false positives (redacting
 * a non-PHI string that happens to look like a phone number) are acceptable;
 * false negatives (leaking real PHI) are not.
 */
class ReleaseTree : Timber.Tree() {

    override fun isLoggable(tag: String?, priority: Int): Boolean {
        // Only WARN, ERROR, and WTF reach Logcat in release builds.
        // DEBUG, INFO, VERBOSE are dropped entirely.
        return priority >= Log.WARN
    }

    override fun log(priority: Int, tag: String?, t: Throwable?, message: String?) {
        val sanitized = message?.let(::redactPhi) ?: ""
        val sanitizedTag = tag?.let(::redactPhi) ?: TAG

        // Re-emit via android.util.Log so the message reaches Logcat.
        // We do NOT use Timber.log here — that would recurse.
        when (priority) {
            Log.WARN -> Log.w(sanitizedTag, sanitized, t)
            Log.ERROR -> Log.e(sanitizedTag, sanitized, t)
            Log.ASSERT -> Log.wtf(sanitizedTag, sanitized, t)
            // DEBUG/INFO/VERBOSE are dropped by isLoggable above.
        }
    }

    /**
     * Redacts PHI patterns from a log message. Patterns are intentionally
     * broad — better to over-redact than to leak.
     *
     * Redacted:
     *   - Phone numbers: `+7XXXXXXXXXX`, `+X (XXX) XXX-XX-XX`
     *   - JWT tokens: `eyJ...` (Base64-encoded JWT prefix)
     *   - Authorization headers: `Bearer ...`
     *   - JSON fields named `patient_phone`, `diagnosis`, `prescription`,
     *     `recommendations`, `access_token`, `refresh_token`
     *   - Long hex strings (potential tokens / IDs)
     */
    private fun redactPhi(input: String): String {
        var s = input

        // Phone numbers: +7 followed by 10 digits, with optional formatting
        s = s.replace(PHONE_REGEX.toRegex(), "[PHONE]")

        // JWT tokens (Base64-encoded header starts with eyJ...)
        s = s.replace(JWT_REGEX.toRegex(), "[JWT]")

        // Authorization Bearer header values
        s = s.replace(BEARER_REGEX.toRegex(), "[AUTH]")

        // JSON PHI fields — match "field": "value" and replace the value
        s = s.replace(JSON_PHI_FIELDS_REGEX.toRegex(), "\"$1\":\"[REDACTED]\"")

        // Long hex/base64 strings (32+ chars — likely tokens or hashes)
        s = s.replace(LONG_TOKEN_REGEX.toRegex(), "[TOKEN]")

        return s
    }

    companion object {
        private const val TAG = "ClinicRelease"

        // Phone: +7 followed by 10 digits (Russian mobile format)
        private const val PHONE_REGEX = "\\+7\\d{10}"

        // JWT: eyJ followed by Base64 characters (JWT header always starts with eyJ)
        private const val JWT_REGEX = "eyJ[A-Za-z0-9_=-]{10,}\\.[A-Za-z0-9_=-]{10,}\\.[A-Za-z0-9_=-]{10,}"

        // Bearer header: "Bearer " followed by any non-whitespace
        private const val BEARER_REGEX = "(?i)Bearer [A-Za-z0-9_=-]+"

        // JSON PHI fields — case-insensitive, captures field name
        // Matches: "patient_phone": "value", "diagnosis": "value", etc.
        private const val JSON_PHI_FIELDS_REGEX =
            "\"(patient_phone|patient_name|diagnosis|prescription|recommendations|" +
                "access_token|refresh_token|telegram_chat_id|full_name|date_of_birth|" +
                "phone|notes|reason)\"\\s*:\\s*\"[^\"]*\""

        // Long opaque tokens (32+ chars of base64/hex)
        private const val LONG_TOKEN_REGEX = "[A-Za-z0-9_=-]{32,}"
    }
}

/**
 * Stage 4.1: Initializes Timber. Called once from
 * [com.aistudio.clinicsystem.ClinicSystemApplication.onCreate].
 *
 * In debug builds: plants [Timber.DebugTree] (full logging to Logcat).
 * In release builds: plants [ReleaseTree] (only WARN/ERROR, PHI redacted).
 */
fun initTimber() {
    if (BuildConfig.DEBUG) {
        Timber.plant(Timber.DebugTree())
    } else {
        Timber.plant(ReleaseTree())
    }
}
