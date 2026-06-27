# 🏥 Clinic Management System — Android App

![CI](https://github.com/drsapaev/apkfinal/actions/workflows/android.yml/badge.svg)

Mobile client for the [Clinic Management System](https://github.com/drsapaev/final) backend.
Built with **Kotlin** and **Jetpack Compose**.

## 📱 Features

- **JWT Authentication** with 2FA (TOTP) support, token refresh, and server-side logout
- **Patient Dashboard** — appointments, medical records, queue position
- **Staff Dashboard** — appointment approval, queue management, medical record creation
- **Real-time WebSocket** — queue updates, appointment status changes, new medical records
- **Offline-first** — write-ahead outbox pattern with exponential backoff and dead letter queue
- **Biometric Login** — fingerprint/Face ID support
- **Telegram Integration** — link account for notifications
- **3 Languages** — 🇷🇺 Russian (default), 🇬🇧 English, 🇺🇿 Uzbek
- **Dark/Light/System Theme**

## 🏗️ Architecture

### Tech Stack

| Component | Technology |
|---|---|
| Language | Kotlin 2.2.10 |
| UI | Jetpack Compose (BOM 2025.04.00), Material 3 |
| Architecture | Clean Architecture (data / domain / ui) |
| Networking | Retrofit 2.12 + OkHttp 4.12 + Moshi 1.15 |
| Database | Room 2.7 + SQLCipher 4.5.4 (encrypted) |
| Real-time | OkHttp WebSocket via RealtimeManager |
| Background | WorkManager 2.9 (SyncWorker) |
| Security | EncryptedSharedPreferences, FLAG_SECURE, HTTPS-only |
| Testing | JUnit 4, Robolectric 4.14, mockk, MockWebServer |
| Static Analysis | ktlint 12.1, detekt 1.23 |
| CI | GitHub Actions |

### Key Architectural Decisions

- **SessionRepository** — Single Source of Truth for auth state (StateFlow\<SessionState\>)
- **RealtimeManager** — Unified WebSocket management (SharedFlow\<RealtimeEvent\>)
- **Outbox Pattern** — UUID primary keys, PENDING→PROCESSING→COMPLETED/FAILED→DEAD_LETTER state machine, exponential backoff with jitter
- **NetworkBoundResource** — Offline-first sync (cache → network → cache)
- **UUID IDs** — All entities use UUID local PKs + optional `serverId` (Int) for backend compatibility
- **TokenAuthenticator** — Auto-refresh on 401 with Mutex for concurrent request coalescing

### Project Structure

```
app/src/main/java/com/aistudio/clinicsystem/
├── data/
│   ├── api/                  # MobileApiService, ApiService, TokenAuthenticator
│   ├── db/                   # ClinicDatabase, DAOs, Entities, Migrations
│   ├── outbox/               # OutboxStatus, OutboxRetryPolicy, UUID generator
│   ├── realtime/             # RealtimeManager, RealtimeEvent
│   ├── repository/           # AuthRepository, ClinicRepository, NetworkBoundResource
│   ├── session/              # SessionRepository (SSOT)
│   └── model/                # UserRole enum
├── domain/
│   ├── model/                # Pure Kotlin domain models
│   ├── repository/           # Repository interfaces
│   ├── usecase/              # 11 UseCases (auth, appointment, queue, medical, sync)
│   └── mapper/               # Entity↔Domain mappers
├── ui/
│   ├── screens/
│   │   ├── patient/          # 9 decomposed composables
│   │   ├── staff/            # 3 decomposed composables
│   │   ├── AuthScreen.kt
│   │   ├── PatientScreen.kt  # thin shell (733 LOC)
│   │   └── StaffScreen.kt    # thin shell (1746 LOC)
│   ├── components/           # SecureScreen (FLAG_SECURE)
│   ├── navigation/           # ClinicNavGraph
│   ├── viewmodel/            # ClinicViewModel, AuthViewModel, PatientViewModel, StaffViewModel
│   └── theme/                # Material 3 theme
├── utils/                    # TokenManager, SessionManager, SyncWorker, WebSocketClient, etc.
└── MainActivity.kt
```

## 🚀 Setup & Installation

### Prerequisites

- Android Studio (latest)
- JDK 17+ (for Gradle)
- Android SDK 36 (compileSdk)

### 1. Clone

```bash
git clone https://github.com/drsapaev/apkfinal.git
cd apkfinal
```

### 2. Configure SDK path

Create `local.properties`:

```properties
sdk.dir=/path/to/Android/Sdk
```

### 3. Configure backend URL

Backend URL is set per build type in `app/build.gradle.kts`:

```kotlin
// Debug (emulator → host's localhost)
buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:18000/\"")
buildConfigField("String", "WEBSOCKET_URL", "\"ws://10.0.2.2:18000/ws/queue\"")

// Release (override before release build)
buildConfigField("String", "BASE_URL", "\"https://api.clinic.example.com/\"")
buildConfigField("String", "WEBSOCKET_URL", "\"wss://api.clinic.example.com/ws/queue\"")
```

### 4. Build

```bash
./gradlew assembleDebug    # Debug APK
./gradlew bundleRelease    # Release AAB (needs keystore env vars)
```

### 5. Run

```bash
./gradlew installDebug     # Install on emulator/device
```

## 🔐 Security

- **SQLCipher** — local database encrypted with AES-256, passphrase from EncryptedSharedPreferences
- **EncryptedSharedPreferences** — JWT tokens stored encrypted, fail-closed (no plaintext fallback)
- **FLAG_SECURE** — screenshots/screen recording blocked on all PHI screens
- **Network Security Config** — release builds: HTTPS only, no cleartext
- **HttpLoggingInterceptor** — disabled in release builds
- **2FA tokens** — sent in request body, not query string
- **allowBackup=false** — no cloud backup of app data

## 🧪 Testing

### Unit Tests (92 tests)

```bash
./gradlew testDebugUnitTest
```

Test coverage:
- 35 UseCase tests (auth, appointment, medical, sync)
- 10 NetworkBoundResource tests (offline-first sync pattern)
- 8 ClinicRepository Room tests (CRUD, patient-scoped queries)
- 13 WebSocket tests (event handling, reconciliation guard, error resilience)
- 14 AuthViewModel tests (login flow, 2FA, state management)
- 6 SyncWorker tests (background sync, retry, error handling)
- 4 Room migration tests
- 2 Example tests

### Static Analysis

```bash
./gradlew ktlintCheck    # Code style
./gradlew detekt         # Code smell detection
```

## 🔄 CI/CD

GitHub Actions pipeline runs on every push/PR to `main`:

1. ktlint check
2. detekt
3. Unit tests (92 tests)
4. Assemble debug APK
5. Upload APK + test reports as artifacts

**Status**: [![CI](https://github.com/drsapaev/apkfinal/actions/workflows/android.yml/badge.svg)](https://github.com/drsapaev/apkfinal/actions)

## 📊 API Integration

### Authentication

```
POST /api/v1/authentication/login     — Login (username + password, returns access + refresh tokens)
POST /api/v1/authentication/refresh   — Refresh access token
POST /api/v1/authentication/logout    — Server-side session invalidation
POST /api/v1/2fa/verify               — 2FA TOTP verification
POST /api/v1/2fa/recovery/request     — 2FA recovery code request
POST /api/v1/2fa/recovery/verify      — 2FA recovery code verification
GET  /api/v1/authentication/profile   — Current user profile
```

### Mobile API (/api/v1/mobile/*)

```
GET  /mobile/patients/me              — Patient profile
GET  /mobile/appointments/upcoming    — Upcoming appointments
POST /mobile/appointments/book        — Book appointment
POST /mobile/appointments/cancel      — Cancel appointment
GET  /mobile/lab/results              — Lab results
GET  /mobile/queues/status            — Queue status
GET  /mobile/notifications            — Notifications
```

### WebSocket

```
ws://host:18000/ws/queue
Authorization: Bearer <access_token>

Events: APPOINTMENT_STATUS, NEW_MEDICAL_RECORD, QUEUE_UPDATE
```

## 🌍 Localization

| Locale | Language |
|---|---|
| `values/` | 🇷🇺 Русский (default) |
| `values-en/` | 🇬🇧 English |
| `values-uz/` | 🇺🇿 O'zbekcha |

## 📦 Release

See [RELEASE_NOTES.md](RELEASE_NOTES.md) for version history.

### Build Release AAB

```bash
# Generate keystore (one-time)
keytool -genkeypair -v -keystore clinic-release.keystore \
  -alias clinic -keyalg RSA -keysize 4096 -validity 10000

# Set env vars
export KEYSTORE_PATH=/path/to/clinic-release.keystore
export STORE_PASSWORD=your_store_password
export KEY_PASSWORD=your_key_password

# Build
./gradlew bundleRelease
```

## 🤝 Contributing

1. Create a feature branch (`git checkout -b feature/amazing-feature`)
2. Ensure `./gradlew ktlintCheck detekt testDebugUnitTest` passes
3. Commit changes following conventional commits
4. Open a Pull Request

## 👥 Related Projects

- [**Clinic Management System (Backend)**](https://github.com/drsapaev/final) — FastAPI backend
