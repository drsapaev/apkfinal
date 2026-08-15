# Дорожная карта рефакторинга мобильного клиента `apkfinal`

> **Проект:** Android-клиент клинической системы (`apkfinal`) ↔ бэкенд `final`
> **Автор плана:** Super Z
> **Горизонт:** 16 недель (4 месяца) от старта
> **Аудитория:** разработчик-одиночка
> **Язык:** русский
> **Формат:** Markdown для хранения в корне репозитория (`docs/REFACTORING_ROADMAP.md`)

---

## 1. Обзор и контекст

### 1.1. Что не так сейчас

Мобильный клиент `apkfinal` был выгружен из Google AI Studio одним squashed-коммитом 2026-06-13. Это early-stage AI-generated прототип поверх продуманного (но не доведённого) набора идей: offline-first sync queue, WebSocket с backoff, SQLCipher, EncryptedSharedPreferences. При этом:

- **9 480 LOC** production-кода, **87 LOC** тестов → покрытие **0.9%**
- **1 коммит**, нет истории разработки
- **7 security ship-blocker-ов** в production-коде (demo-bypass auth, чипы быстрого входа, role-switching toggle, BODY-логирование в release, пустой `SecureScreen`, etc.)
- **README materially inaccurate**: заявляет Clean Architecture + Hilt + 8 экранов + 4 роли + GitHub Actions — ничего из этого не реализовано
- **Не собирается out-of-the-box**: нет `gradlew`, пустой `proguard-rules.pro`
- **Контракт с бэкендом**: бэкенд предоставляет `/api/v1/mobile/*` (26 endpoints) — мобильный клиент их игнорирует и ходит через общий `ApiService`
- **2FA, token refresh, logout с инвалидацией сессии** — не реализованы
- **God-composables**: `PatientScreen.kt` = 2 183 LOC, `StaffScreen.kt` = 2 079 LOC
- **Room**: `fallbackToDestructiveMigration` + `exportSchema=false` → любая schema-правка стирает пользовательские данные (БД уже на version 4)
- **i18n**: 10 файлов с захардкоженными русскими строками

### 1.2. Цель плана

Довести мобильный клиент до **production-ready** за 16 недель, обеспечив:

1. Безопасное хранение и обработку PHI (Protected Health Information)
2. Корректную интеграцию с backend-контрактом `/api/v1/mobile/*`
3. Воспроизводимую сборку и CI/CD
4. Покрытие бизнес-логики тестами ≥ 60%
5. Поддерживаемую архитектуру (не god-composables, не manual singletons)
6. Релиз в Google Play Internal Testing

### 1.3. Current vs Target по 7 направлениям

| Направление | Current | Target к M5 | Приоритет |
|---|---|---|---|
| Security харднинг | 🔴 7 ship-blocker-ов | 🟢 0 ship-blocker-ов, пройден security audit | P0 |
| Интеграция с бэкендом | 🔴 общий `ApiService`, нет refresh/2FA/logout | 🟢 full `/mobile/*` contract, refresh + 2FA + logout | P0–P1 |
| Архитектурный рефакторинг | 🔴 manual singletons, god-composables | 🟢 Hilt DI, экраны ≤ 400 LOC, domain-слой | P1–P2 |
| Тесты + CI/CD | 🔴 4 stub-теста, нет CI | 🟢 ≥ 60% coverage на data/domain, GitHub Actions | P1 |
| Room миграции | 🔴 destructive fallback | 🟢 явные миграции 1→N + тесты | P1 |
| Локализация | 🔴 hardcoded RU в 10 файлах | 🟢 `values/` + `values-en/` + pseudo-locale | P2 |
| Релиз-подготовка | 🔴 нет wrapper, нет подписи | 🟢 signed AAB, Play Console Internal Testing | P2 |

---

## 2. Принципы планирования

### 2.1. Приоритеты

| Приоритет | Значение | SLA |
|---|---|---|
| **P0** | Ship-blocker, безопасность, блокирует сборку | M0 (T+2 недели) |
| **P1** | Критичная функциональность и архитектура | M1–M2 (T+6…10 недель) |
| **P2** | Качество, i18n, релиз-инфраструктура | M3–M4 (T+10…14 недель) |
| **P3** | Nice-to-have, долг | M5+ (после релиза) |

### 2.2. Правило зависимостей

Задача **не начинается**, пока не закрыты все её зависимости (колонка `Depends on`). Зависимости указаны ID задачи в формате `E{эпик}.{номер}` — например, `E1.3` = эпик 1, задача 3.

### 2.3. Definition of Done (DoD)

Задача считается выполненной, когда:
- [ ] Код написан и закоммичен в feature-branch `feature/E{эпик}-{номер}-{slug}`
- [ ] PR открыт, прошла code review (даже self-review, с чек-листом в PR description)
- [ ] Если задача P0/P1 — есть unit-тест или обоснование в PR, почему тест не нужен
- [ ] PR влит в `main` через squash-merge
- [ ] Соответствующая строка в чек-листе (раздел 16) отмечена `[x]`
- [ ] При необходимости — обновлён README и этот roadmap

### 2.4. Конвенция коммитов

```
<type>(<scope>): <subject>

<body>
```

Типы: `feat`, `fix`, `refactor`, `test`, `build`, `ci`, `docs`, `chore`, `security`.
Scope — эпик, например `auth`, `sync`, `room`, `hilt`.

Пример:
```
security(auth): remove demo-bypass in AuthRepository.login

Closes E1.1. Production auth no longer accepts username="admin"/"patient"
with any password.
```

### 2.5. Ветки

- `main` — защищённая, только через PR, всегда собирается и проходит тесты
- `feature/E{эпик}-{номер}-{slug}` — feature-ветки
- `hotfix/{issue}` — критичные фиксы prod-сборки (после M5)

---

## 3. Milestones (M0–M5)

| Milestone | Срок | Критерий выхода |
|---|---|---|
| **M0 — Stabilize** | T + 2 недели | Все P0-задачи закрыты: security харднинг + сборка воспроизводима (есть wrapper, есть ProGuard rules). DEBUG-сборка собирается и запускается на эмуляторе. |
| **M1 — Integrate** | T + 6 недель | Backend-интеграция завершена: `/mobile/*` contract, token refresh, logout, 2FA flow. Room миграции описаны. Тесты на auth-флоу. |
| **M2 — Refactor** | T + 10 недель | Архитектура приведена к Hilt + domain-слой + feature-экраны ≤ 400 LOC. NetworkBoundResource используется во всех репозиториях. |
| **M3 — Quality** | T + 12 недель | Coverage ≥ 60% на data/domain. CI/CD зелёный. ktlint + detekt настроены и проходят. |
| **M4 — Polish** | T + 14 недель | Локализация (RU + EN). Релиз-keystore, signing config, AAB собирается. README приведён в соответствие с реальностью. |
| **M5 — Release** | T + 16 недель | AAB загружен в Play Console Internal Testing. Smoke-тесты на 3 устройствах (Android 10/13/15). Финальный чек-лист закрыт. |

---

## 4. Эпик 1 — Security харднинг (P0)

> **Milestone:** M0
> **Цель:** устранить 7 ship-blocker-ов, чтобы приложение могло безопасно попасть к реальным пользователям.

### E1.1 — Удалить demo-bypass в `AuthRepository.login` [P0]

**Файл:** `app/src/main/java/com/example/data/repository/AuthRepository.kt` (строки ~33–71)

**Что убрать:** ветки, где `username == "admin"` или `username == "patient"` (с любым паролем) возвращают фейковый `UserDto` и кладут `fake_demo_token_$username` в EncryptedSharedPreferences.

**Как проверить:** unit-тест `AuthRepositoryTest.login_rejectsDemoBypassCredentials` — ввод `"admin"/"x"` → ожидаем `AuthResult.Failure` или network error, **не** success.

**Сниппет см. в разделе 13.1.**

**Depends on:** —
**Commit:** `security(auth): remove demo-bypass in AuthRepository.login`

---

### E1.2 — Удалить чипы быстрого входа из `AuthScreen` [P0]

**Файл:** `app/src/main/java/com/example/ui/screens/AuthScreen.kt`

**Что убрать:** Composable `DemoSandboxToggleBar` (или аналогичный), кнопки "🚀 Тестовые аккаунты:" с предзаполнением `patient/password` и автовходом.

**Depends on:** E1.1
**Commit:** `security(auth): remove test-account quick-login chips from AuthScreen`

---

### E1.3 — Удалить `DemoSandboxToggleBar` из `MainActivity` [P0]

**Файл:** `app/src/main/java/com/example/MainActivity.kt`

**Что убрать:** рендер `DemoSandboxToggleBar` в главном Scaffold, который позволяет залогиненному пользователю переключаться между PATIENT и STAFF view без повторной аутентификации.

**Depends on:** —
**Commit:** `security(ui): remove DemoSandboxToggleBar from MainActivity`

---

### E1.4 — Гейт `HttpLoggingInterceptor` за `BuildConfig.DEBUG` [P0]

**Файл:** `app/src/main/java/com/example/data/api/ApiClient.kt`

**Что менять:** текущий код включает `HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }` безусловно. В release-сборке в Logcat льются JWT-токены, тела запросов с медицинскими данными.

**Целевое состояние:**
```kotlin
if (BuildConfig.DEBUG) {
    val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    okHttpClient.addInterceptor(logging)
}
```

**Depends on:** —
**Commit:** `security(net): gate HttpLoggingInterceptor behind BuildConfig.DEBUG`

---

### E1.5 — Реализовать `FLAG_SECURE` в `SecureScreen` [P0]

**Файл:** `app/src/main/java/com/example/ui/components/SecureScreen.kt`

**Что менять:** текущий `SecureScreen` Composable пустой. Реализовать установку `WindowManager.LayoutParams.FLAG_SECURE` на текущее окно — запрещает скриншоты и запись экрана. Обернуть все экраны с PHI (`PatientScreen`, `StaffScreen`, `AuthScreen`) в `SecureScreen`.

**Сниппет см. в разделе 13.8.**

**Depends on:** —
**Commit:** `security(ui): implement FLAG_SECURE in SecureScreen and apply to PHI screens`

---

### E1.6 — Audit `TokenManager` fallback на plain `SharedPreferences` [P0]

**Файл:** `app/src/main/java/com/example/utils/TokenManager.kt`

