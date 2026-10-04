package com.uwu.animex.data

/**
 * Unified result type for network & repository operations.
 * Supports offline resilience by carrying optional cached data on failure.
 */
sealed interface Result<out T> {
    data class Success<T>(val data: T, val fromCache: Boolean = false) : Result<T>
    data class Error(
        val message: String,
        val cause: Throwable? = null,
        val cached: Any? = null,
    ) : Result<Nothing>
    data object Loading : Result<Nothing>
}

inline fun <T> Result<T>.getOrNull(): T? = (this as? Result.Success)?.data

inline fun <T> Result<T>.getOrDefault(default: T): T = getOrNull() ?: default

inline fun <T> Result<T>.onSuccess(block: (T) -> Unit): Result<T> {
    if (this is Result.Success) block(data)
    return this
}

inline fun <T> Result<T>.onError(block: (Result.Error) -> Unit): Result<T> {
    if (this is Result.Error) block(this)
    return this
}

inline fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> = when (this) {
    is Result.Success -> Result.Success(transform(data), fromCache)
    is Result.Error -> this
    Result.Loading -> Result.Loading
}

suspend fun <T> runCatchingResult(block: suspend () -> T): Result<T> =
    try {
        Result.Success(block())
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.Error(e.message ?: "Terjadi kesalahan", e)
    }
