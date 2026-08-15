package com.aistudio.clinicsystem.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M3A/E6.2: Unit tests for [networkBoundResource].
 *
 * Tests the offline-first sync pattern:
 *   1. Emit cached data (Loading)
 *   2. Fetch from network
 *   3. Save to cache
 *   4. Emit updated data (Success)
 *   5. On failure, emit Error with cached data
 *
 * These tests verify the core architectural pattern without any Android
 * dependencies — pure Kotlin coroutines + Flow.
 */
class NetworkBoundResourceTest {

    @Test
    fun `shouldFetch true - emits Loading then Success with updated data`() = runBlocking {
        // Arrange: cache starts empty, network returns "network-data"
        var cacheValue = "initial-cache"
        val query: () -> Flow<String> = { flowOf(cacheValue) }
        val fetch: suspend () -> String = { "network-data" }
        val save: suspend (String) -> Unit = { cacheValue = it }

        // Act
        val results = networkBoundResource(
            query = query,
            fetch = fetch,
            saveFetchResult = save,
            shouldFetch = { true }
        ).toList()

        // Assert: Loading(initial-cache) → Success(network-data)
        assertEquals(2, results.size)
        assertTrue("First emission should be Loading", results[0] is Resource.Loading)
        assertEquals("initial-cache", results[0].data)
        assertTrue("Second emission should be Success", results[1] is Resource.Success)
        assertEquals("network-data", results[1].data)
    }

    @Test
    fun `shouldFetch false - emits Success with cached data only`() = runBlocking {
        val query: () -> Flow<String> = { flowOf("cached-data") }
        val fetch: suspend () -> String = { "should-not-be-called" }
        val save: suspend (String) -> Unit = { }

        val results = networkBoundResource(
            query = query,
            fetch = fetch,
            saveFetchResult = save,
            shouldFetch = { false }
        ).toList()

        // Should emit only Success(cached-data), no Loading, no network call
        assertEquals(1, results.size)
        assertTrue("Should be Success", results[0] is Resource.Success)
        assertEquals("cached-data", results[0].data)
    }

    @Test
    fun `network failure - emits Loading then Error with cached data`() = runBlocking {
        val query: () -> Flow<String> = { flowOf("cached-data") }
        val fetch: suspend () -> String = { throw RuntimeException("Network error") }
        val save: suspend (String) -> Unit = { }

        val results = networkBoundResource(
            query = query,
            fetch = fetch,
            saveFetchResult = save,
            shouldFetch = { true }
        ).toList()

        // Loading(cached-data) → Error("Network error", cached-data)
        assertEquals(2, results.size)
        assertTrue("First should be Loading", results[0] is Resource.Loading)
        assertEquals("cached-data", results[0].data)
        assertTrue("Second should be Error", results[1] is Resource.Error)
        assertEquals("cached-data", results[1].data)
        assertEquals("Network error", (results[1] as Resource.Error).message)
    }

    @Test
    fun `onFetchFailed callback is called on network failure`() = runBlocking {
        val query: () -> Flow<String> = { flowOf("cached") }
        val fetch: suspend () -> String = { throw RuntimeException("Timeout") }
        var callbackCalled = false
        var capturedError: Throwable? = null

        networkBoundResource(
            query = query,
            fetch = fetch,
            saveFetchResult = { },
            shouldFetch = { true },
            onFetchFailed = { error ->
                callbackCalled = true
                capturedError = error
            }
        ).toList()

        assertTrue("onFetchFailed should be called", callbackCalled)
        assertTrue("Error should be RuntimeException", capturedError is RuntimeException)
        assertEquals("Timeout", capturedError?.message)
    }

    @Test
    fun `saveFetchResult is called with network data on success`() = runBlocking {
        val query: () -> Flow<String> = { flowOf("cache") }
        val fetch: suspend () -> String = { "from-network" }
        var savedValue: String? = null

        networkBoundResource(
            query = query,
            fetch = fetch,
            saveFetchResult = { savedValue = it },
            shouldFetch = { true }
        ).toList()

        assertEquals("from-network", savedValue)
    }

    @Test
    fun `saveFetchResult is NOT called on network failure`() = runBlocking {
        val query: () -> Flow<String> = { flowOf("cache") }
        val fetch: suspend () -> String = { throw RuntimeException("fail") }
        var saveCalled = false

        networkBoundResource(
            query = query,
            fetch = fetch,
            saveFetchResult = { saveCalled = true },
            shouldFetch = { true }
        ).toList()

        assertTrue("saveFetchResult should NOT be called on failure", !saveCalled)
    }

    @Test
    fun `shouldFetch receives cached data for decision`() = runBlocking {
        val query: () -> Flow<List<String>> = { flowOf(listOf("item1")) }
        val fetch: suspend () -> List<String> = { listOf("new") }
        var shouldFetchCalledWith: List<String>? = null

        networkBoundResource(
            query = query,
            fetch = fetch,
            saveFetchResult = { },
            shouldFetch = { cached ->
                shouldFetchCalledWith = cached
                false  // don't fetch
            }
        ).toList()

        assertEquals(listOf("item1"), shouldFetchCalledWith)
    }

    @Test
    fun `empty cache triggers fetch, non-empty cache skips fetch`() = runBlocking {
        // Test the common pattern: shouldFetch returns true if cache is empty
        var fetchCallCount = 0

        // Scenario 1: empty cache → should fetch
        val emptyQuery: () -> Flow<List<String>> = { flowOf(emptyList()) }
        networkBoundResource(
            query = emptyQuery,
            fetch = { fetchCallCount++; listOf("item") },
            saveFetchResult = { },
            shouldFetch = { it.isEmpty() }
        ).toList()
        assertEquals("Fetch should be called for empty cache", 1, fetchCallCount)

        // Scenario 2: non-empty cache → should NOT fetch
        val nonEmptyQuery: () -> Flow<List<String>> = { flowOf(listOf("existing")) }
        networkBoundResource(
            query = nonEmptyQuery,
            fetch = { fetchCallCount++; listOf("item") },
            saveFetchResult = { },
            shouldFetch = { it.isEmpty() }
        ).toList()
        assertEquals("Fetch should NOT be called for non-empty cache", 1, fetchCallCount)
    }

    @Test
    fun `query is called at least twice - once for initial, once after save`() = runBlocking {
        var cacheValue = "initial"
        var queryCallCount = 0
        val query: () -> Flow<String> = {
            queryCallCount++
            flowOf(cacheValue)
        }

        networkBoundResource(
            query = query,
            fetch = { "network" },
            saveFetchResult = { cacheValue = it },
            shouldFetch = { true }
        ).toList()

        // query() is called: once for .first() (initial), once for .map{} (after save)
        assertTrue("query should be called at least twice", queryCallCount >= 2)
    }

    @Test
    fun `error message falls back to 'Network error' when localizedMessage is null`() = runBlocking {
        val query: () -> Flow<String> = { flowOf("cache") }
        val fetch: suspend () -> String = { throw object : Throwable() {
            override val message: String? = null
        } }

        val results = networkBoundResource(
            query = query,
            fetch = fetch,
            saveFetchResult = { },
            shouldFetch = { true }
        ).toList()

        assertTrue("Second should be Error", results[1] is Resource.Error)
        assertEquals("Network error", (results[1] as Resource.Error).message)
    }
}
