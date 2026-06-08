package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.db.ClinicDatabase
import com.example.data.db.UserEntity
import com.example.data.api.ApiClient
import com.example.data.api.LoginRequest
import com.example.data.api.UserDto
import com.example.utils.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AuthRepository manages network authentication workflows with the backend FastAPI 'final' server,
 * while maintaining local cache synchronization in the Room database (`com.example.data.db.UserEntity`).
 */
class AuthRepository(
    private val context: Context,
    private val database: ClinicDatabase
) {
    private val apiService = ApiClient.service
    private val userDao = database.userDao()

    /**
     * Verifies credentials with the backend, logs the user in, gets the JWT token,
     * updates the application state with metadata details, and registers/caches the user in Room.
     */
    suspend fun login(username: String, otpOrPassword: String): Result<UserDto> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.login(LoginRequest(username, otpOrPassword))
            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                
                // Fetch user data via profile using token
                val profileResponse = apiService.getProfile("Bearer ${authResponse.accessToken}")
                
                if (profileResponse.isSuccessful && profileResponse.body() != null) {
                    val userDto = profileResponse.body()!!
                    
                    // 1. Secure token on disk in SharedPreferences
                    TokenManager.saveAuthData(
                        context = context,
                        token = authResponse.accessToken,
                        phone = userDto.phone,
                        role = userDto.role
                    )

                    // 2. Clear token cache inside ApiClient to point to the new session
                    ApiClient.tokenProvider = { authResponse.accessToken }

                    // 3. Cache the logged-in User profile information in SQLite using the UserEntity Room table
                    val cachedUser = UserEntity(
                        phone = userDto.phone,
                        fullName = userDto.fullName,
                        role = userDto.role,
                        dateOfBirth = userDto.dateOfBirth ?: "1995-05-15",
                        biometricEnabled = userDto.biometricEnabled,
                        telegramChatId = userDto.telegramChatId
                    )
                    
                    val existing = userDao.getUserByPhone(userDto.phone)
                    if (existing == null) {
                        userDao.insertUser(cachedUser)
                    } else {
                        userDao.updateUser(cachedUser.copy(id = existing.id))
                    }

                    Result.success(userDto)
                } else {
                    Result.failure(retrofit2.HttpException(profileResponse))
                }
            } else {
                Result.failure(retrofit2.HttpException(response))
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Error logging in: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Perform physical or bio authorization verification.
     * Fetches current server-side profile session using saved JWT token.
     */
    suspend fun verifyCurrentSession(): Result<UserDto> = withContext(Dispatchers.IO) {
        try {
            val token = TokenManager.getToken(context)
            if (token.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Сессия отсутствует"))
            }
            
            val response = apiService.getProfile("Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                val userDto = response.body()!!
                
                // Synchronize cached SQLite item
                val existing = userDao.getUserByPhone(userDto.phone)
                val cachedUser = UserEntity(
                    phone = userDto.phone,
                    fullName = userDto.fullName,
                    role = userDto.role,
                    dateOfBirth = userDto.dateOfBirth ?: "1995-05-15",
                    biometricEnabled = userDto.biometricEnabled,
                    telegramChatId = userDto.telegramChatId
                )
                if (existing == null) {
                    userDao.insertUser(cachedUser)
                } else {
                    userDao.updateUser(cachedUser.copy(id = existing.id))
                }
                
                Result.success(userDto)
            } else {
                // Token has expired or been revoked; reset credentials
                TokenManager.clearAuthData(context)
                if (response.code() == 401) {
                    Result.failure(Exception("Срок действия сессии истек"))
                } else {
                    Result.failure(retrofit2.HttpException(response))
                }
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Error fetching profile session: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Links telegram notifications to this user profile
     */
    suspend fun linkTelegram(telegramId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = TokenManager.getToken(context) ?: return@withContext Result.failure(Exception("Не авторизован"))
            val response = apiService.linkTelegram("Bearer $token", telegramId)
            if (response.isSuccessful) {
                // Update local DB cache as well
                TokenManager.getPhone(context)?.let { phone ->
                    userDao.getUserByPhone(phone)?.let { user ->
                        userDao.updateUser(user.copy(telegramChatId = telegramId))
                    }
                }
                Result.success(Unit)
            } else {
                Result.failure(Exception("Ошибка привязки Telegram: Код ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Terminate the local application authentication state cleanly.
     */
    fun logout() {
        TokenManager.clearAuthData(context)
        ApiClient.tokenProvider = { null }
    }
}