**Что менять:** текущий код при сбое создания `EncryptedSharedPreferences` (например, при повреждении keystore) тихо падает на обычный `SharedPreferences` — JWT может оказаться в cleartext.

**Целевое состояние:**
- Если `EncryptedSharedPreferences` не создаётся → **не** падать на plain Prefs
- Логировать ошибку в Crashlytics (или хотя бы `Log.e` в DEBUG)
- Показать пользователю диалог "Сбой безопасного хранилища, обратитесь к администратору" и выйти из приложения
- Опционально: попытаться очистить повреждённый keystore-ключ один раз и пересоздать

**Depends on:** —
**Commit:** `security(auth): fail closed on EncryptedSharedPreferences init failure`

---

### E1.7 — Удалить фейковый Firebase API-key из `FirestoreSyncManager` [P0]

**Файл:** `app/src/main/java/com/example/utils/FirestoreSyncManager.kt` (строки ~28–30)

**Что делать:**
1. Если Firestore реально не используется (только симуляция) — **удалить инициализацию Firestore целиком**, оставить только OkHttp WebSocket как real-time канал
2. Если планируется использовать — вынести ключ в `local.properties` / `secrets.gradle` (плагин уже подключён), не коммитить реальный ключ
3. Удалить строку `setApiKey("AIzaSyFakeKeyForRealtimeSyncSimulation")`

**Depends on:** —
**Commit:** `security(firebase): remove fake API key, disable Firestore init until properly configured`

---

### E1.8 — Security audit pass (вручную) [P0]

**Что делать:** пройтись по всему коду на поиск других захардкоженных секретов / debug-дверей. Проверить:
- `git grep -nE "password|secret|token|admin|test|demo" app/src/main/`
- `git grep -nE "TODO|FIXME|HACK" app/src/main/`
- `BuildConfig.DEBUG`-гейты на всех dev-only функциях
- `network_security_config.xml` — не должен уезжать в production cleartext-разрешения (см. E2.6)

**Depends on:** E1.1, E1.2, E1.3, E1.4, E1.5, E1.6, E1.7
**Commit:** `security: manual audit pass, no hardcoded secrets remaining`

---

## 5. Эпик 2 — Сборка и воспроизводимость (P0)

> **Milestone:** M0
> **Цель:** любой клон репозитория собирается командой `./gradlew assembleDebug` без ручной настройки.

### E2.1 — Добавить Gradle wrapper [P0]

**Что делать:**
```bash
gradle wrapper --gradle-version 8.9 --distribution-type bin
```

Зафиксировать `gradle-wrapper.properties`, `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` в Git. Проверить AGP 9.1.1 ↔ Gradle 8.9 совместимость (AGP 9.x требует Gradle 8.9+).

**Depends on:** —
**Commit:** `build: add Gradle wrapper 8.9`

---

### E2.2 — Переименовать namespace с `com.example` на `com.aistudio.clinicsystem` [P0]

**Файлы:** `app/build.gradle.kts`, `app/src/main/java/com/example/` → `app/src/main/java/com/aistudio/clinicsystem/`

**Что делать:**
1. Рефакторинг пакета через IDE (Android Studio → Refactor → Rename package)
2. Обновить `namespace = "com.aistudio.clinicsystem"` в `app/build.gradle.kts`
3. Обновить `applicationId` — оставить `com.aistudio.clinicsystem.hvyqt` или упростить до `com.aistudio.clinicsystem`
4. Проверить все `import` в `.kt`-файлах
5. Обновить `AndroidManifest.xml` (`package` атрибут, если есть)

**Depends on:** —
**Commit:** `refactor: rename package com.example → com.aistudio.clinicsystem`

---

### E2.3 — Переименовать `rootProject.name` с `My Application` [P0]

**Файл:** `settings.gradle.kts`

**Что менять:** `rootProject.name = "My Application"` → `rootProject.name = "ClinicSystemMobile"`

**Depends on:** —
**Commit:** `chore: rename root project to ClinicSystemMobile`

---

### E2.4 — Заполнить `proguard-rules.pro` keep-правилами [P0]

**Файл:** `app/proguard-rules.pro`

**Что добавить:** keep-правила для Moshi (codegen + reflection), Retrofit, Room, SQLCipher, Kotlin Metadata.

**Сниппет см. в разделе 13.2.**

**Depends on:** E2.1
**Commit:** `build: add ProGuard keep rules for Moshi/Retrofit/Room/SQLCipher`

---

### E2.5 — Обновить Compose BOM и зависимостей [P0]

**Файл:** `gradle/libs.versions.toml`

**Что менять:**
- Compose BOM `2024.09.00` → `2025.04.00` (или последний стабильный)
- Lifecycle `2.8.7` → `2.9.x`
- Navigation-Compose `2.8.9` → `2.9.x`
- Activity-Compose `1.10.1` → `1.11.x`
- Проверить `compileSdk = 36` + `minorApiLevel = 1` — на момент написания это preview, оставить только если AGP 9.1 форсирует

**Depends on:** E2.1
**Commit:** `build: bump Compose BOM and lifecycle/navigation dependencies`

---

### E2.6 — Production-grade `network_security_config.xml` [P1]

**Файл:** `app/src/main/res/xml/network_security_config.xml`

**Что менять:** текущий конфиг разрешает cleartext для `10.0.2.2` и `localhost`. Создать **два** конфига:
- `res/xml/network_security_config.xml` — production (только HTTPS, no cleartext)
- `res/xml/network_security_config_debug.xml` — debug (cleartext для `10.0.2.2`, `localhost`)

В `AndroidManifest.xml` сослаться через `<application android:networkSecurityConfig="@xml/network_security_config" ...>` и использовать `manifestPlaceholders` или `sourceSets` для подмены конфига в debug-сборке.

**Depends on:** E2.1
**Commit:** `security(net): split network security config for debug/release`

---

### E2.7 — Вынос `BASE_URL` в `BuildConfig` [P1]

**Файлы:** `app/build.gradle.kts`, `ApiClient.kt`

**Что делать:**
```kotlin
// app/build.gradle.kts
android {
    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:18000/api/v1/\"")
        }
        release {
            buildConfigField("String", "BASE_URL", "\"https://api.clinic.example.com/api/v1/\"")
        }
    }
}
```

В `ApiClient.kt` использовать `BuildConfig.BASE_URL` вместо хардкода.

**Depends on:** E2.2
**Commit:** `build: externalize BASE_URL to BuildConfig per build type`

---

## 6. Эпик 3 — Интеграция с бэкендом (P0–P1)

> **Milestone:** M1
> **Цель:** мобильный клиент использует контракт `/api/v1/mobile/*`, реализует refresh / 2FA / logout, корректно обрабатывает 401.

### E3.1 — Перейти на `/api/v1/mobile/*` контракт [P0]

**Файлы:** `app/src/main/java/com/example/data/api/ApiService.kt` (новый), `AuthRepository.kt`, `ClinicRepository.kt`

**Что делать:**
1. Создать `MobileApiService.kt` с endpoints из `/api/v1/mobile/*` (26 путей):
   - `POST /mobile/auth/login` (проверить — бэкенд имеет баг `/mobile/mobile/auth/login` с двойным префиксом; завести issue на бэкенд, использовать рабочий путь)
   - `GET /mobile/patients/me`
   - `PUT /mobile/profile`
   - `POST /mobile/profile/avatar`
   - `GET /mobile/appointments/upcoming`
   - `POST /mobile/appointments/book`
   - `POST /mobile/appointments/cancel`
   - `POST /mobile/appointments/reschedule`
   - `POST /mobile/doctors/search`
   - `GET /mobile/doctors/{id}/schedule`
   - `GET /mobile/queues/status`
   - `GET /mobile/queues/my-position`
   - `GET /mobile/lab/results`
   - `GET /mobile/notifications`
   - `POST /mobile/notifications/{id}/read`
   - `GET/PUT /mobile/settings/notifications`
   - `POST /mobile/services/search`
   - `GET /mobile/services/categories`
   - `GET /mobile/clinic/info`
   - `GET /mobile/stats`
   - `POST /mobile/feedback`
   - `POST /mobile/emergency/contact`
2. Сгенерировать Moshi DTOs из `backend/openapi.json` (можно через `openapi-generator-cli` с `moshi-kotlin` profile, или вручную — там ~30 схем для mobile)
3. Заменить вызовы в репозиториях со старого `ApiService` на новый `MobileApiService`
4. Удалить старый `ApiService.kt` после миграции

**Depends on:** E1.1, E2.7
**Commit:** `feat(api): migrate to /mobile/* contract`

---

### E3.2 — Реализовать token refresh через `/authentication/refresh` [P0]

**Файлы:** `TokenManager.kt`, новый `TokenAuthenticator.kt`

**Что делать:**
1. Добавить в `TokenManager` хранение `refreshToken` (бэкенд отдаёт `access_token` + `refresh_token` в `/authentication/login`)
2. Создать `TokenAuthenticator : Authenticator` (OkHttp) — перехватывает 401, делает синхронный запрос на `POST /authentication/refresh` с refresh-токеном, обновляет оба токена в `TokenManager`, ретраит оригинальный запрос с новым access-токеном
3. Сериализовать concurrent refresh-запросы через `Mutex` (несколько параллельных 401 не должны триггерить N refresh-запросов)
4. Если refresh упал с 401 — очистить сессию и триггерить logout flow

**Сниппет см. в разделе 13.5.**

**Depends on:** E3.1
**Commit:** `feat(auth): implement token refresh via /authentication/refresh`

---

### E3.3 — Реализовать logout с инвалидацией сессии на сервере [P0]

**Файлы:** `AuthRepository.kt`, `SessionManager.kt`

**Что делать:**
1. `POST /authentication/logout` — бэкенд инвалидирует сессию в `UserSession` таблице и добавляет access-token в blacklist
2. После успешного ответа — очистить `TokenManager` (access + refresh), очистить `SessionManager`, очистить Room (или пометить записи как `logged_out`), отменить `SyncWorker`
3. Если запрос упал с network error — оставить токены, показать пользователю retry
4. Если запрос упал с 401 — токены уже невалидны, локально очистить, перевести на AuthScreen

**Depends on:** E3.1, E3.2
**Commit:** `feat(auth): implement server-side logout with session invalidation`

---

