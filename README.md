# 🏥 Clinic Management System - Android App

Mobile client for the [Clinic Management System](https://github.com/drsapaev/final) built with **Kotlin** and **Jetpack Compose**.

## 📱 Features

- ✅ **Patient Management** - View and manage patient appointments
- ✅ **Online Queue System** - Real-time queue updates with WebSocket
- ✅ **Appointment Booking** - Schedule, reschedule, and cancel appointments
- ✅ **Authentication** - Secure JWT-based login with multiple user roles
- ✅ **Lab Results** - View test results and medical records
- ✅ **Notifications** - Real-time push notifications for queue updates
- ✅ **Doctor Schedule** - Browse doctor availability and specialties
- ✅ **Role-Based Access** - Support for Patient, Doctor, Registrar, Admin roles

## 🏗️ Architecture

### Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM + Repository Pattern
- **Networking**: Retrofit + OkHttp
- **Serialization**: Moshi
- **Database**: Room
- **Dependency Injection**: Hilt (prepared, will add later)
- **Real-time**: OkHttp WebSocket
- **Coroutines**: Kotlin Coroutines + Flow

### Project Structure

```
app/
├── src/main/kotlin/com/aistudio/clinicsystem/
│   ├── data/
│   │   ├── api/                 # API client and services
│   │   ├── db/                  # Room database
│   │   ├── repository/          # Repository implementations
│   │   └── model/               # Data models
│   ├── domain/
│   │   ├── repository/          # Repository interfaces
│   │   └── model/               # Domain models
│   ├── ui/
│   │   ├── screens/             # Compose screens
│   │   ├── navigation/          # Navigation setup
│   │   ├── components/          # Reusable Compose components
│   │   ├── viewmodel/           # ViewModels
│   │   └── theme/               # Material Design 3 theme
│   ├── auth/                    # Authentication logic
│   ├── utils/                   # Utilities and extensions
│   └── MainActivity.kt
└── build.gradle.kts
```

## 🚀 Setup & Installation

### Prerequisites

- Android Studio 2024.1+
- Android SDK 24+ (API level)
- Android Gradle Plugin 8.0+
- Kotlin 2.0+

### 1. Clone and Setup

```bash
git clone https://github.com/drsapaev/apkfinal.git
cd apkfinal
```

### 2. Configure Backend URL

Create `.env` file from template:

```bash
cp .env.example .env
```

Edit `.env` and set your backend URL:

```env
BACKEND_URL=http://192.168.1.100:18000  # Change to your backend IP/URL
WEBSOCKET_URL=ws://192.168.1.100:18000/ws
```

**For local development (emulator):**
```env
BACKEND_URL=http://10.0.2.2:18000
WEBSOCKET_URL=ws://10.0.2.2:18000/ws
```

**For production:**
```env
BACKEND_URL=https://api.clinic.example.com
WEBSOCKET_URL=wss://api.clinic.example.com/ws
```

### 3. Open in Android Studio

1. Open Android Studio
2. Select **File → Open**
3. Choose this project directory
4. Wait for Gradle sync to complete

### 4. Run on Emulator or Device

```bash
# Run on emulator (must be running)
./gradlew installDebug

# Or use Android Studio: Run → Run 'app'
```

## 🔐 API Integration

### Authentication

The app uses **JWT Bearer Token** authentication.

#### Login Flow

```kotlin
// 1. User enters username/password
val loginRequest = LoginRequest(username = "admin", password = "password")

// 2. API returns access token
val response = apiService.login(loginRequest)
// Response: {
//   "access_token": "eyJhbGc...",
//   "token_type": "bearer",
//   "expires_in": 43200
// }

// 3. Token is stored in secure SharedPreferences
tokenManager.saveToken(response.accessToken, response.tokenType)

// 4. Token is automatically added to all requests
// Authorization: Bearer <access_token>
```

### Base API Endpoints

**Base URL**: `http://localhost:18000/api/v1`

#### Authentication
- `POST /authentication/login` - Login and get token
- `POST /authentication/refresh` - Refresh access token
- `POST /authentication/logout` - Logout

#### Users
- `GET /users/me` - Get current user info
- `GET /users/users` - List all users (Admin only)

#### Patients
- `GET /patients/` - List patients with pagination
- `GET /patients/{id}` - Get patient details
- `POST /patients/` - Create new patient

#### Appointments
- `GET /patient/appointments` - Get current patient's appointments
- `POST /patient/appointments/{id}/cancel` - Cancel appointment
- `POST /patient/appointments/{id}/reschedule` - Reschedule appointment
- `GET /patient/appointments/{id}/available-slots` - Get available slots
- `GET /patient/results` - Get lab results

#### Queue
- `GET /queue/` - Get all queues
- `POST /queue/join` - Join online queue
- `POST /queue/{id}/call` - Call next patient (Doctor/Registrar)
- `POST /queue/{id}/complete` - Complete queue item

#### Departments & Services
- `GET /departments/` - List all departments
- `GET /services/` - List all services
- `GET /services/department/{dept_id}` - Get department services

#### Doctors
- `GET /doctors/` - List all doctors
- `GET /doctors/{id}/schedule` - Get doctor's schedule
- `GET /doctors/{id}/queue` - Get doctor's current queue

### WebSocket Real-time Updates

Connect to WebSocket for real-time updates:

```
ws://192.168.1.100:18000/ws

Headers:
Authorization: Bearer <access_token>
```

**Message Types**:
- Queue position updates
- Appointment reminders
- Doctor availability changes
- System notifications

## 🧪 Testing

### Run Unit Tests

```bash
./gradlew test
```

### Run Instrumented Tests (on emulator/device)

```bash
./gradlew connectedAndroidTest
```

### Run Screenshot Tests

```bash
./gradlew recordRoborazzi  # Record baseline
./gradlew verifyRoborazzi   # Verify against baseline
```

## 📚 Documentation

- [API Reference](../../docs/API_REFERENCE.md) - Complete API endpoints
- [Authentication Guide](../../docs/AUTHENTICATION_SYSTEM_FINAL_GUIDE.md) - Auth system details
- [Role System](../../docs/ROLES_AND_ROUTING.md) - User roles and permissions
- [Queue System](../../docs/QUEUE_SYSTEM_ARCHITECTURE.md) - Queue architecture

## 🔄 CI/CD

### GitHub Actions

The app includes automated CI/CD:

- ✅ Code quality checks (Lint)
- ✅ Unit tests
- ✅ Build APK in debug and release modes
- ✅ Screenshot tests

**Status**: [Actions](https://github.com/drsapaev/apkfinal/actions)

## 🐛 Debugging

### Enable Debug Logging

Edit `.env`:
```env
DEBUG_MODE=true
LOG_LEVEL=DEBUG
```

Then rebuild the app.

### Check Network Requests

The app includes OkHttp logging interceptor. Check Logcat:

```bash
adb logcat | grep -i "clinicsystem\|okhttp"
```

### Clear App Data

```bash
adb shell pm clear com.aistudio.clinicsystem.hvyqt
```

## 🤝 Contributing

1. Create a feature branch (`git checkout -b feature/amazing-feature`)
2. Commit changes (`git commit -m 'Add amazing feature'`)
3. Push to branch (`git push origin feature/amazing-feature`)
4. Open a Pull Request

See [CONTRIBUTING.md](CONTRIBUTING.md) for more details.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🆘 Support & Issues

- 📝 [Report a Bug](https://github.com/drsapaev/apkfinal/issues/new?template=bug_report.md)
- 💡 [Request a Feature](https://github.com/drsapaev/apkfinal/issues/new?template=feature_request.md)
- 💬 [Discussions](https://github.com/drsapaev/apkfinal/discussions)

## 👥 Related Projects

- [**Clinic Management System (Backend)**](https://github.com/drsapaev/final) - Main backend/frontend
- [**API Reference**](https://github.com/drsapaev/final/docs/API_REFERENCE.md) - API documentation

---

**Clinic Management System - Mobile Client** 🚀✨
