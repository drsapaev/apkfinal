# 🏥 Clinic Management System — Android App

![CI](https://github.com/drsapaev/apkfinal/actions/workflows/android.yml/badge.svg)
![Release](https://github.com/drsapaev/apkfinal/actions/workflows/release.yml/badge.svg)

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
| DI | Hilt 2.51.1 (KSP) |
| Networking | Retrofit 2.12 + OkHttp 4.12 + Moshi 1.15 |
| Database | Room 2.7 + SQLCipher 4.5.4 (encrypted) |
| Real-time | OkHttp WebSocket via RealtimeManager |
| Background | WorkManager 2.9 (SyncWorker) |
| Security | EncryptedSharedPreferences, FLAG_SECURE, HTTPS-only |
| Testing | JUnit 4, Robolectric 4.14, mockk, MockWebServer |
| Static Analysis | ktlint 12.1, detekt 1.23 |
| CI/CD | GitHub Actions (CI + Release) |

### Key Architectural Decisions

- **SessionRepository** — Single Source of Truth for auth state (`StateFlow<SessionState>`)
- **RealtimeManager** — Unified WebSocket management (`SharedFlow<RealtimeEvent>`), `ClinicWebSocketClient` via `@Inject`
- **Outbox Pattern** — UUID primary keys, `PENDING→PROCESSING→COMPLETED/FAILED→DEAD_LETTER` state machine, exponential backoff with jitter, `lastHttpCode` for 4xx vs 5xx handling
- **NetworkBoundResource** — Offline-first sync (cache → network → cache)
- **UUID IDs** — All business entities use UUID local PKs + optional `serverId` (Int) for backend compatibility
- **TokenAuthenticator** — Auto-refresh on 401 with Mutex for concurrent request coalescing
- **Hilt DI** — All dependencies injected via `@Inject` / `@Singleton`, no manual singletons or `getInstance()`

### Project Structure

```
app/src/main/java/com/aistudio/clinicsystem/
├── data/
│   ├── api/                  # MobileApiService, ApiService, TokenAuthenticator, ApiClient
│   ├── db/                   # ClinicDatabase, DAOs, Entities, Migrations (v4→v7)
│   ├── outbox/               # OutboxStatus, OutboxRetryPolicy, UUID generator
│   ├── realtime/             # RealtimeManager, RealtimeEvent
│   ├── repository/           # AuthRepository, ClinicRepository, NetworkBoundResource
│   ├── session/              # SessionRepository (SSOT), SessionState
│   └── model/                # UserRole enum
├── domain/
│   ├── model/                # Pure Kotlin domain models
│   ├── repository/           # Repository interfaces
│   ├── usecase/              # 11 UseCases (auth, appointment, queue, medical, sync)
│   └── mapper/               # Entity↔Domain mappers
├── di/                       # Hilt modules (AppModule, etc.)
├── ui/
│   ├── screens/
│   │   ├── patient/          # 9 decomposed composables
│   │   ├── staff/            # 3 decomposed composables
│   │   ├── AuthScreen.kt
│   │   ├── PatientScreen.kt  # thin shell
│   │   └── StaffScreen.kt    # thin shell
│   ├── components/           # SecureScreen (FLAG_SECURE)
│   ├── navigation/           # ClinicNavGraph
│   ├── viewmodel/            # ClinicViewModel, AuthViewModel, PatientViewModel, StaffViewModel
│   └── theme/                # Material 3 theme
├── utils/                    # TokenManager, SessionManager, SyncWorker, NetworkMonitor, etc.
├── ClinicSystemApplication.kt
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

Backend URL is configured via Gradle properties:

```bash
# Debug build (emulator → host's localhost, default)
./gradlew assembleDebug

# Debug build with custom backend (staging)
./gradlew assembleDebug \
    -Pclinic.debugBaseUrl=https://staging.clinic.tld/ \
    -Pclinic.debugWsUrl=wss://staging.clinic.tld/ws/queue

# Release build — MUST pass production URLs via gradle properties or env vars
./gradlew assembleRelease \
    -Pclinic.baseUrl=https://api.clinic.tld/ \
    -Pclinic.wsUrl=wss://api.clinic.tld/ws/queue

# Release AAB (also needs keystore env vars — see below)
KEYSTORE_PATH=/path/to/clinic-release.jks \
STORE_PASSWORD=... \
KEY_PASSWORD=... \
./gradlew bundleRelease \
    -Pclinic.baseUrl=https://api.clinic.tld/ \
    -Pclinic.wsUrl=wss://api.clinic.tld/ws/queue
```

If `clinic.baseUrl` is not provided, the build produces an obviously invalid
URL (`https://INVALID.unset-base-url.example/`) so the release artifact
fails at runtime with a clear error rather than silently hitting a parked
domain.

### 4. Build

```bash
./gradlew assembleDebug    # Debug APK
./gradlew bundleRelease    # Release AAB (needs keystore env vars + clinic.baseUrl)
```

### 5. Run

```bash
./gradlew installDebug     # Install on emulator/device
```

## 🔐 Security

- **SQLCipher** — local database encrypted with AES-256, passphrase from EncryptedSharedPreferences
- **EncryptedSharedPreferences** — JWT tokens stored encrypted, fail-closed (no plaintext fallback)
- **Hardware-backed Keystore** — master key uses StrongBox (API 28+) with TEE fallback
- **BiometricPrompt with CryptoObject** — refresh token encrypted with biometric-gated key (AES/GCM)
- **FLAG_SECURE** — screenshots/screen recording blocked on all PHI screens
- **Timber + ReleaseTree** — PHI redacted from Logcat in release builds (phone, JWT, diagnosis, prescription)
- **Network Security Config** — release builds: HTTPS only, no cleartext
- **Certificate Pinning** — SHA-256 public key pins on all OkHttp clients (release builds)
- **Play Integrity API** — device integrity attestation at login + sensitive operations
- **Idempotency-Key** — all POST/PUT/PATCH requests carry UUID for server-side dedup
- **HttpLoggingInterceptor** — disabled in release builds
- **2FA tokens** — sent in request body, not query string
- **allowBackup=false** — no cloud backup of app data

See [Security Audit](#) (PR #8) for full audit results (8/8 checks passed).

## 🧪 Testing

### Unit Tests (166+ tests)

Test coverage areas:
- TokenAuthenticator (8 tests) — refresh, session clear, retry loop, Mutex coalescing
- MigrationTest (8 tests) — migration registration, data persistence, new column verification
- PatientViewModel (9 tests) — create/cancel appointment, logout, biometric, theme
- StaffViewModel (9 tests) — approve/cancel, undo, draft management, theme
- ClinicRepositorySyncTest (8 tests) — outbox retry, 4xx→DEAD_LETTER, 5xx→retry, claimForProcessing
- OutboxRetryPolicy (12 tests) — exponential backoff, jitter, capping, enum round-trip
- RealtimeManager (7 tests) — start/stop, idempotency, reconnectNow, emitEvent
- NetworkMonitor (5 tests) — isOnline, start/stop monitoring, idempotency
- WebSocketConnection (8 tests) — connect, subscribe, close, reconnect, malformed JSON
- AuthViewModel (14 tests) — login flow, 2FA, state management
- AuthRepository (8 tests) — 2FA challenge, logout, network errors
- ClinicRepository Room (10 tests) — CRUD, patient-scoped queries

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
- 4 Room migration tests (v4→v7)
- Additional tests (TokenAuthenticator, ClinicRepositorySync, PatientViewModel, StaffViewModel)

### Static Analysis

```bash
./gradlew ktlintCheck    # Code style
./gradlew detekt         # Code smell detection
```

### Smoke Testing

See [SMOKE_TEST_PROTOCOL.md](SMOKE_TEST_PROTOCOL.md) for the 29-scenario
pre-release verification protocol (authentication, offline-first, realtime,
database security, UI/UX, compatibility, background, localization).

## 🔄 CI/CD

### CI Pipeline (every push/PR to `main`)

[![CI](https://github.com/drsapaev/apkfinal/actions/workflows/android.yml/badge.svg)](https://github.com/drsapaev/apkfinal/actions)

1. ktlint check
2. detekt
3. Unit tests
4. Assemble debug APK
5. Upload APK + test reports as artifacts

### Release Pipeline (on tag `v*.*.*`)

[![Release](https://github.com/drsapaev/apkfinal/actions/workflows/release.yml/badge.svg)](https://github.com/drsapaev/apkfinal/actions)

1. ktlint → detekt → unit tests
2. Assemble release APK + bundle release AAB
3. Upload artifacts (APK, AAB, mapping.txt — 90-day retention)
4. Create GitHub Release with auto-generated notes

### Branch Protection

- `main` branch: PR required + CI must pass + linear history (squash-merge)
- No force pushes, no deletions

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

### Required GitHub Secrets (for CI release workflow)

The `release.yml` workflow (triggered on `v*.*.*` tags) reads these
secrets — the build **fails fast** if any is missing:

| Secret | Purpose |
|---|---|
| `CLINIC_BASE_URL` | `https://api.clinic.tld/` — production backend base URL |
| `CLINIC_WS_URL` | `wss://api.clinic.tld/ws/queue` — production WebSocket URL |
| `CLINIC_CERT_PIN_1` | `sha256/AAAA...` — current public key pin |
| `CLINIC_CERT_PIN_2` | `sha256/BBBB...` — backup pin for rotation |
| `KEYSTORE_PATH` | base64-encoded `.jks` keystore |
| `STORE_PASSWORD` | keystore store password |
| `KEY_PASSWORD` | key alias password |

Configure in **GitHub repo → Settings → Secrets and variables → Actions**.

To extract cert pins from a server certificate:
```bash
echo | openssl s_client -connect api.clinic.tld:443 2>/dev/null \
  | openssl x509 -pubkey -noout \
  | openssl pkey -pubin -outform der \
  | openssl dgst -sha256 -binary \
  | base64 | awk '{print "sha256/"$1}'
```

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
./gradlew bundleRelease \
    -Pclinic.baseUrl=https://api.clinic.tld/ \
    -Pclinic.wsUrl=wss://api.clinic.tld/ws/queue
```

### Create a Release

```bash
git tag v1.0.0
git push origin v1.0.0
# → Release workflow triggers automatically
```

## 🤝 Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup, code style,
testing guidelines, and PR process.

## 👥 Related Projects

- [**Clinic Management System (Backend)**](https://github.com/drsapaev/final) — FastAPI backend

## 📄 Documentation

- [Refactoring Roadmap](docs/REFACTORING_ROADMAP.md) — 16-week plan (M0–M5)
- [Smoke Test Protocol](SMOKE_TEST_PROTOCOL.md) — 29-scenario pre-release checklist
- [Release Notes](RELEASE_NOTES.md) — Version history
- [Contributing Guide](CONTRIBUTING.md) — Development guidelines