### E3.4 — Реализовать 2FA challenge flow [P1]

**Файлы:** `AuthRepository.kt`, `AuthViewModel.kt`, `AuthScreen.kt`, новый `TwoFactorScreen.kt`

**Что делать:**
1. `POST /authentication/login` может вернуть `{"requires_2fa": true, "challenge_token": "..."}` вместо access-токена — обработать это в `AuthRepository.login`
2. Добавить state в `AuthViewModel`: `LoginState.TwoFactorRequired(challengeToken)`
3. Создать `TwoFactorScreen` — ввод 6-значного кода, кнопка "Отправить" → `POST /2fa/verify-code`
4. Реализовать fallback на recovery code: `POST /2fa/recovery/request`
5. Реализовать "Доверенное устройство" — checkbox, после успешного 2FA вызвать `POST /2fa/devices` для регистрации устройства

**Сниппет см. в разделе 13.6.**

**Depends on:** E3.1
**Commit:** `feat(auth): implement 2FA challenge flow with recovery and trusted device`

---

### E3.5 — Синхронизация ролей с бэкендом [P1]

**Файлы:** `UserDto.kt`, `SessionManager.kt`, `ClinicNavGraph.kt`, `ClinicViewModel.kt`

**Что делать:**
1. Бэкенд имеет 9 ролей: `Admin, Doctor, Registrar (Receptionist), Lab, Cashier, cardio, derma, dentist, Patient`. Заменить текущий enum `Role { PATIENT, STAFF }` на полный enum, выровненный с бэкендом
2. В `ClinicNavGraph` реализовать маршрутизацию по ролям:
   - `Patient` → `PatientScreen`
   - `Doctor`, `cardio`, `derma`, `dentist` → `DoctorScreen` (новый — вынести из `StaffScreen`)
   - `Registrar` → `RegistrarScreen` (новый)
   - `Lab` → `LabScreen` (новый)
   - `Cashier` → `CashierScreen` (новый)
   - `Admin` → `AdminScreen` (новый)
3. Это часть эпика 5 (архитектурный рефакторинг) — здесь только модель и навигация, экраны выносятся в E5.5

**Depends on:** E3.1
**Commit:** `feat(auth): align role enum with backend, role-based routing`

---

### E3.6 — Исправить URL WebSocket на `/ws/queue` [P1]

**Файлы:** `ClinicWebSocketClient.kt`

**Что делать:**
1. Бэкенд имеет WebSocket endpoints на корне (не под `/api/v1`): `/ws/queue`, `/ws/chat`, `/ws/dev-queue`
2. Проверить текущий URL в `ClinicWebSocketClient` — он должен быть `{BASE_URL_without_/api/v1}/ws/queue`
3. Реализовать subscribe на конкретный queue-id: `{"type": "subscribe", "queue_id": "..."}` после подключения
4. Покрыть unit-тестом `handleSocketMessage` для всех типов сообщений: `queue_updated`, `position_changed`, `patient_called`, `queue_closed`

**Depends on:** E2.7
**Commit:** `fix(ws): correct WebSocket URL to /ws/queue, add subscribe handshake`

---

### E3.7 — Обработка 401 от `AuthInterceptor` с авто-refresh [P1]

**Файлы:** `AuthInterceptor.kt`, `ApiClient.kt`

**Что делать:** текущий `AuthInterceptor` добавляет `Authorization: Bearer ...` ко всем запросам. На 401 он вызывает `onUnauthorized` callback, который просто переводит пользователя на AuthScreen. Это неправильно: 401 может быть из-за истёкшего access-токена, который нужно refresh-нуть, а не logout-ить.

**Решение:** заменить `AuthInterceptor` + ручной `onUnauthorized` на `TokenAuthenticator` (см. E3.2). `Authenticator` перехватывает именно 401 и сам решает — refresh или logout.

**Depends on:** E3.2
**Commit:** `refactor(auth): replace AuthInterceptor.onUnauthorized with TokenAuthenticator`

---

## 7. Эпик 4 — Room миграции (P1)

> **Milestone:** M1
> **Цель:** убрать `fallbackToDestructiveMigration`, включить `exportSchema=true`, описать явные миграции.

### E4.1 — Включить `exportSchema = true` [P1]

**Файл:** `app/src/main/java/com/example/data/db/ClinicDatabase.kt`

**Что менять:**
```kotlin
@Database(
    version = 4,
    entities = [...],
    exportSchema = true  // было false
)
abstract class ClinicDatabase : RoomDatabase() { ... }
```

В `app/build.gradle.kts` добавить:
```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

После первой сборки в `app/schemas/com.aistudio.clinicsystem.data.db.ClinicDatabase/4.json` появится JSON-схема текущей БД. Закоммитить её.

**Depends on:** E2.2
**Commit:** `build(room): enable exportSchema, configure schema directory`

---

### E4.2 — Убрать `fallbackToDestructiveMigration` [P1]

**Файл:** `ClinicDatabase.kt`, `ClinicDatabase.getDatabase(...)`

**Что менять:**
```kotlin
// Было:
Room.databaseBuilder(context, ClinicDatabase::class.java, "clinic.db")
    .fallbackToDestructiveMigration()
    .build()

// Стало:
Room.databaseBuilder(context, ClinicDatabase::class.java, "clinic.db")
    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    .build()
```

**Depends on:** E4.1
**Commit:** `refactor(room): remove fallbackToDestructiveMigration`

---

### E4.3 — Описать миграции 1→2, 2→3, 3→4 [P1]

**Файл:** новый `app/src/main/java/com/example/data/db/Migrations.kt`

**Что делать:** так как текущая версия 4 и `exportSchema` был `false` — у нас нет истории схем 1, 2, 3. Поэтому:
1. Зафиксировать текущую схему 4 как baseline (через `exportSchema=true` после E4.1)
2. Создать `MIGRATION_4_5` (для будущих изменений) как заглушку-пример
3. Для версий 1→2→3→4 — если есть информация о том, что менялось, описать; иначе — задокументировать в комментарии, что история утеряна, и любая установка со старой версией БД будет считаться clean install (через `fallbackToDestructiveMigrationOnDowngrade` только для downgrade)
4. Реальный production-риски: пользователи, у которых сейчас стоит версия 4, при обновлении приложения до версии с миграцией 4→5 должны пройти её корректно. Миграции 1→4 никогда не существовали, и они не нужны для новых пользователей.

**Сниппет см. в разделе 13.7.**

**Depends on:** E4.2
**Commit:** `feat(room): add migration 4→5 template with test`

---

### E4.4 — Тесты на миграции [P1]

**Файл:** `app/src/test/java/com/example/data/db/MigrationTest.kt`

**Что делать:** использовать `MigrationTestHelper` из `androidx.room:room-testing`:
```kotlin
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        databaseClass = ClinicDatabase::class.java
    )

    @Test
    fun migrate4To5_preservesData() {
        // 1. Создать БД версии 4, заполнить тестовыми данными
        // 2. Запустить миграцию
        // 3. Проверить, что данные на месте, схема = 5
    }
}
```

**Depends on:** E4.3
**Commit:** `test(room): add migration test for 4→5`

---

## 8. Эпик 5 — Архитектурный рефакторинг (P1–P2)

> **Milestone:** M2
> **Цель:** Hilt DI, domain-слой, экраны ≤ 400 LOC, NetworkBoundResource используется повсеместно.

### E5.1 — Внедрение Hilt [P1]

**Файлы:** `app/build.gradle.kts`, `libs.versions.toml`, новый `ClinicApplication.kt`, `MainActivity.kt`

**Что делать:**
1. Добавить зависимости Hilt (`hilt-android`, `hilt-compiler`, `hilt-navigation-compose`)
2. Создать `ClinicApplication` с `@HiltAndroidApp`
3. Зарегистрировать в `AndroidManifest.xml` (`android:name=".ClinicApplication"`)
4. Создать `AppModule` (предоставляет `Context`, `RoomDatabase`, `Retrofit`, `OkHttpClient`, `TokenManager`, `WorkManager`)
5. Создать feature-модули: `AuthModule`, `ClinicModule`, `SyncModule`
6. Заменить `SessionManagerImpl.getInstance()` и другие ручные singletons на `@Inject`/`@Singleton`
7. `MainActivity` пометить `@AndroidEntryPoint`
8. Все ViewModels — `@HiltViewModel`, инжектить через `@Inject constructor`

**Сниппет см. в разделе 13.3.**

**Depends on:** E2.2, E2.1
**Commit:** `refactor: introduce Hilt DI, replace manual singletons`

---

### E5.2 — Создать `domain/` слой с UseCase-ами [P1]

**Структура:**
```
app/src/main/java/com/aistudio/clinicsystem/domain/
├── model/        # чистые Kotlin-классы (без Room/Retrofit аннотаций)
│   ├── User.kt
│   ├── Appointment.kt
│   ├── QueueEntry.kt
│   ├── MedicalRecord.kt
│   └── ...
├── repository/   # интерфейсы репозиториев
│   ├── AuthRepository.kt
│   ├── ClinicRepository.kt
│   └── SyncRepository.kt
└── usecase/
    ├── auth/
    │   ├── LoginUseCase.kt
    │   ├── RefreshTokenUseCase.kt
    │   ├── LogoutUseCase.kt
    │   └── Verify2FAUseCase.kt
    ├── appointment/
    │   ├── BookAppointmentUseCase.kt
    │   ├── CancelAppointmentUseCase.kt
    │   └── GetUpcomingAppointmentsUseCase.kt
    ├── queue/
    │   ├── GetQueueStatusUseCase.kt
    │   └── SubscribeToQueueUseCase.kt
    └── sync/
        ├── SyncPendingUseCase.kt
        └── GetPendingSyncCountUseCase.kt
