package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.database.ClinicDatabase
import com.example.data.database.UserEntity
import com.example.data.network.ApiClient
import com.example.data.network.LoginRequest
import com.example.data.network.OtpRequest
import com.example.data.network.UserDto
import com.example.util.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AuthRepository manages network authentication workflows with the backend FastAPI 'final' server,
 * while maintaining local cache synchronization in the Room database (`com.example.data.database.UserEntity`).
 */
class AuthRepository(
    private val context: Context,
    private val database: ClinicDatabase
) {
    private val apiService = ApiClient.service
    private val userDao = database.userDao()

    /**
     * Request an OTP (One-Time Password) from the FastAPI backend for the given phone number.
     * Returns true if successful.
     */
    suspend fun requestOtp(phone: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.requestOtp(OtpRequest(phone))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.success) {
                    Result.success(body.message)
                } else {
                    Result.failure(Exception(body.message))
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Ошибка отправки запроса ОТР"
                Result.failure(Exception("Код: ${response.code()}: $errorMsg"))
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Error requesting OTP: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Verifies the OTP with the backend, logs the user in, gets the JWT token,
     * updates the application state with metadata details, and registers/caches the user in Room.
     */
    suspend fun login(phone: String, otp: String): Result<UserDto> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.login(LoginRequest(phone, otp))
            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                val userDto = authResponse.user
                
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
                val errorMsg = response.errorBody()?.string() ?: "Ошибка авторизации кода OTP"
                Result.failure(Exception("Код: ${response.code()}: $errorMsg"))
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
                Result.failure(Exception("Срок действия сессии истек"))
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
