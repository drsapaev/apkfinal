package com.example.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

sealed class Resource<T>(
    val data: T? = null,
    val message: String? = null
) {
    class Success<T>(data: T) : Resource<T>(data)
    class Loading<T>(data: T? = null) : Resource<T>(data)
    class Error<T>(message: String, data: T? = null) : Resource<T>(data, message)
}

/**
 * A generalized strategy function to implement Network-Bound-Resource caching pattern.
 * This ensures that local cache is always provided immediately, followed by network sync.
 * On network failure, it gracefully degrades to showing offline data.
 */
fun <ResultType, RequestType> networkBoundResource(
    query: () -> Flow<ResultType>,
    fetch: suspend () -> RequestType,
    saveFetchResult: suspend (RequestType) -> Unit,
    shouldFetch: suspend (ResultType) -> Boolean = { true },
    onFetchFailed: suspend (Throwable) -> Unit = {}
): Flow<Resource<ResultType>> = flow {
    val data = query().first() // Fetch the first snapshot from the database

    val flow = if (shouldFetch(data)) {
        // Emit loading state with existing local data
        emit(Resource.Loading(data))
        try {
            val networkResult = fetch()
            saveFetchResult(networkResult)
            // Re-query the database as Single Source of Truth
            query().map { Resource.Success(it) }
        } catch (throwable: Throwable) {
            onFetchFailed(throwable)
            // Emit error state, but keep the local data for offline viewing
            query().map { Resource.Error(throwable.localizedMessage ?: "Network error", it) }
        }
    } else {
        query().map { Resource.Success(it) }
    }

    emitAll(flow)
}