```

Каждый UseCase — `class XxxUseCase @Inject constructor(private val repo: XxxRepository) { suspend operator fun invoke(...): Result<T> }`

Репозитории в `data/repository/` реализуют интерфейсы из `domain/repository/`. DTO ↔ Domain-model маппинг — в `data/mapper/`.

**Depends on:** E5.1
**Commit:** `refactor: introduce domain layer with UseCase pattern`

---

### E5.3 — Внедрить `NetworkBoundResource` во всех репозиториях [P1]

**Файлы:** `NetworkBoundResource.kt` (уже есть), `ClinicRepository.kt`, `AuthRepository.kt` (где применимо)

**Что делать:** текущий `NetworkBoundResource<ResultType, RequestType>` реализован, но **не используется**. Переписать `ClinicRepository.getAppointments()`, `getQueues()`, `getMedicalRecords()` и т.д. на шаблон `networkBoundResource`:

```kotlin
override fun getAppointments(): Flow<Resource<List<Appointment>>> =
    networkBoundResource(
        query = { appointmentDao.getAll() },
        fetch = { mobileApiService.getAppointmentsUpcoming() },
        saveFetchResult = { dto ->
            appointmentDao.upsertAll(dto.toEntityList())
        },
        shouldFetch = { cached ->
            cached.isEmpty() || isOnline()
        }
    )
```

Сниппет полного использования — см. раздел 13.4.

**Depends on:** E3.1, E5.2
**Commit:** `refactor: use NetworkBoundResource in all repositories`

---

### E5.4 — Разбить `PatientScreen.kt` (2 183 LOC) на feature-композаблы [P1]

**Файлы:** удалить `PatientScreen.kt`, создать:

```
app/src/main/java/com/aistudio/clinicsystem/ui/screens/patient/
├── PatientScreen.kt          # корневой Scaffold, NavHost, ~150 LOC
├── profile/
│   ├── ProfileSection.kt     # просмотр/редактирование профиля
│   └── ProfileEditDialog.kt
├── appointments/
│   ├── AppointmentsListSection.kt
│   ├── BookingDialog.kt
│   └── AppointmentDetailsDialog.kt
├── records/
│   └── MedicalRecordsSection.kt
├── queue/
│   └── QueueSection.kt       # текущая позиция, время ожидания
└── telegram/
    └── TelegramLinkPanel.kt
```

Каждый Composable ≤ 400 LOC, использует `@HiltViewModel` для state. State-hoisting: чистые Composable-функции, состояние передаётся через параметры, события — через лямбды.

**Depends on:** E5.1, E5.2
**Commit:** `refactor(ui): split PatientScreen into feature composables`

---

### E5.5 — Разбить `StaffScreen.kt` (2 079 LOC) по ролям [P1]

**Файлы:** удалить `StaffScreen.kt`, создать:

```
app/src/main/java/com/aistudio/clinicsystem/ui/screens/role/
├── DoctorScreen.kt       # для Doctor, cardio, derma, dentist
├── RegistrarScreen.kt    # для Registrar (Receptionist)
├── LabScreen.kt          # для Lab
├── CashierScreen.kt      # для Cashier
├── AdminScreen.kt        # для Admin
└── shared/
    ├── AppointmentsApprovalSection.kt  # используется в Doctor, Registrar
    ├── QueueManagementSection.kt       # используется в Registrar, Doctor
    └── MedicalRecordCreateSection.kt   # используется в Doctor
