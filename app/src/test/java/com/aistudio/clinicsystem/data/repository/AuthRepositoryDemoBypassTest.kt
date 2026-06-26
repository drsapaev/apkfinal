package com.aistudio.clinicsystem.data.repository

import androidx.test.core.app.ApplicationProvider
import com.aistudio.clinicsystem.data.api.ApiClient
import com.aistudio.clinicsystem.data.api.ApiService
import com.aistudio.clinicsystem.data.db.ClinicDatabase
import com.aistudio.clinicsystem.utils.SessionManagerImpl
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * M0 / E1.1 regression test: verifies that the demo-bypass that used to accept
 * username == "admin" or "patient" with any password has been removed.
 *
 * The bypass used to short-circuit any network call and return a hardcoded
 * fake UserDto + "fake_demo_token_$username" in EncryptedSharedPreferences.
 *
 * Strategy:
 *  1. Spin up a [MockWebServer] that returns HTTP 401 for every request.
 *  2. Use mockkObject(ApiClient) to redirect ApiClient.service to a mock
 *     Retrofit interface pointing at MockWebServer.
 *  3. Call login("admin", "any") and login("patient", "any").
 *  4. Assert the call returned Result.failure (proves it hit the network and
 *     got rejected by the backend), and that the request actually reached
 *     MockWebServer (proves no short-circuit bypass ran).
 *
 * If the bypass is reintroduced, the calls will return Result.success without
 * ever touching the network — both assertions will fail.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class AuthRepositoryDemoBypassTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: AuthRepository
    private lateinit var context: android.content.Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        mockWebServer = MockWebServer().apply { start() }
        // Queue 401 responses — one per login attempt we make in tests
        repeat(10) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(401)
                    .setBody("""{"detail":"Unauthorized"}""")
            )
        }

        val mockApiService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(ApiService::class.java)

        // Mock the ApiClient singleton so its `service` property returns our mock
        mockkObject(ApiClient)
        every { ApiClient.service } returns mockApiService

        // Reset the SessionManagerImpl singleton to a fresh state so the test
        // does not reuse state from previous tests. Under Robolectric the
        // EncryptedSharedPreferences will fail (keystore unavailable), which
        // means SessionManagerImpl.saveSession() becomes a no-op (E1.6 fail-closed).
        // This is exactly what we want: the test verifies that login() does NOT
        // short-circuit through the bypass and DOES reach the network.
        resetSessionManagerSingleton()

        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            ClinicDatabase::class.java
        ).allowMainThreadQueries().build()

        repository = AuthRepository(context, db)
    }

    @After
    fun tearDown() {
        unmockkObject(ApiClient)
        mockWebServer.shutdown()
    }

    @Test
    fun login_adminDemoBypass_isRemoved() = runBlocking {
        val result = repository.login("admin", "any-password")

        assertTrue(
            "login('admin', *) must FAIL after E1.1 — was the demo bypass reintroduced?",
            result.isFailure
        )

        // Prove the request actually reached the network (no bypass short-circuit)
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertTrue(
            "Request must hit /api/v1/authentication/login, was: ${recordedRequest.path}",
            recordedRequest.path?.contains("/api/v1/authentication/login") == true
        )
    }

    @Test
    fun login_patientDemoBypass_isRemoved() = runBlocking {
        val result = repository.login("patient", "any-password")

        assertTrue(
            "login('patient', *) must FAIL after E1.1 — was the demo bypass reintroduced?",
            result.isFailure
        )

        val recordedRequest = mockWebServer.takeRequest()
        assertTrue(
            "Request must reach the network — bypass must not have short-circuited",
            recordedRequest != null
        )
    }

    @Test
    fun login_arbitraryCredentials_reachesNetwork() = runBlocking {
        val result = repository.login("real_user@example.com", "real_password")

        assertTrue(
            "Arbitrary credentials must go through the network and fail with 401",
            result.isFailure
        )
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertTrue(
            "Request must hit /api/v1/authentication/login, was: ${recordedRequest.path}",
            recordedRequest.path?.contains("/api/v1/authentication/login") == true
        )
    }

    @Test
    fun login_adminDemoBypass_doesNotSaveFakeToken() = runBlocking {
        // The bypass used to call sessionManager.saveSession("fake_demo_token_admin", ...).
        // After E1.6, SessionManagerImpl.saveSession under Robolectric is a no-op
        // (EncryptedSharedPreferences unavailable). So even if the bypass WAS
        // reintroduced, no token would be persisted. The real protection here is
        // the isFailure assertion above. This test is a secondary check.
        val result = repository.login("admin", "any-password")
        assertTrue(result.isFailure)

        // SessionManagerImpl.getToken() returns null under Robolectric (fail-closed)
        val savedToken = SessionManagerImpl.getInstance(context).getToken()
        assertFalse(
            "No fake_demo_token_* must be saved",
            (savedToken ?: "").startsWith("fake_demo_token_")
        )
    }

    // ------------------------------------------------------------------

    private fun resetSessionManagerSingleton() {
        try {
            val companionInstance = SessionManagerImpl::class.java
                .getDeclaredField("Companion")
                .apply { isAccessible = true }
                .get(SessionManagerImpl) as Any

            val instanceField = companionInstance.javaClass.getDeclaredField("instance")
            instanceField.isAccessible = true
            instanceField.set(companionInstance, null)
        } catch (e: Exception) {
            // Best-effort reset; ignore failures
        }
    }
}
