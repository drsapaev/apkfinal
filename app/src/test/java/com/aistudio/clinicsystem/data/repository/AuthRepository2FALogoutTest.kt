package com.aistudio.clinicsystem.data.repository

import androidx.test.core.app.ApplicationProvider
import com.aistudio.clinicsystem.data.api.ApiService
import com.aistudio.clinicsystem.data.api.LoginResponse
import com.aistudio.clinicsystem.data.api.MobileApiService
import com.aistudio.clinicsystem.data.api.RefreshTokenResponse
import com.aistudio.clinicsystem.data.api.TwoFARecoveryResponse
import com.aistudio.clinicsystem.data.db.ClinicDatabase
import com.aistudio.clinicsystem.data.session.SessionRepository
import com.aistudio.clinicsystem.utils.SessionManagerImpl
import io.mockk.coEvery
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * M1/E3.4 + E3.3 regression tests:
 *  - login() with a 2FA-challenge response → returns LoginOutcome.TwoFactorRequired
 *  - verify2FA() with correct code → returns LoginOutcome.Success
 *  - logout() calls POST /authentication/logout on the backend
 *
 * Stage 2.11: ApiClient is no longer an `object` (Hilt @Singleton class).
 * Tests now construct AuthRepository with the MockWebServer-backed
 * MobileApiService directly — no mockkObject(ApiClient).
 *
 * Strategy:
 *  - MockWebServer returns canned JSON for each endpoint.
 *  - mobileApiService is a Retrofit interface pointing at MockWebServer,
 *    passed to AuthRepository via constructor.
 *  - SessionManager singleton is reset before each test.
 *  - SessionRepository is constructed with the (real) SessionManager and
 *    (real) ClinicDatabase — no Hilt needed in unit tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class AuthRepository2FALogoutTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var mobileApiService: MobileApiService
    private lateinit var repository: AuthRepository
    private lateinit var context: android.content.Context
    private lateinit var db: ClinicDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockWebServer = MockWebServer().apply { start() }

        mobileApiService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(MobileApiService::class.java)

        // Reset SessionManager singleton
        try {
            val companionInstance = SessionManagerImpl::class.java
                .getDeclaredField("Companion")
                .apply { isAccessible = true }
                .get(SessionManagerImpl) as Any
            val instanceField = companionInstance.javaClass.getDeclaredField("instance")
            instanceField.isAccessible = true
            instanceField.set(companionInstance, null)
        } catch (e: Exception) {
            // best-effort
        }

        db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            ClinicDatabase::class.java
        ).allowMainThreadQueries().build()

        // Stage 2.11: SessionRepository now takes (context, sessionManager, database).
        // No Hilt in unit tests — construct manually.
        val sessionRepo = SessionRepository(
            appContext = context,
            sessionManager = SessionManagerImpl.getInstance(context),
            database = db,
        )

        repository = AuthRepository(
            context = context,
            database = db,
            mobileApiService = mobileApiService,
            apiService = io.mockk.mockk(relaxed = true),
            sessionRepository = sessionRepo,
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        db.close()
    }

    @Test
    fun login_2faRequired_returnsTwoFactorChallenge() = runBlocking {
        // Backend returns requires_2fa=true with a pending_2fa_token
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{
                        "access_token": null,
                        "refresh_token": null,
                        "token_type": "bearer",
                        "expires_in": 300,
                        "requires_2fa": true,
                        "pending_2fa_token": "pending-token-abc123"
                    }""".trimIndent()
                )
        )

        val result = repository.login("user_with_2fa", "password123")

        assertTrue("login should succeed at the Result level", result.isSuccess)
        val outcome = result.getOrThrow()
        assertTrue(
            "Outcome must be TwoFactorRequired, was: $outcome",
            outcome is LoginOutcome.TwoFactorRequired
        )
        val challenge = outcome as LoginOutcome.TwoFactorRequired
        assertTrue(
            "Challenge token must be preserved",
            challenge.challengeToken == "pending-token-abc123"
        )
    }

    @Test
    fun login_normalSuccess_returnsSuccess() = runBlocking {
        // Backend returns access_token + refresh_token (no 2FA)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{
                        "access_token": "access-token-xyz",
                        "refresh_token": "refresh-token-xyz",
                        "token_type": "bearer",
                        "expires_in": 3600,
                        "requires_2fa": false
                    }""".trimIndent()
                )
        )
        // Profile response (called after login to get user details)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{
                        "id": 42,
                        "username": "testuser",
                        "phone": "+77771112233",
                        "full_name": "Test User",
                        "role": "Patient",
                        "date_of_birth": "1990-01-01",
                        "biometric_enabled": false,
                        "telegram_chat_id": null,
                        "clinic_id": "clinic_base"
                    }""".trimIndent()
                )
        )

        val result = repository.login("testuser", "password123")

        assertTrue("login should succeed", result.isSuccess)
        val outcome = result.getOrThrow()
        assertTrue(
            "Outcome must be Success, was: $outcome",
            outcome is LoginOutcome.Success
        )
        val success = outcome as LoginOutcome.Success
        assertTrue("User phone must be populated", success.user.phone == "+77771112233")
    }

    @Test
    fun logout_callsBackendLogoutEndpoint() = runBlocking {
        // Backend returns 200 for POST /authentication/logout
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success": true, "message": "Logged out"}""")
        )

        val result = repository.logout()

        assertTrue("logout should succeed", result.isSuccess)

        val recordedRequest = mockWebServer.takeRequest()
        assertTrue(
            "logout must POST to /api/v1/authentication/logout, was: ${recordedRequest.path}",
            recordedRequest.path?.contains("/api/v1/authentication/logout") == true &&
                recordedRequest.method == "POST"
        )
    }

    @Test
    fun logout_networkError_returnsFailure() = runBlocking {
        // Backend returns 500 (server error) — should NOT clear local tokens
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""{"detail": "Internal server error"}""")
        )

        val result = repository.logout()

        assertTrue(
            "logout with server error should return failure so user can retry",
            result.isFailure
        )
    }
}
