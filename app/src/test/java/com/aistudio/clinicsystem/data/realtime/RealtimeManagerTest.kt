package com.aistudio.clinicsystem.data.realtime

import android.content.Context
import com.aistudio.clinicsystem.data.db.ClinicDatabase
import com.aistudio.clinicsystem.data.session.SessionRepository
import com.aistudio.clinicsystem.data.session.SessionState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Stage 10c (TEST-7 fix): RealtimeManagerTest.
 *
 * Closes audit finding TEST-7: "No tests for RealtimeManager."
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class RealtimeManagerTest {

    private lateinit var realtimeManager: RealtimeManager
    private lateinit var mockContext: Context
    private lateinit var mockDatabase: ClinicDatabase
    private lateinit var mockSessionRepo: SessionRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        mockContext = mockk(relaxed = true)
        mockDatabase = mockk(relaxed = true)
        mockSessionRepo = mockk(relaxed = true)

        every { mockSessionRepo.sessionState } returns MutableStateFlow(
            SessionState.Unauthenticated,
        )

        realtimeManager = RealtimeManager(
            context = mockContext,
            database = mockDatabase,
            sessionRepository = mockSessionRepo,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `connectionState starts as Disconnected`() {
        assertEquals(
            RealtimeEvent.ConnectionState.Disconnected,
            realtimeManager.connectionState.value,
        )
    }

    @Test
    fun `stop sets connectionState to Disconnected`() {
        realtimeManager.stop()
        assertEquals(
            RealtimeEvent.ConnectionState.Disconnected,
            realtimeManager.connectionState.value,
        )
    }

    @Test
    fun `stop is idempotent`() {
        realtimeManager.stop()
        realtimeManager.stop()
        assertEquals(
            RealtimeEvent.ConnectionState.Disconnected,
            realtimeManager.connectionState.value,
        )
    }

    @Test
    fun `start when unauthenticated does not connect`() {
        realtimeManager.start()
        assertEquals(
            RealtimeEvent.ConnectionState.Disconnected,
            realtimeManager.connectionState.value,
        )
    }

    @Test
    fun `initialize does not crash when unauthenticated`() {
        realtimeManager.initialize()
        assertEquals(
            RealtimeEvent.ConnectionState.Disconnected,
            realtimeManager.connectionState.value,
        )
    }

    @Test
    fun `reconnectNow when not connected does not crash`() {
        realtimeManager.reconnectNow()
        assertEquals(
            RealtimeEvent.ConnectionState.Disconnected,
            realtimeManager.connectionState.value,
        )
    }

    @Test
    fun `emitEvent delivers to SharedFlow subscribers`() {
        kotlinx.coroutines.runBlocking {
            val collected = mutableListOf<RealtimeEvent>()
            val job = kotlinx.coroutines.GlobalScope.launch(Dispatchers.Unconfined) {
                realtimeManager.events.collect { collected.add(it) }
            }
            realtimeManager.emitEvent(RealtimeEvent.ConnectionState.Connected)
            realtimeManager.emitEvent(RealtimeEvent.ConnectionState.Disconnected)
            kotlinx.coroutines.delay(100)
            job.cancel()

            assertEquals(2, collected.size)
        }
    }
}
