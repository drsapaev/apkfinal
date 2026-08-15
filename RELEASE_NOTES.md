# Release Notes

## Version 1.0.0 (Initial Release)

### Features
- JWT authentication with 2FA (TOTP) support
- Patient dashboard: appointments, medical records, queue position
- Staff dashboard: appointment approval, queue management
- Real-time WebSocket updates (queue, appointment status, medical records)
- Offline-first with outbox pattern (exponential backoff, dead letter queue)
- Biometric login support
- Telegram integration for notifications
- Dark/Light/System theme
- English and Russian localization

### Security
- SQLCipher encrypted local database
- EncryptedSharedPreferences for token storage (fail-closed)
- FLAG_SECURE on all PHI screens (screenshots blocked)
- Network Security Config (HTTPS-only in release)
- HttpLoggingInterceptor disabled in release builds
- 2FA tokens in request body (not query string)
- No hardcoded secrets
- allowBackup=false

### Architecture
- Clean Architecture: data / domain / ui layers
- SessionRepository (SSOT for auth state)
- RealtimeManager (unified WebSocket)
- Outbox pattern with UUID primary keys
- NetworkBoundResource for offline-first sync
- 268 unit + android tests, ktlint + detekt static analysis
- GitHub Actions CI pipeline

### Tech Stack
- Kotlin 2.1.0, Jetpack Compose (BOM 2025.04.00)
- Room 2.7.0 + SQLCipher 4.6.0
- Retrofit 2.12.0 + Moshi 1.15.2 + OkHttp 4.12.0
- WorkManager 2.9.1 (background sync)
- Robolectric 4.14.1 (unit testing)
