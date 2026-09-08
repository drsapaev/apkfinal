package com.aistudio.clinicsystem.data.repository

import androidx.test.core.app.ApplicationProvider
import com.aistudio.clinicsystem.data.api.MobileApiService
import com.aistudio.clinicsystem.data.db.ClinicDatabase
import com.aistudio.clinicsystem.data.db.UserEntity
import com.aistudio.clinicsystem.data.session.SessionRepository
import io.mockk.every
import io.mockk.mockk
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
 * Unit tests for AuthRepository Telegram integration methods.
 *
 * M-CONTRACT-FIX: the backend removed POST /api/v1/users/telegram/link and
 * /unlink together with the legacy API (they would return HTTP 404). The
 * repository now FAILS FAST for linkTelegram / unlinkTelegram with an
 * explanatory error instead of making a doomed network call. These tests
 * pin that contract.
 *
 * sendTestTelegramNotification still hits the live
 * /api/v1/telegram-integration/send-notification route via OkHttp
 * (MockWebServer here).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class AuthRepositoryTelegramTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: AuthRepository
    private lateinit var context: android.content.Context
    private lateinit var database: ClinicDatabase
    private lateinit var sessionRepository: SessionRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockWebServer = MockWebServer().apply { start() }

        database =
            androidx.room.Room
                .inMemoryDatabaseBuilder(
                    context,
                    ClinicDatabase::class.java,
                ).allowMainThreadQueries()
                .build()

        sessionRepository = mockk(relaxed = true)

        // Default: session has a token + phone
        every { sessionRepository.accessToken } returns "fake-test-token"
        every { sessionRepository.phone } returns "+77771112233"

        // Seed a user with Telegram linked
        runBlocking {
            database.userDao().insertUser(
                UserEntity(
                    id = 1,
                    phone = "+77771112233",
                    fullName = "Test User",
                    role = "Patient",
                    telegramChatId = "123456789",
                ),
            )
        }

        val mobileApiService =
            Retrofit
                .Builder()
                .baseUrl(mockWebServer.url("/"))
                .client(OkHttpClient.Builder().build())
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
                .create(MobileApiService::class.java)

        repository =
            AuthRepository(
                context = context,
                database = database,
                mobileApiService = mobileApiService,
                sessionRepository = sessionRepository,
            )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        database.close()
    }

    // ─── linkTelegram ────────────────────────────────────────────────

    @Test
    fun `linkTelegram fails fast — endpoint removed from backend`() =
        runBlocking {
            val result = repository.linkTelegram("999999")

            assertTrue("linkTelegram must fail (endpoint removed on backend)", result.isFailure)
            assertTrue(
                "Failure must explain that Telegram linking happens via the bot",
                result.exceptionOrNull()?.message?.contains("бот", ignoreCase = true) == true,
            )
        }

    // ─── unlinkTelegram ──────────────────────────────────────────────

    @Test
    fun `unlinkTelegram fails fast — endpoint removed from backend`() =
        runBlocking {
            val result = repository.unlinkTelegram()

            assertTrue("unlinkTelegram must fail (endpoint removed on backend)", result.isFailure)
        }

    // ─── sendTestTelegramNotification ────────────────────────────────

    @Test
    fun `sendTestTelegramNotification success on HTTP 200`() =
        runBlocking {
            // The OkHttp call goes to MockWebServer
            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"success":true}"""))

            val result = repository.sendTestTelegramNotification()

            assertTrue(
                "sendTestTelegramNotification should succeed on HTTP 200",
                result.isSuccess,
            )

            // Verify the request actually reached the network
            val recorded = mockWebServer.takeRequest()
            assertTrue(
                "Request must hit /api/v1/telegram-integration/send-notification, was: ${recorded.path}",
                recorded.path?.contains("/api/v1/telegram-integration/send-notification") == true,
            )
            assertTrue(
                "Request must carry Authorization header",
                recorded.getHeader("Authorization")?.contains("Bearer fake-test-token") == true,
            )
        }

    @Test
    fun `sendTestTelegramNotification failure on HTTP error`() =
        runBlocking {
            mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("""{"detail":"error"}"""))

            val result = repository.sendTestTelegramNotification()

            assertTrue(
                "sendTestTelegramNotification should fail on HTTP 500",
                result.isFailure,
            )
        }

    @Test
    fun `sendTestTelegramNotification failure when not authenticated`() =
        runBlocking {
            every { sessionRepository.accessToken } returns null

            val result = repository.sendTestTelegramNotification()

            assertTrue(
                "sendTestTelegramNotification should fail when not authenticated",
                result.isFailure,
            )
        }

    @Test
    fun `sendTestTelegramNotification failure when telegram not linked`() =
        runBlocking {
            // Clear the user's telegramChatId
            runBlocking {
                val user = database.userDao().getUserByPhone("+77771112233")
                database.userDao().updateUser(user!!.copy(telegramChatId = null))
            }

            val result = repository.sendTestTelegramNotification()

            assertTrue(
                "sendTestTelegramNotification should fail when telegram is not linked",
                result.isFailure,
            )
        }
}
