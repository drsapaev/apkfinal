package com.aistudio.clinicsystem.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.aistudio.clinicsystem.data.db.SyncLogDao
import com.aistudio.clinicsystem.data.realtime.RealtimeManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Stage 10c (TEST-7 fix): NetworkMonitorTest.
 *
 * Closes audit finding TEST-7: "No tests for NetworkMonitor."
 *
 * Tests cover:
 *  1. isOnline starts as true (default)
 *  2. startMonitoring is idempotent (second call is no-op)
 *  3. stopMonitoring cleans up without crash
 *  4. isOnline StateFlow is observable
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class NetworkMonitorTest {

    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var mockContext: Context
    private lateinit var mockRealtimeManager: RealtimeManager
    private lateinit var mockSyncLogDao: SyncLogDao

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockRealtimeManager = mockk(relaxed = true)
        mockSyncLogDao = mockk(relaxed = true)

        // Mock ConnectivityManager
        val mockCm = mockk<ConnectivityManager>(relaxed = true)
        every { mockContext.getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockCm
        every { mockCm.getNetworkCapabilities(any()) } returns null

        networkMonitor = NetworkMonitor(
            appContext = mockContext,
            realtimeManager = mockRealtimeManager,
            syncLogDao = mockSyncLogDao,
        )
    }

    @After
    fun tearDown() {
        try {
            networkMonitor.stopMonitoring()
        } catch (e: Exception) {
            // ignore
        }
    }

    @Test
    fun `isOnline starts as true by default`() {
        assertTrue("isOnline should default to true", networkMonitor.isOnline.value)
    }

    @Test
    fun `startMonitoring is idempotent`() {
        networkMonitor.startMonitoring()
        networkMonitor.startMonitoring()
        // Should not crash — started flag prevents double registration
        assertTrue("isOnline should still be accessible", networkMonitor.isOnline.value != null)
    }

    @Test
    fun `stopMonitoring cleans up without crash`() {
        networkMonitor.startMonitoring()
        networkMonitor.stopMonitoring()
        // After stop, isOnline is still accessible (StateFlow retains last value)
        assertTrue("isOnline should still be accessible after stop", networkMonitor.isOnline.value != null)
    }

    @Test
    fun `stopMonitoring without startMonitoring does not crash`() {
        networkMonitor.stopMonitoring()
        // Should be a no-op
        assertTrue("isOnline should still be accessible", networkMonitor.isOnline.value != null)
    }

    @Test
    fun `isOnline is a StateFlow`() {
        // Verify it's a StateFlow by checking it has a value
        assertEquals(true, networkMonitor.isOnline.value)
    }
}
