package com.aistudio.clinicsystem.data.api

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Stage 3.3 (H-1 fix): IdempotencyInterceptor — adds the `Idempotency-Key`
 * HTTP header to all POST / PUT / PATCH requests that mutate PHI.
 *
 * The header value is read from the request's existing `clientRequestId`
 * query parameter OR the request tag ([IdempotencyKey] data class). If
 * neither is present, no header is added (the request is non-idempotent
 * by design — e.g. login, where the server returns a fresh token each time).
 *
 * Server contract:
 *   - The backend MUST dedup requests with the same `Idempotency-Key`
 *     within a 24-hour window, returning the original response.
 *   - This prevents duplicate writes when SyncWorker retries a request
 *     that already succeeded server-side but whose response was lost
 *     (network blip, app crash mid-flight).
 *
 * The header value is a UUID v4 string. It is generated client-side and
 * stored in [PendingSyncEntity.clientRequestId] before the first attempt.
 *
 * Reference: https://developer.squareup.com/blog/idempotency-keys-for-apis/
 */
class IdempotencyInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val method = request.method.uppercase()

        // Only add the header to mutating methods.
        if (method !in MUTATING_METHODS) {
            return chain.proceed(request)
        }

        // If the request already has an Idempotency-Key (e.g. a retry), respect it.
        if (request.header(HEADER_NAME) != null) {
            return chain.proceed(request)
        }

        // Read the key from the request tag (set by ClinicRepository when
        // building the request). If absent, no idempotency guarantee —
        // proceed without the header.
        val key = request.tag(IdempotencyKey::class.java)?.value
            ?: return chain.proceed(request)

        val idempotentRequest = request.newBuilder()
            .header(HEADER_NAME, key)
            .build()
        return chain.proceed(idempotentRequest)
    }

    companion object {
        const val HEADER_NAME = "Idempotency-Key"
        private val MUTATING_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")
    }
}

/**
 * Request tag carrying the idempotency key. Set by [ClinicRepository] when
 * building a mutating request that goes through the outbox.
 *
 * Usage:
 *   val request = Request.Builder()
 *       .url(...)
 *       .tag(IdempotencyKey::class.java, IdempotencyKey(outboxRow.clientRequestId))
 *       .build()
 *
 * Or via Retrofit `@Tag` parameter:
 *   @POST("api/v1/appointments")
 *   suspend fun createAppointment(
 *       @Body dto: AppointmentDto,
 *       @Tag idempotencyKey: IdempotencyKey,
 *   ): Response<AppointmentDto>
 */
data class IdempotencyKey(val value: String)
