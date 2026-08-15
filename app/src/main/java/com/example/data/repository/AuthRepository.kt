package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.db.ClinicDatabase
import com.example.data.db.UserEntity
import com.example.data.api.ApiClient
import com.example.data.api.LoginRequest
import com.example.data.api.UserDto
import com.example.utils.SessionManagerImpl
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
    private val sessionManager = SessionManagerImpl.getInstance(context)
    private val userDao = database.userDao()

    /**
     * Verifies credentials with the backend, logs the user in, gets the JWT token,
     * updates the application state with metadata details, and registers/caches the user in Room.
     */
    suspend fun login(username: String, otpOrPassword: String): Result<UserDto> = withContext(Dispatchers.IO) {
        try {
            // DEMO BYPASS for local preview
            if (username == "patient" || username == "admin") {
                val role = if (username == "admin") "STAFF" else "PATIENT"
                val fakePhone = if (username == "admin") "+77071234567" else "+77771112233"
                val fakeFullName = if (username == "admin") "Dr. Rustam Sapaev" else "Иванов Иван Иванович"
                
                val userDto = UserDto(
                    id = if (username == "admin") 1 else 2,
                    phone = fakePhone,
                    fullName = fakeFullName,
                    role = role,
                    dateOfBirth = "1990-01-01",
                    biometricEnabled = true,
                    telegramChatId = null,
                    clinicId = "clinic_base"
                )

                sessionManager.saveSession(
                    token = "fake_demo_token_$username",
                    phone = fakePhone,
                    role = role
                )
                
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
                
                return@withContext Result.success(userDto)
            }

            val response = apiService.login(LoginRequest(username, otpOrPassword))
            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                
                // 1. Save token on disk temporarily so the interceptor applies it automatically
                sessionManager.saveSession(
                    token = authResponse.accessToken,
                    phone = "",
                    role = ""
                )
                ApiClient.tokenProvider = { authResponse.accessToken }

                // Fetch user data via profile using token (intercepted!)
                val profileResponse = apiService.getProfile()
                
                if (profileResponse.isSuccessful && profileResponse.body() != null) {
                    val userDto = profileResponse.body()!!
                    
                    // 2. Secure token on disk in SharedPreferences with correct details
                    sessionManager.saveSession(
                        token = authResponse.accessToken,
                        phone = userDto.phone,
                        role = userDto.role
                    )

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
            val token = sessionManager.getToken()
            if (token.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Сессия отсутствует"))
            }
            
            if (token == "fake_demo_token_patient" || token == "fake_demo_token_admin") {
                val username = if (token == "fake_demo_token_admin") "admin" else "patient"
                val role = if (username == "admin") "STAFF" else "PATIENT"
                val fakePhone = if (username == "admin") "+77071234567" else "+77771112233"
                val fakeFullName = if (username == "admin") "Dr. Rustam Sapaev" else "Иванов Иван Иванович"
                
                val userDto = UserDto(
                    id = if (username == "admin") 1 else 2,
                    phone = fakePhone,
                    fullName = fakeFullName,
                    role = role,
                    dateOfBirth = "1990-01-01",
                    biometricEnabled = true,
                    telegramChatId = null,
                    clinicId = "clinic_base"
                )
                return@withContext Result.success(userDto)
            }
            
            val response = apiService.getProfile()
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
                // Token has expired or been revoked
                if (response.code() == 401) {
                    val authResponse = apiService.refresh()
                    if (authResponse.isSuccessful && authResponse.body() != null) {
                        val newToken = authResponse.body()!!.accessToken
                        sessionManager.saveSession(
                            token = newToken,
                            phone = "",
                            role = ""
                        )
                        ApiClient.tokenProvider = { newToken }
                        
                        val profileResponse = apiService.getProfile()
                        if (profileResponse.isSuccessful && profileResponse.body() != null) {
                            val userDto = profileResponse.body()!!
                            sessionManager.saveSession(
                                token = newToken,
                                phone = userDto.phone,
                                role = userDto.role
                            )
                            
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
                            return@withContext Result.success(userDto)
                        }
                    }
                    sessionManager.clearSession()
                    Result.failure(Exception("Срок действия сессии истек"))
                } else {
                    sessionManager.clearSession()
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
            val token = sessionManager.getToken() ?: return@withContext Result.failure(Exception("Не авторизован"))
            val response = apiService.linkTelegram(telegramId)
            if (response.isSuccessful) {
                // Update local DB cache as well
                sessionManager.getPhone()?.let { phone ->
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
        sessionManager.clearSession()
        ApiClient.tokenProvider = { null }
    }
}