```

См. также E3.5 — навигация по ролям.

**Depends on:** E3.5, E5.4
**Commit:** `refactor(ui): split StaffScreen into role-based screens`

---

### E5.6 — Удалить `SyncConsoleView` из production или скрыть за `BuildConfig.DEBUG` [P2]

**Файлы:** `MainActivity.kt`, `SyncConsoleView.kt`

**Что делать:** `SyncConsoleView` — debug-инструмент (логи sync, pending-queue, security tab). Не должен быть виден в release-сборке.

Вариант 1: удалить из `MainActivity` Scaffold
Вариант 2 (лучше): оставить, но рендерить только в debug:
```kotlin
if (BuildConfig.DEBUG) {
    SyncConsoleView(...)
}
```

**Depends on:** E1.4
**Commit:** `refactor(ui): hide SyncConsoleView behind BuildConfig.DEBUG`

---

### E5.7 — Реализовать `AnalyticsManager` с Firebase Analytics [P3]

**Файлы:** `AnalyticsManager.kt`

**Что делать:** текущий `AnalyticsManager` — заглушка с `Log.i("Event Traacked: ...")` (с typo). Либо:
- Удалить полностью и удалить все вызовы
- Либо реализовать с Firebase Analytics (нужно добавить зависимость `firebase-analytics` — Firebase BOM уже подключён)

**Depends on:** E1.7 (т.к. связан с Firebase setup)
**Commit:** `feat(analytics): implement Firebase Analytics or remove AnalyticsManager`

---

## 9. Эпик 6 — Тесты (P1–P2)

> **Milestone:** M3
> **Цель:** coverage ≥ 60% на `data/` + `domain/`, screenshot-тесты на ключевые экраны.

### E6.1 — Unit-тесты на `AuthRepository` [P1]

**Файл:** `app/src/test/java/.../data/repository/AuthRepositoryTest.kt`

**Сценарии:**
- `login_success_storesTokens`
- `login_invalidCredentials_returnsFailure`
- `login_2faRequired_returnsChallengeToken`
- `login_networkError_returnsFailure`
- `login_rejectsDemoBypassCredentials` (E1.1)
- `refreshToken_success_updatesBothTokens`
- `refreshToken_failure_clearsSession`
- `logout_success_clearsLocalState`
- `logout_networkError_keepsSession`

Использовать MockWebServer для HTTP-моков, `mockk` для моков DAO и `TokenManager`.

**Depends on:** E3.1, E3.2, E3.3
**Commit:** `test(auth): add unit tests for AuthRepository`

---

### E6.2 — Unit-тесты на `ClinicRepository` + `NetworkBoundResource` usage [P1]

**Файл:** `app/src/test/java/.../data/repository/ClinicRepositoryTest.kt`

**Сценарии:**
- `getAppointments_online_fetchesFromNetworkAndCaches`
- `getAppointments_offline_returnsCachedData`
- `getAppointments_cacheEmpty_offline_returnsEmpty`
- `bookAppointment_success_addsToPendingSync`
- `bookAppointment_networkError_addsToPendingSync`
- `syncPending_success_emptiesQueue`
- `syncPending_partialFailure_retriesFailedItems`

**Depends on:** E5.3
**Commit:** `test(clinic): add unit tests for ClinicRepository`

---

### E6.3 — Unit-тесты на `ClinicWebSocketClient.handleSocketMessage` [P1]

**Файл:** `app/src/test/java/.../utils/ClinicWebSocketClientTest.kt`

**Сценарии для каждого типа WS-сообщения:**
- `queue_updated` → обновляет Room, эмитит Flow
- `position_changed` → обновляет Room, эмитит Flow, показывает notification
- `patient_called` → показывает notification с вибрацией
- `queue_closed` → обновляет state
- `unknown_message` → логирует warning, не падает
- `malformed_json` → логирует error, не падает

**Depends on:** E3.6
**Commit:** `test(ws): add unit tests for WebSocket message handling`

---

### E6.4 — Unit-тесты на `SyncWorker` [P1]

**Файл:** `app/src/test/java/.../utils/SyncWorkerTest.kt`

**Сценарии:**
- `doWork_pendingItems_allSucceed_returnsSuccess`
- `doWork_pendingItems_someFail_returnsRetry`
- `doWork_noPending_returnsSuccess`
- `doWork_networkUnavailable_returnsRetry`
- `clientRequestId_deduplication_noDoubleApply`

Использовать `TestWorkerBuilder` из `androidx.work:work-testing`.

**Depends on:** E5.3
**Commit:** `test(sync): add unit tests for SyncWorker`

---

### E6.5 — Unit-тесты на ViewModels [P2]

**Файлы:** `AuthViewModelTest.kt`, `PatientViewModelTest.kt`, `StaffViewModelTest.kt` (после разбития — по одному на role-VM)

**Что делать:**
- Использовать `@OptIn(ExperimentalCoroutinesApi::class)` + `runTest`
- `Dispatchers.setMain(StandardTestDispatcher())`
- Mock UseCase-ы через `mockk`
- Проверять state-переходы: Loading → Success / Error

**Depends on:** E5.2, E5.4, E5.5
**Commit:** `test(vm): add unit tests for ViewModels`

---

### E6.6 — Roborazzi screenshot-тесты на ключевые экраны [P2]

**Файлы:** `app/src/test/java/.../ui/screens/*ScreenshotTest.kt`

**Что делать:** для каждого из `AuthScreen`, `PatientScreen` (после разбития), `DoctorScreen`, `RegistrarScreen`, `TwoFactorScreen` — сделать screenshot-тест в трёх состояниях:
- Empty (no data)
- Loading
- Loaded with sample data
- Error

Запускать через `./gradlew verifyRoborazzi`. Сохранить baseline в `src/test/snapshots/`.

**Depends on:** E5.4, E5.5
**Commit:** `test(ui): add Roborazzi screenshot tests for key screens`

---

### E6.7 — Покрытие Jacoco + репорт [P2]

**Файлы:** `app/build.gradle.kts`

**Что делать:**
```kotlin
plugins {
    id("jacoco")
}

android {
    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
    }
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
```

Цель: `data/` + `domain/` ≥ 60% line coverage. Добавить в CI (см. E7.1).

**Depends on:** E6.1, E6.2, E6.3, E6.4, E6.5
**Commit:** `build: configure Jacoco coverage report`

---

## 10. Эпик 7 — CI/CD (P1)

> **Milestone:** M3
> **Цель:** каждый PR автоматически проверяется на lint, тесты, сборку.

### E7.1 — Базовый GitHub Actions workflow [P1]

**Файл:** `.github/workflows/android.yml`

**Сниппет см. в разделе 13.9.**

Шаги:
1. Checkout
2. Setup JDK 17 (требование AGP 9.x)
3. Setup Gradle 8.9
4. Cache Gradle dependencies + wrapper
5. `./gradlew ktlintCheck` (см. E7.2)
6. `./gradlew detekt` (см. E7.2)
7. `./gradlew assembleDebug`
8. `./gradlew testDebugUnitTest`
9. `./gradlew lintDebug`
10. `./gradlew jacocoTestReport`
11. Upload APK artifact
12. Upload coverage report (опционально — Codecov)

Триггер: `push` на `main` + `pull_request` на `main`.

**Depends on:** E2.1
**Commit:** `ci: add GitHub Actions workflow for Android`

---

### E7.2 — Настроить `ktlint` + `detekt` [P1]

**Файлы:** `app/build.gradle.kts`, `config/detekt.yml`, `libs.versions.toml`

**Что делать:**
1. Добавить плагины `org.jlleitschuh.gradle.ktlint` (версия `12.1.0`) и `io.gitlab.arturbosch.detekt` (версия `1.23.7`)
2. В `config/detekt.yml` задать правила (можно начать с дефолтного `detekt --generate-config`)
3. Запустить `./gradlew ktlintFormat` для авто-формата существующего кода
4. Фиксировать нарушения в `detekt baseline` (`detektBaseline`) для постепенного внедрения

**Depends on:** E2.1
**Commit:** `build: configure ktlint and detekt`

---

### E7.3 — Branch protection на `main` [P1]

**Что делать (в GitHub UI):**
- Settings → Branches → Add rule for `main`
- Require pull request before merging
- Require status checks: `ktlint`, `detekt`, `assembleDebug`, `testDebugUnitTest`, `lintDebug`
- Require branches up-to-date before merging
- Require linear history (squash-merge)

**Depends on:** E7.1, E7.2
**Commit:** (нет — настройка в GitHub UI)

---

### E7.4 — Автоматический release на теги [P2]

**Файл:** `.github/workflows/release.yml`

**Что делать:**
- Триггер: `push` tag `v*.*.*`
- Шаги: собрать release AAB (нужен keystore — см. E9.1), подписать, загрузить в Play Console Internal Testing через `r0adkll/upload-google-play@v1`
- Создать GitHub Release с APK-артефактом

**Depends on:** E7.1, E9.1, E9.2
**Commit:** `ci: add release workflow for tagged versions`

---

## 11. Эпик 8 — Локализация (P2)

> **Milestone:** M4

### E8.1 — Извлечение захардкоженных строк в `strings.xml` [P2]

**Файлы:** все `.kt` в `app/src/main/java/.../ui/screens/` (10 файлов с захардкоженными RU-строками)

**Что делать:**
1. Пройтись по всем Composable, найти строковые литералы на русском
2. Заменить на `stringResource(R.string.xxx)`
3. Добавить запись в `app/src/main/res/values/strings.xml`

Пример:
```kotlin
// Было:
Text("Запись на приём")

// Стало:
Text(stringResource(R.string.booking_title))
```

```xml
<!-- values/strings.xml -->
<string name="booking_title">Запись на приём</string>
```

**Depends on:** E5.4, E5.5 (легче после разбития экранов)
**Commit:** `i18n: extract hardcoded RU strings to strings.xml`

---

### E8.2 — Создать `values-en/` с английским переводом [P2]

**Файл:** `app/src/main/res/values-en/strings.xml`

**Что делать:** перевести все строки из `values/strings.xml` на английский. Использовать медицинскую терминологию корректно (appointment, medical record, queue, prescription, lab result).

**Depends on:** E8.1
**Commit:** `i18n: add English locale (values-en)`

---

### E8.3 — Настроить pseudo-locale для тестов [P2]

**Файл:** `app/src/main/res/values/pseudo/strings.xml`

**Что делать:** Android Studio умеет генерировать pseudo-locale (`Pseudo Locales` в Build Variant). Включить в `build.gradle.kts`:
```kotlin
android {
    buildFeatures {
        pseudoLocalesEnabled = true
    }
}
```

Запускать UI на pseudo-locale, чтобы ловить:
- Переполнение layout (pseudo добавляет `[ ... ]` вокруг каждой строки)
- Хардкод (pseudo-строки не меняются, если строка в коде, а не в ресурсах)

**Depends on:** E8.1
**Commit:** `i18n: enable pseudo-locale for testing`

---

### E8.4 — Локализация Plurals + Date/Time [P3]

**Файл:** `app/src/main/res/values/plurals.xml`

**Что делать:** для строк типа "N пациентов" использовать `<plurals>`:
```xml
<plurals name="patients_count">
    <item quantity="one">%d пациент</item>
    <item quantity="few">%d пациента</item>
    <item quantity="many">%d пациентов</item>
    <item quantity="other">%d пациентов</item>
</plurals>
```

Дата/время — через `DateTimeFormatter` с `Locale.getDefault()`, не через хардкод формата.

**Depends on:** E8.1
**Commit:** `i18n: add plurals and locale-aware date formatting`

---

## 12. Эпик 9 — Релиз-подготовка (P2)

> **Milestone:** M4–M5

### E9.1 — Генерация release keystore [P2]

**Что делать:**
1. `keytool -genkeypair -v -keystore clinic-release.keystore -alias clinic -keyalg RSA -keysize 4096 -validity 10000`
2. **НЕ коммитить** keystore в Git. Хранить локально или в CI secrets.
3. Пароль и alias — в `~/.gradle/gradle.properties` (не в проекте):
   ```
   CLINIC_KEYSTORE_PATH=/path/to/clinic-release.keystore
   CLINIC_KEYSTORE_PASSWORD=...
   CLINIC_KEY_ALIAS=clinic
   CLINIC_KEY_PASSWORD=...
   ```

**Depends on:** —
**Commit:** (нет — keystore не коммитится)

---

### E9.2 — Конфигурация `signingConfigs` в `build.gradle.kts` [P2]

**Файл:** `app/build.gradle.kts`

```kotlin
android {
    signingConfigs {
        create("release") {
            val props = Properties().apply {
                load(File(System.getProperty("user.home"), ".gradle/gradle.properties").inputStream())
            }
            storeFile = file(props["CLINIC_KEYSTORE_PATH"] as String)
            storePassword = props["CLINIC_KEYSTORE_PASSWORD"] as String
            keyAlias = props["CLINIC_KEY_ALIAS"] as String
            keyPassword = props["CLINIC_KEY_PASSWORD"] as String
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

Для CI — использовать `secrets.CLINIC_KEYSTORE_BASE64` (base64-encoded keystore в GitHub secrets), декодировать в workflow перед сборкой.

**Depends on:** E2.4, E9.1
**Commit:** `build: configure release signing config`

---

### E9.3 — Стратегия versioning [P2]

**Файл:** `app/build.gradle.kts`

**Что делать:** перейти на SemVer:
```kotlin
android {
    defaultConfig {
        versionCode = 1  // инкремент на каждый release
        versionName = "1.0.0"  // major.minor.patch
    }
}
```

Правила:
- `versionCode` — всегда инкрементируется (Play Console требует монотонно)
- `versionName` — SemVer:
  - `major` — breaking change в UX или контракте
  - `minor` — новая фича
  - `patch` — bugfix

**Depends on:** —
**Commit:** `build: enforce SemVer versioning`

---

### E9.4 — Play Console setup + Internal Testing [P2]

**Что делать (в Play Console UI):**
1. Создать приложение `Clinic System` (если ещё нет)
2. Заполнить store listing: описание, иконки, скриншоты
3. Privacy policy URL (нужен публично доступный URL)
4. Content rating questionnaire
5. Target audience: 13+
6. Upload первый AAB в Internal Testing
7. Добавить tester email-ы
8. Получить invite-link, открыть на тестовом устройстве

**Depends on:** E9.1, E9.2, E9.3
**Commit:** (нет — настройка в Play Console)

---

### E9.5 — R8 full mode + resource shrinking [P2]

**Файл:** `gradle.properties` + `app/build.gradle.kts`

**Что делать:**
1. В `gradle.properties`:
   ```
   android.enableR8.fullMode=true
   ```
2. В `app/build.gradle.kts` для release:
   ```kotlin
   release {
       isMinifyEnabled = true
       isShrinkResources = true
   }
   ```
3. Прогнать release-сборку локально, проверить, что приложение запускается (R8 full mode может удалить что-то нужное, если ProGuard rules неполные)
4. При необходимости — дополнить `proguard-rules.pro`

**Depends on:** E2.4, E9.2
**Commit:** `build: enable R8 full mode and resource shrinking`

---

### E9.6 — Обновить README до соответствия реальности [P2]

**Файл:** `README.md`

**Что делать:** текущий README врёт — заявляет Clean Architecture + Hilt + 8 экранов + 4 роли + GitHub Actions + LICENSE + CONTRIBUTING. После завершения M4 — обновить:
- Реальный стек (Kotlin 2.2.x, Compose BOM, Hilt, Room, SQLCipher)
- Реальные экраны (PatientScreen + role-экраны после E5.5)
- Реальные роли (9 штук после E3.5)
- Ссылка на CI badge
- Добавить LICENSE-файл (MIT, как и заявлено)
- Добавить CONTRIBUTING.md
- Удалить ссылки на несуществующие `docs/` файлы

**Depends on:** все остальные эпики
**Commit:** `docs: update README to match reality, add LICENSE and CONTRIBUTING`

---

## 13. Сниппеты кода для критичных мест

### 13.1 — `AuthRepository` без demo-bypass (E1.1)

```kotlin
// app/src/main/java/com/aistudio/clinicsystem/data/repository/AuthRepository.kt

class AuthRepository @Inject constructor(
    private val mobileApiService: MobileApiService,
    private val tokenManager: TokenManager,
    private val sessionManager: SessionManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AuthRepositoryInterface {

    override suspend fun login(
        username: String,
        password: String,
        deviceFingerprint: String
    ): Result<LoginOutcome> = withContext(ioDispatcher) {
        return@withContext try {
            val response = mobileApiService.login(
                LoginRequest(
                    username = username.trim(),
                    password = password,
                    device_fingerprint = deviceFingerprint,
                    remember_me = true
                )
            )

            when {
                response.requires_2fa == true -> {
                    Result.success(
                        LoginOutcome.TwoFactorRequired(
                            challengeToken = response.challenge_token
                                ?: return@withContext Result.failure(IllegalStateException("2FA required but no challenge token"))
                        )
                    )
                }
                response.access_token != null && response.refresh_token != null -> {
                    tokenManager.saveTokens(
                        accessToken = response.access_token,
                        refreshToken = response.refresh_token
                    )
                    sessionManager.saveUser(response.user.toDomain())
                    Result.success(LoginOutcome.Success(response.user.toDomain()))
                }
                else -> {
                    Result.failure(IllegalStateException("Malformed login response"))
                }
            }
        } catch (e: HttpException) {
            if (e.code() == 401) {
                Result.failure(AuthError.InvalidCredentials)
            } else {
                Result.failure(e)
            }
        } catch (e: IOException) {
            Result.failure(AuthError.NetworkError(e))
        }
    }
}

sealed class LoginOutcome {
    data class Success(val user: User) : LoginOutcome()
    data class TwoFactorRequired(val challengeToken: String) : LoginOutcome()
}

sealed class AuthError : Throwable() {
    object InvalidCredentials : AuthError()
    data class NetworkError(val cause: Throwable) : AuthError()
    object SessionExpired : AuthError()
}
```

---

### 13.2 — `proguard-rules.pro` keep-rules (E2.4)

```proguard
# ====================================
# Moshi
# ====================================
-keepclassmembers,allowobfuscation class * {
    @com.squareup.moshi.JsonClass <fields>;
}
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.* <methods>;
}
-keep class **JsonAdapter { *; }

# ====================================
# Retrofit
# ====================================
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# ====================================
# OkHttp
# ====================================
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ====================================
# Room
# ====================================
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# ====================================
# SQLCipher
# ====================================
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.**

# ====================================
# Coroutines
# ====================================
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ====================================
# Compose
# ====================================
-dontwarn androidx.compose.**

# ====================================
# Hilt
# ====================================
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.* { *; }

# ====================================
# Kotlin Metadata (нужно для reflection)
# ====================================
-keepattributes KotlinMetadata
-keep class kotlin.Metadata { *; }
```

---

### 13.3 — Hilt Application + Module (E5.1)

```kotlin
// app/src/main/java/com/aistudio/clinicsystem/ClinicApplication.kt

@HiltAndroidApp
class ClinicApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Инициализация WorkManager с HiltWorkerFactory
        val configuration = Configuration.Builder()
            .setWorkerFactory(HiltWorkerFactory.getInstance(this))
            .build()
        WorkManager.initialize(this, configuration)
    }
}
```

```kotlin
// app/src/main/java/com/aistudio/clinicsystem/di/AppModule.kt

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        tokenManager: TokenManager,
        tokenAuthenticator: TokenAuthenticator
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenManager))
            .authenticator(tokenAuthenticator)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BODY
                        }
                    )
                }
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideMobileApiService(retrofit: Retrofit): MobileApiService =
        retrofit.create(MobileApiService::class.java)

    @Provides
    @Singleton
    fun provideClinicDatabase(@ApplicationContext context: Context): ClinicDatabase =
        Room.databaseBuilder(context, ClinicDatabase::class.java, "clinic.db")
            .addMigrations(MIGRATION_4_5)
            .openHelperFactory(SQLCipherOpenHelperFactory())
            .build()

    @Provides
    fun provideAppointmentDao(db: ClinicDatabase) = db.appointmentDao()

    @Provides
    fun provideQueueDao(db: ClinicDatabase) = db.queueDao()

    @Provides
    @Singleton
    fun provideTokenManager(@ApplicationContext context: Context): TokenManager =
        TokenManager(context)

    @Provides
    @Singleton
    fun provideSessionManager(@ApplicationContext context: Context): SessionManager =
        SessionManager(context)
}
```

---

### 13.4 — `NetworkBoundResource` использование (E5.3)

```kotlin
// app/src/main/java/com/aistudio/clinicsystem/data/repository/ClinicRepositoryImpl.kt

class ClinicRepositoryImpl @Inject constructor(
    private val mobileApiService: MobileApiService,
    private val appointmentDao: AppointmentDao,
    private val queueDao: QueueDao,
    private val pendingSyncDao: PendingSyncDao,
    private val networkMonitor: NetworkMonitor,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ClinicRepository {

    override fun getAppointments(): Flow<Resource<List<Appointment>>> =
        networkBoundResource(
            query = { appointmentDao.observeAll() },
            fetch = { mobileApiService.getAppointmentsUpcoming() },
            saveFetchResult = { dto ->
                appointmentDao.upsertAll(dto.appointments.map { it.toEntity() })
            },
            shouldFetch = { cached ->
                cached.isEmpty() || networkMonitor.isOnline()
            },
            mapToDomain = { entities ->
                entities.map { it.toDomain() }
            }
        )

    override suspend fun bookAppointment(request: BookAppointmentRequest): Result<String> {
        val clientRequestId = UUID.randomUUID().toString()
        val pendingEntity = PendingSyncEntity(
            clientRequestId = clientRequestId,
            operation = "book_appointment",
            payload = MoshiUtils.toJson(request),
            createdAt = System.currentTimeMillis(),
            status = PendingSyncStatus.PENDING
        )
        pendingSyncDao.insert(pendingEntity)

        return withContext(ioDispatcher) {
            if (networkMonitor.isOnline()) {
                try {
                    val response = mobileApiService.bookAppointment(request)
                    pendingSyncDao.markSucceeded(clientRequestId)
                    appointmentDao.upsert(response.appointment.toEntity())
                    Result.success(response.appointment.id)
                } catch (e: Exception) {
                    pendingSyncDao.markPending(clientRequestId)  // retry later
                    Result.failure(e)
                }
            } else {
                // Осталось в pending, SyncWorker подхватит при появлении сети
                Result.success(clientRequestId)  // временный ID
            }
        }
    }
}
```

---

### 13.5 — `TokenAuthenticator` с auto-refresh (E3.2)

```kotlin
// app/src/main/java/com/aistudio/clinicsystem/data/api/TokenAuthenticator.kt

class TokenAuthenticator @Inject constructor(
    private val mobileApiService: MobileApiService,
    private val tokenManager: TokenManager,
    private val sessionManager: SessionManager
) : Authenticator {

    private val refreshMutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Защита от бесконечного ретрая
        if (responseCount(response) >= 2) return null

        val currentRefreshToken = tokenManager.refreshToken
            ?: run {
                // Нет refresh-токена → logout
                sessionManager.clear()
                return null
            }

        val newTokens = runBlocking {
            refreshMutex.withLock {
                // Двойная проверка: возможно, другой поток уже обновил токен
                val currentAccessToken = tokenManager.accessToken
                val requestToken = response.request.header("Authorization")
                    ?.removePrefix("Bearer ")

                if (currentAccessToken != null && currentAccessToken != requestToken) {
                    // Токен уже обновлён другим потоком — ретрай с ним
                    return@withLock TokenPair(currentAccessToken, tokenManager.refreshToken!!)
                }

                try {
                    val refreshResponse = mobileApiService.refreshToken(
                        RefreshRequest(currentRefreshToken)
                    )
                    tokenManager.saveTokens(
                        refreshResponse.access_token,
                        refreshResponse.refresh_token
                    )
                    TokenPair(refreshResponse.access_token, refreshResponse.refresh_token)
                } catch (e: Exception) {
                    // Refresh упал — logout
                    sessionManager.clear()
                    null
                }
            }
        } ?: return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer ${newTokens.accessToken}")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private data class TokenPair(val accessToken: String, val refreshToken: String)
}
```

---

### 13.6 — 2FA flow в `AuthViewModel` (E3.4)

```kotlin
// app/src/main/java/com/aistudio/clinicsystem/ui/auth/AuthViewModel.kt

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val verify2FAUseCase: Verify2FAUseCase,
    private val request2FACodeUseCase: Request2FACodeUseCase,
    private val recovery2FAUseCase: Recovery2FAUseCase
) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    fun login(username: String, password: String) {
        _loginState.value = LoginState.Loading
        viewModelScope.launch {
            loginUseCase(username, password, getDeviceFingerprint())
                .onSuccess { outcome ->
                    when (outcome) {
                        is LoginOutcome.Success -> {
                            _loginState.value = LoginState.Success(outcome.user)
                        }
                        is LoginOutcome.TwoFactorRequired -> {
                            _loginState.value = LoginState.TwoFactorRequired(
                                challengeToken = outcome.challengeToken
                            )
                        }
                    }
                }
                .onFailure { error ->
                    _loginState.value = LoginState.Error(
                        when (error) {
                            is AuthError.InvalidCredentials -> "Неверный логин или пароль"
                            is AuthError.NetworkError -> "Ошибка сети: ${error.cause.message}"
                            else -> "Неизвестная ошибка"
                        }
                    )
                }
        }
    }

    fun verify2FA(challengeToken: String, code: String, trustDevice: Boolean) {
        _loginState.value = LoginState.Loading
        viewModelScope.launch {
            verify2FAUseCase(challengeToken, code, trustDevice)
                .onSuccess { user ->
                    _loginState.value = LoginState.Success(user)
                }
                .onFailure { error ->
                    _loginState.value = LoginState.TwoFactorError(
                        message = "Неверный код",
                        attemptsRemaining = (error as? TwoFAError)?.attemptsRemaining
                    )
                }
        }
    }

    fun requestRecoveryCode(challengeToken: String) {
        viewModelScope.launch {
            request2FACodeUseCase(challengeToken)
            _loginState.value = (loginState.value as? LoginState.TwoFactorRequired)
                ?.copy(recoveryRequested = true)
        }
    }

    fun useRecoveryCode(challengeToken: String, recoveryCode: String) {
        viewModelScope.launch {
            recovery2FAUseCase(challengeToken, recoveryCode)
                .onSuccess { user -> _loginState.value = LoginState.Success(user) }
                .onFailure { _loginState.value = LoginState.TwoFactorError("Неверный recovery-код", null) }
        }
    }
}

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class TwoFactorRequired(
        val challengeToken: String,
        val recoveryRequested: Boolean = false
    ) : LoginState()
    data class TwoFactorError(val message: String, val attemptsRemaining: Int?) : LoginState()
    data class Error(val message: String) : LoginState()
    data class Success(val user: User) : LoginState()
}
```

---

### 13.7 — Room миграция 4→5 с тестом (E4.3, E4.4)

```kotlin
// app/src/main/java/com/aistudio/clinicsystem/data/db/Migrations.kt

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Пример: добавляем индекс на appointment_date для оптимизации
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_appointments_date ON appointments(appointment_date)"
        )

        // Пример: добавляем колонку sync_priority в pending_sync
        database.execSQL(
            "ALTER TABLE pending_sync ADD COLUMN sync_priority INTEGER NOT NULL DEFAULT 0"
        )

        // Пример: переименование колонки (SQLite не поддерживает напрямую)
        // 1. Создать новую таблицу
        database.execSQL("""
            CREATE TABLE notifications_new (
                id TEXT NOT NULL PRIMARY KEY,
                user_id TEXT NOT NULL,
                title TEXT NOT NULL,
                body TEXT,
                created_at INTEGER NOT NULL,
                read_at INTEGER
            )
        """.trimIndent())
        // 2. Скопировать данные
        database.execSQL("""
            INSERT INTO notifications_new (id, user_id, title, body, created_at, read_at)
            SELECT id, user_id, title, body, created_at, read_at FROM notifications
        """.trimIndent())
        // 3. Удалить старую
        database.execSQL("DROP TABLE notifications")
        // 4. Переименовать новую
        database.execSQL("ALTER TABLE notifications_new RENAME TO notifications")
    }
}
```

```kotlin
// app/src/test/java/com/aistudio/clinicsystem/data/db/MigrationTest.kt

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        databaseClass = ClinicDatabase::class.java
    )

    @Test
    @Throws(IOException::class)
    fun migrate4To5_preservesAppointments() {
        // 1. Создаём БД версии 4
        val dbV4 = helper.createDatabase("test_db", 4)
        dbV4.execSQL(
            "INSERT INTO appointments (id, patient_id, doctor_id, date, status) " +
            "VALUES ('apt-1', 'pat-1', 'doc-1', 1700000000, 'booked')"
        )
        dbV4.close()

        // 2. Запускаем миграцию
        val dbV5 = helper.runMigrationsAndValidate(
            "test_db", 5, true, MIGRATION_4_5
        )

        // 3. Проверяем данные на месте
        val cursor = dbV5.query("SELECT id, status FROM appointments WHERE id = 'apt-1'")
        assertTrue(cursor.moveToFirst())
        assertEquals("apt-1", cursor.getString(0))
        assertEquals("booked", cursor.getString(1))
        cursor.close()

        dbV5.close()
    }
}
```

---

### 13.8 — `SecureScreen` с `FLAG_SECURE` (E1.5)

```kotlin
// app/src/main/java/com/aistudio/clinicsystem/ui/components/SecureScreen.kt

@Composable
fun SecureScreen(content: @Composable () -> Unit) {
    val view = LocalView.current
    val context = LocalContext.current

    DisposableEffect(view) {
        val window = (context as? Activity)?.window
            ?: (context as? ContextWrapper)?.baseContext?.let { it as? Activity }?.window

        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    content()
}

// Использование:
@Composable
fun PatientScreen() {
    SecureScreen {
        PatientScreenContent()
    }
}
```

---

### 13.9 — GitHub Actions `android.yml` (E7.1)

```yaml
# .github/workflows/android.yml
name: Android CI

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

concurrency:
  group: android-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    timeout-minutes: 30

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Setup JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3
        with:
          gradle-version: 8.9
          cache-disabled: false

      - name: Cache Gradle packages
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: gradle-${{ runner.os }}-${{ hashFiles('**/*.gradle*', 'gradle/libs.versions.toml') }}
          restore-keys: gradle-${{ runner.os }}-

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Run ktlint
        run: ./gradlew ktlintCheck

      - name: Run detekt
        run: ./gradlew detekt

      - name: Assemble debug APK
        run: ./gradlew assembleDebug

      - name: Run unit tests
        run: ./gradlew testDebugUnitTest

      - name: Run lint
        run: ./gradlew lintDebug

      - name: Generate coverage report
        run: ./gradlew jacocoTestReport

      - name: Upload APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: apk-debug
          path: app/build/outputs/apk/debug/*.apk
          retention-days: 14

      - name: Upload coverage to Codecov
        uses: codecov/codecov-action@v4
        with:
          file: app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml
          fail_ci_if_error: false

      - name: Upload test results on failure
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: test-results
          path: |
            app/build/reports/tests/
            app/build/reports/detekt/
            app/build/reports/lint-results-*.html
```

---

### 13.10 — `strings.xml` пример (E8.1)

```xml
<!-- app/src/main/res/values/strings.xml -->
<resources>
    <string name="app_name">Clinic System</string>

    <!-- Auth -->
    <string name="auth_login_title">Вход в систему</string>
    <string name="auth_username">Логин</string>
    <string name="auth_password">Пароль</string>
    <string name="auth_login_button">Войти</string>
    <string name="auth_biometric_prompt">Авторизуйтесь для входа</string>
    <string name="auth_error_invalid_credentials">Неверный логин или пароль</string>
    <string name="auth_error_network">Ошибка сети. Проверьте подключение</string>
    <string name="auth_2fa_title">Двухфакторная аутентификация</string>
    <string name="auth_2fa_code_hint">6-значный код</string>
    <string name="auth_2fa_verify">Подтвердить</string>
    <string name="auth_2fa_use_recovery">Использовать recovery-код</string>
    <string name="auth_2fa_trust_device">Доверять этому устройству</string>

    <!-- Appointments -->
    <string name="booking_title">Запись на приём</string>
    <string name="booking_select_doctor">Выберите врача</string>
    <string name="booking_select_date">Выберите дату</string>
    <string name="booking_select_time">Выберите время</string>
    <string name="booking_confirm">Подтвердить запись</string>
    <string name="booking_cancel">Отменить запись</string>

    <!-- Queue -->
    <string name="queue_position">Ваша позиция: %1$d</string>
    <string name="queue_wait_time">Ориентировочное ожидание: %1$s</string>
    <string name="queue_called">Вас вызывают!</string>

    <!-- Errors -->
    <string name="error_generic">Произошла ошибка. Попробуйте позже</string>
    <string name="error_offline">Нет подключения к интернету</string>
</resources>
```

---

## 14. Risk register

| ID | Риск | Вер. | Влияние | Mitigation | Владелец |
|---|---|---|---|---|---|
| R1 | Изменение API бэкенда (новые endpoints, breaking changes в `/mobile/*`) во время рефакторинга | С | С | Договориться с бэкенд-разработчиком о заморозке `/mobile/*` контракта на 4 недели. Использовать `openapi.json` из репо бэкенда как SSOT, генерировать DTOs через `openapi-generator`. | Dev |
| R2 | Потеря данных пользователя при Room миграции в production | Н | Крит | Тесты на миграции (E4.4). Перед релизом M5 — провести ручной тест миграции на тестовом устройстве с реальными данными из beta-сборки. | Dev |
| R3 | Regression в offline-sync queue после рефакторинга репозиториев | С | Крит | Unit-тесты на `SyncWorker` (E6.4) + `ClinicRepository.bookAppointment` (E6.2). Integration-тест: airplane mode → book → enable network → verify sync. | Dev |
| R4 | Утечка PHI (медицинских данных) через логи в release | С | Крит | E1.4 (gate logging за DEBUG) + ручная проверка Logcat в release-сборке. Включить `android:allowBackup="false"` в манифесте. | Dev |
| R5 | Отказ Google Play из-за отсутствия privacy policy / content rating | С | С | E9.4 — подготовить privacy policy (можно через generator типа termsfeed.com), заполнить content rating questionnaire заранее. | Dev |
| R6 | Изменение 2FA flow на бэкенде во время реализации E3.4 | Н | С | Согласовать с бэкендом конкретные endpoints (`/2fa/send-code`, `/2fa/verify-code`) до старта E3.4. Тестировать на staging-бэкенде. | Dev |
| R7 | Hilt + WorkManager интеграция нестабильна (известные баги) | Н | С | Использовать `androidx.hilt:hilt-work` + `HiltWorkerFactory`. Проверить на минимальном примере до введения во все `Worker`-ы. | Dev |
| R8 | Compose BOM обновление ломает UI (deprecated APIs) | С | Н | Обновлять мажорные версии только между milestone-ами. На каждом обновлении — прогон Roborazzi screenshot-тестов. | Dev |
| R9 | SQLCipher 4.5.4 + Room 2.7 несовместимость на конкретных Android-версиях | Н | Крит | Прогон на эмуляторах Android 10/12/14 + физическое устройство Samsung/Xiaomi. Backup-план: `ReactiveSQL` + ручное шифрование. | Dev |
| R10 | R8 full mode удаляет нужный код (Moshi adapters, Hilt factories) | С | С | E2.4 (правильные keep-rules) + E9.5 (тестовый release-build перед каждым релизом). Smoke-тест: login → book → sync → logout на release-сборке. | Dev |
| R11 | Backend `expires_in: 3600` hardcoded → мобильный клиент делает лишние refresh-запросы (168× чаще) | В | Н | E3.2 — игнорировать `expires_in`, использовать `exp` claim из JWT для планирования refresh. Завести issue на бэкенд. | Dev |
| R12 | Backend `/mobile/mobile/auth/login` (двойной префикс) не исправлен → мобильный клиент использует несуществующий путь | С | Крит | E3.1 — проверить реальный путь на staging-сервере перед миграцией. Завести issue на бэкенд (если ещё не исправлено). | Dev |
| R13 | Демп-данные в БД эмулятора мешают тестам миграции | С | Н | Очистка данных устройства между тестовыми циклами. Использовать отдельный `test_db` в `MigrationTestHelper`. | Dev |
| R14 | Single developer → bus factor = 1, болезнь/отпуск срывает milestone-ы | В | С | Документировать архитектурные решения в ADR (Architecture Decision Records) в `docs/adr/`. Зафиксировать roadmap в Git. | Dev |
| R15 | Backend enforce 2FA per-user в production → мобильный клиент блокирует пользователей | С | Крит | E3.4 завершить до любого production-релиза. На staging — проверить с пользователем, у которого 2FA включена. | Dev |

> **Легенда:** Вероятность (В/С/Н — Высокая/Средняя/Низкая), Влияние (Крит/С/Н — Критическое/Среднее/Низкое)

---

## 15. Rollback-стратегии по milestone-ам

### 15.1. M0 (Security + Build)

**Триггер rollback:** после влития security-фиксов приложение не собирается или ломается критичная функциональность.

**Rollback:**
1. `git revert <squash-commit-hash>` — откатывает весь PR целиком
2. Создать `hotfix/rollback-m0` branch с ручным cherry-pick только рабочих security-фиксов (например, оставить E1.4 про logging, откатить E1.5 про SecureScreen если он ломает UI)
3. Если demo-bypass уже удалён, но новый auth-флоу не работает — временный hotfix: вернуть demo-bypass в `BuildConfig.DEBUG`-гейте (только debug-сборки), не в production

**Prevention:** M0 — единственный milestone, где допустимо вливать несколько маленьких PR вместо одного большого. Каждый из 8 задач E1.* — отдельный PR с отдельным smoke-тестом.

---

### 15.2. M1 (Backend Integration + Room)

**Триггер rollback:** новый `/mobile/*` контракт не работает с реальным бэкендом, или миграция Room стирает данные.

**Rollback:**
1. **Backend контракт:** сохранить старый `ApiService.kt` в branch `feature/legacy-api` до завершения M2. В emergency — revert на `main` и переключиться обратно.
2. **Room миграции:** если миграция 4→5 в production теряет данные — emergency hotfix:
   ```kotlin
   Room.databaseBuilder(...)
       .fallbackToDestructiveMigrationOnDowngrade()  // только downgrade
       .addMigrations(MIGRATION_4_5)
       .build()
   ```
   + сообщение пользователю "Обновление прошло с ошибкой, пожалуйста, перезайдите в аккаунт". Никогда не возвращать полный `fallbackToDestructiveMigration()`.
3. **2FA flow:** если `/2fa/verify-code` нестабилен на бэкенде — добавить feature flag `Features.ENABLE_2FA_UI` (через `FirebaseRemoteConfig` или `BuildConfig`), отключить UI до исправления бэкенда. Пользователи с включённой 2FA получат понятное сообщение "Вход временно недоступен".

---

### 15.3. M2 (Architectural Refactoring)

**Триггер rollback:** после разбития `PatientScreen`/`StaffScreen` UI работает хуже, чем раньше, или Hilt-инъекция падает на конкретных устройствах.

**Rollback:**
1. Сохранить `PatientScreen.kt` и `StaffScreen.kt` (старые версии) в branch `feature/legacy-screens` до завершения M3
2. Если новая разбивка нестабильна — revert merge commit, вернуть старые экраны
3. Hilt rollback сложнее: если Hilt ломает приложение на конкретных устройствах (известны баги с AGP 9.x), temporary fallback на ручной DI через `object Singleton { ... }`. Это шаг назад, но лучше, чем неработающее приложение.

**Prevention:** M2 — самый рискованный milestone. Рекомендуется:
- Сделать Hilt-интеграцию в отдельном feature branch `feature/hilt` и собрать debug-APK для ручного тестирования на 3 устройствах до влития
- Разбивку экранов делать по одной фиче за раз (сначала `ProfileSection`, протестировать, потом `AppointmentsSection` и т.д.)

---

### 15.4. M3 (Tests + CI/CD)

**Триггер rollback:** CI начинает падать на всех PR из-за flaky-тестов или detekt-нарушений в legacy-коде.

**Rollback:**
1. **Flaky-тесты:** пометить через `@Ignore("flaky: investigate in #issue")` с трекингом в issue. Не отключать весь test-suite.
2. **Detekt:** использовать `detektBaseline.txt` для исключения существующих нарушений. Новые нарушения — блокируют, старые — нет.
3. **CI workflow:** если GitHub Actions нестабилен (rate limits, runner-ы) — temporary disable обязательные checks в branch protection, но **не более 1 недели** без remediation.

---

### 15.5. M4 (Localization + Release Prep)

**Триггер rollback:** после локализации UI ломается (длинные английские строки), или release-keystore утерян.

**Rollback:**
1. **i18n UI bug:** revert конкретного файла `strings.xml`, вернуть хардкод. Локализация — P2, не должна блокировать релиз.
2. **Keystore loss:** критично. Если утерян — невозможно выпустить обновление (Play Console требует тот же keystore). Mitigation: backup keystore в 2 местах (например, 1Password + offline USB). Если уже утерян — register new app in Play Console (потеря всех установок).

---

### 15.6. M5 (Production Release)

**Триггер rollback:** critical bug в production после rollout.

**Rollback:**
1. **Phased rollout:** использовать Play Console staged rollout (10% → 50% → 100%), на каждом этапе мониторить crash-rate (Firebase Crashlytics). Если crash-rate > 1% — приостановить rollout кнопкой "Halt rollout".
2. **Emergency update:** выпустить hotfix-версию `1.0.1` с исправлением. Не пытаться откатить пользователей на старую версию — Play Console не поддерживает downgrade.
3. **Kill switch:** для критичных фич (новый `/mobile/*` контракт, 2FA) — использовать `FirebaseRemoteConfig` boolean flags. В emergency — отключить фичу без обновления приложения.

---

## 16. Финальный чек-лист готовности к релизу (M5)

> Заполнять по мере завершения задач. Каждый пункт соответствует эпику/задаче.

### Security (E1)

- [ ] E1.1 — Demo-bypass в `AuthRepository.login` удалён, есть unit-тест
- [ ] E1.2 — Чипы быстрого входа из `AuthScreen` удалены
- [ ] E1.3 — `DemoSandboxToggleBar` из `MainActivity` удалён
- [ ] E1.4 — `HttpLoggingInterceptor` гейтован за `BuildConfig.DEBUG`
- [ ] E1.5 — `SecureScreen` реализует `FLAG_SECURE`, применён к PHI-экранам
- [ ] E1.6 — `TokenManager` fail-closed, не падает на plain SharedPreferences
- [ ] E1.7 — Фейковый Firebase API-key удалён
- [ ] E1.8 — Ручной security audit pass, `git grep` чистый

### Build (E2)

- [ ] E2.1 — Gradle wrapper добавлен, `./gradlew assembleDebug` работает
- [ ] E2.2 — Namespace переименован в `com.aistudio.clinicsystem`
- [ ] E2.3 — `rootProject.name` = `ClinicSystemMobile`
- [ ] E2.4 — `proguard-rules.pro` заполнен keep-rules
- [ ] E2.5 — Compose BOM и зависимости обновлены
- [ ] E2.6 — `network_security_config` разделён на debug/release
- [ ] E2.7 — `BASE_URL` вынесен в `BuildConfig`

### Backend Integration (E3)

- [ ] E3.1 — Мобильный клиент использует `/mobile/*` контракт
- [ ] E3.2 — Token refresh через `/authentication/refresh` реализован
- [ ] E3.3 — Logout с инвалидацией сессии на сервере
- [ ] E3.4 — 2FA challenge flow реализован (verify-code + recovery + trusted device)
- [ ] E3.5 — Роли синхронизированы с бэкендом (9 ролей)
- [ ] E3.6 — WebSocket URL `/ws/queue` исправлен, есть unit-тесты
- [ ] E3.7 — `AuthInterceptor.onUnauthorized` заменён на `TokenAuthenticator`

### Room Migrations (E4)

- [ ] E4.1 — `exportSchema = true`, schema directory настроена
- [ ] E4.2 — `fallbackToDestructiveMigration` удалён
- [ ] E4.3 — Миграции 4→5 описаны (или заглушка для будущих)
- [ ] E4.4 — Тесты на миграции через `MigrationTestHelper`

### Architecture (E5)

- [ ] E5.1 — Hilt DI внедрён, ручные singletons удалены
- [ ] E5.2 — `domain/` слой с UseCase-ами создан
- [ ] E5.3 — `NetworkBoundResource` используется во всех репозиториях
- [ ] E5.4 — `PatientScreen.kt` разбит на ≤ 400 LOC composables
- [ ] E5.5 — `StaffScreen.kt` разбит по ролям (Doctor/Registrar/Lab/Cashier/Admin)
- [ ] E5.6 — `SyncConsoleView` скрыт за `BuildConfig.DEBUG`
- [ ] E5.7 — `AnalyticsManager` либо реализован с Firebase Analytics, либо удалён

### Tests (E6)

- [ ] E6.1 — Unit-тесты на `AuthRepository` (≥ 9 сценариев)
- [ ] E6.2 — Unit-тесты на `ClinicRepository` (≥ 7 сценариев)
- [ ] E6.3 — Unit-тесты на `ClinicWebSocketClient.handleSocketMessage` (5 типов сообщений)
- [ ] E6.4 — Unit-тесты на `SyncWorker` (5 сценариев)
- [ ] E6.5 — Unit-тесты на ViewModels
- [ ] E6.6 — Roborazzi screenshot-тесты на ключевые экраны
- [ ] E6.7 — Jacoco coverage ≥ 60% на `data/` + `domain/`

### CI/CD (E7)

- [ ] E7.1 — GitHub Actions workflow `android.yml` работает
- [ ] E7.2 — `ktlint` + `detekt` настроены и проходят
- [ ] E7.3 — Branch protection на `main` настроена
- [ ] E7.4 — Release workflow на теги `v*.*.*` настроен

### Localization (E8)

- [ ] E8.1 — Все захардкоженные RU-строки в `strings.xml`
- [ ] E8.2 — `values-en/strings.xml` создан
- [ ] E8.3 — Pseudo-locale включён, проверен
- [ ] E8.4 — Plurals + locale-aware date formatting

### Release (E9)

- [ ] E9.1 — Release keystore сгенерирован, в Git НЕ коммитится, backup в 2 местах
- [ ] E9.2 — `signingConfigs` в `build.gradle.kts` настроен
- [ ] E9.3 — SemVer versioning настроен
- [ ] E9.4 — Play Console: app создан, store listing заполнен, Internal Testing настроен
- [ ] E9.5 — R8 full mode + resource shrinking включены, release-сборка работает
- [ ] E9.6 — README обновлён, LICENSE и CONTRIBUTING добавлены

### Final Smoke Test (M5)

- [ ] Login (без 2FA) — работает
- [ ] Login (с 2FA) — работает, recovery-код работает, trusted device работает
- [ ] Token refresh — работает при истечении access token
- [ ] Logout — инвалидирует сессию на сервере
- [ ] Book appointment (online) — работает, появляется в списке
- [ ] Book appointment (offline) — попадает в pending, sync при появлении сети
- [ ] Queue position — обновляется в реальном времени через WebSocket
- [ ] Medical records — отображаются, создание staff-пользователем работает
- [ ] Notifications — приходят, mark-as-read работает
- [ ] App работает на Android 10 (API 29)
- [ ] App работает на Android 13 (API 33)
- [ ] App работает на Android 15 (API 35)
- [ ] Crashlytics не репортит crashes в течение 24h на Internal Testing
- [ ] Privacy policy URL доступен
- [ ] Backup `android:allowBackup="false"` в манифесте

---

**Подпись автора:** ___________________  **Дата готовности:** ___________

> После закрытия всех пунктов этого чек-листа — приложение готово к phased rollout в Google Play (10% → 50% → 100% в течение 2 недель с мониторингом crash-rate).
