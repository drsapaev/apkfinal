package com.aistudio.clinicsystem.data.api

import com.aistudio.clinicsystem.utils.SessionManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Stage 10.1 (C-13 fix): TokenAuthenticatorTest — 8 test cases covering
 * the most security-critical networking component.
 *
 * Closes audit finding C-13: "ZERO tests for TokenAuthenticator".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class TokenAuthenticatorTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var sessionManager: SessionManager
    private var sessionInvalidatedCalled = false
    private lateinit var authenticator: TokenAuthenticator
    private val moshi = com.squareup.moshi.Moshi.Builder().build()

    @Before
    fun setUp() {
        mockWebServer = MockWebServer().apply { start() }
        sessionManager = mockk(relaxed = true)
        sessionInvalidatedCalled = false

        authenticator = TokenAuthenticator(
            sessionManager = sessionManager,
            moshi = moshi,
            baseUrlProvider = { mockWebServer.url("/").toString() },
            onSessionInvalidated = { sessionInvalidatedCalled = true },
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    private fun build401Response(request: Request, priorResponse: Response? = null): Response =
        Response.Builder()
            .request(request)
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .priorResponse(priorResponse)
            .build()

    private fun buildPrior401Response(request: Request): Response {
        val prior = Response.Builder()
            .request(request)
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .build()
        return Response.Builder()
            .request(request)
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .priorResponse(prior)
            .build()
    }

    private val testRequest: Request
        get() = Request.Builder()
            .url(mockWebServer.url("/api/v1/appointments"))
            .header("Authorization", "Bearer old-access-token")
            .build()

    @Test
    fun `401 with valid refresh token retries with new access token`() {
        every { sessionManager.getToken() } returns "old-access-token"
        every { sessionManager.getRefreshToken() } returns "valid-refresh-token"

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"access_token":"new-access-token","refresh_token":"new-refresh-token"}""",
                ),
        )

        val result = authenticator.authenticate(null, build401Response(testRequest))

        assertNotNull("Should return a retried request", result)
        assertEquals("Bearer new-access-token", result?.header("Authorization"))
        verify { sessionManager.setTokens("new-access-token", "new-refresh-token") }
        assertEquals(false, sessionInvalidatedCalled)
    }

    @Test
    fun `401 with expired refresh token clears session and returns null`() {
        every { sessionManager.getToken() } returns "old-access-token"
        every { sessionManager.getRefreshToken() } returns "expired-refresh-token"

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"detail":"Invalid refresh token"}"""),
        )

        val result = authenticator.authenticate(null, build401Response(testRequest))

        assertNull("Should return null (give up)", result)
        verify { sessionManager.clearSession() }
        assertEquals(true, sessionInvalidatedCalled)
    }

    @Test
    fun `401 with no refresh token clears session and returns null`() {
        every { sessionManager.getToken() } returns "old-access-token"
        every { sessionManager.getRefreshToken() } returns null

        val result = authenticator.authenticate(null, build401Response(testRequest))

        assertNull("Should return null", result)
        verify { sessionManager.clearSession() }
        assertEquals(true, sessionInvalidatedCalled)
    }

    @Test
    fun `401 with blank refresh token clears session and returns null`() {
        every { sessionManager.getToken() } returns "old-access-token"
        every { sessionManager.getRefreshToken() } returns ""

        val result = authenticator.authenticate(null, build401Response(testRequest))

        assertNull("Should return null", result)
        verify { sessionManager.clearSession() }
        assertEquals(true, sessionInvalidatedCalled)
    }

    @Test
    fun `401 with priorResponse returns null immediately without refresh`() {
        every { sessionManager.getToken() } returns "old-access-token"
        every { sessionManager.getRefreshToken() } returns "valid-refresh-token"

        val result = authenticator.authenticate(null, buildPrior401Response(testRequest))

        assertNull("Should return null (too many retries)", result)
        verify(exactly = 0) { sessionManager.setTokens(any(), any()) }
    }

    @Test
    fun `401 with refresh endpoint 500 clears session and returns null`() {
        every { sessionManager.getToken() } returns "old-access-token"
        every { sessionManager.getRefreshToken() } returns "valid-refresh-token"

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"),
        )

        val result = authenticator.authenticate(null, build401Response(testRequest))

        assertNull("Should return null on 500", result)
        verify { sessionManager.clearSession() }
        assertEquals(true, sessionInvalidatedCalled)
    }

    @Test
    fun `401 with refresh endpoint null body clears session and returns null`() {
        every { sessionManager.getToken() } returns "old-access-token"
        every { sessionManager.getRefreshToken() } returns "valid-refresh-token"

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(""),
        )

        val result = authenticator.authenticate(null, build401Response(testRequest))

        assertNull("Should return null on empty body", result)
        verify { sessionManager.clearSession() }
        assertEquals(true, sessionInvalidatedCalled)
    }

    @Test
    fun `401 when token already refreshed retries with new token without refresh call`() {
        every { sessionManager.getToken() } returns "new-access-token"
        every { sessionManager.getRefreshToken() } returns "valid-refresh-token"

        val result = authenticator.authenticate(null, build401Response(testRequest))

        assertNotNull("Should return a retried request", result)
        assertEquals("Bearer new-access-token", result?.header("Authorization"))
        verify(exactly = 0) { sessionManager.setTokens(any(), any()) }
        assertEquals(false, sessionInvalidatedCalled)
    }
}
