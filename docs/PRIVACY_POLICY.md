# Privacy Policy — Clinic Management System Mobile App

**Last updated:** 2026-06-28
**Effective date:** 2026-06-28
**Languages:** Русский (ниже) / English (below) / O'zbekcha (pastda)

---

## 🇷🇺 Русский

### 1. Какие данные мы собираем

Приложение «Clinic Management System» (далее — «Приложение») собирает и
обрабатывает следующие категории персональных данных о пациентах и
сотрудниках клиники:

**Данные учётной записи:**
- Имя пользователя и пароль (пароль хранится в виде bcrypt-хеша на сервере)
- Номер телефона (используется как уникальный идентификатор пациента)
- Роль (PATIENT или STAFF)
- Дата рождения (опционально, для медицинских целей)

**Медицинские данные (PHI — Protected Health Information):**
- Записи на приём (дата, время, врач, специальность, причина визита, статус)
- Медицинские записи (диагноз, назначение лекарств, рекомендации, дата визита)
- Лабораторные результаты (название теста, результат, единицы, референсный диапазон)
- Статус очереди в клинике

**Биометрические данные (опционально):**
- Если пользователь добровольно включил биометрический вход —
  отпечаток пальца или Face ID используется для разблокировки ключа
  шифрования в Android Keystore. Сам отпечаток/лицо НЕ покидает
  устройство и НЕ хранится в Приложении.

**Данные об использовании (опционально, только в DEBUG-сборках):**
- Журнал синхронизации (время операций, статус, ошибки)
- События аналитики (название экрана, действие) — только в DEBUG-сборках

### 2. Как мы используем данные

- **Для аутентификации** — проверка учётных данных при входе
- **Для записи на приём** — создание, изменение, отмена записей
- **Для предоставления медицинских услуг** — врач создаёт медицинские
  записи на основе приёма
- **Для уведомлений** — push-уведомления об изменении статуса записи,
  новых медицинских записях (содержимое уведомления НЕ содержит PHI
  на экране блокировки — используется `VISIBILITY_PRIVATE`)
- **Для синхронизации** — локальная база данных синхронизируется с
  сервером клиники для обеспечения offline-first работы

### 3. Где хранятся данные

**На устройстве:**
- Локальная база данных Room, зашифрованная SQLCipher (AES-256)
- Ключ шифрования БД — в Android Keystore (hardware-backed, StrongBox
  где доступно)
- JWT токены — в EncryptedSharedPreferences (AES-256-GCM)
- Биометрический ключ — в Android Keystore, требует биометрической
  аутентификации для использования

**На сервере:**
- Бэкенд клиники (FastAPI) — см. политику конфиденциальности сервера
  на https://clinic.tld/privacy

**НЕ передаём третьим лицам:**
- Firebase Firestore — удалён в Stage 2.4 (мёртвый код)
- Google Analytics — не используется
- Sentry / Crashlytics — не используется (планируется в Stage 9,
  с явным согласием пользователя)

### 4. Передача данных

- **HTTPS / WSS only** — весь сетевой трафик шифруется TLS
- **Certificate Pinning** — в release-сборках пины SHA-256 публичного
  ключа сервера проверяются на каждом соединении (защита от MITM через
  скомпрометированный CA)
- **Idempotency-Key** — на POST/PUT/PATCH запросах для предотвращения
  дубликатов (не содержит PHI)

### 5. Безопасность

- **SQLCipher AES-256** — локальная БД зашифрована
- **Android Keystore** — ключи хранятся в hardware-backed keystore
- **FLAG_SECURE** — скриншоты и запись экрана заблокированы на всех
  экранах с PHI
- **Fail-closed** — если EncryptedSharedPreferences недоступен
  (повреждённый keystore), Приложение отказывается работать, а не
  падает на plaintext хранилище
- **Play Integrity API** — проверка целостности устройства при входе
  и при создании медицинских записей (rooted/modified устройства
  блокируются)

### 6. Срок хранения данных

- **На устройстве** — до явного выхода пользователя из учётной записи
  (logout очищает локальный кэш PHI)
- **На сервере** — согласно политике хранения данных клиники
  (см. https://clinic.tld/retention)

### 7. Ваши права

- **Доступ** — вы можете запросить копию всех ваших данных через
  поддержку клиники
- **Удаление** — вы можете удалить учётную запись через настройки
  в приложении или через поддержку клиники
- **Экспорт** — вы можете выгрузить свои медицинские записи в формате
  PDF/TXT (функция в личном кабинете пациента)
- **Отзыв согласия** — вы можете отключить биометрический вход,
  уведомления или синхронизацию в настройках

### 8. Детские данные

Приложение не предназначено для лиц младше 16 лет. Мы сознательно не
собираем данные детей. Если вы считаете, что ребёнок предоставил нам
данные, свяжитесь с поддержкой для удаления.

### 9. Изменения политики

Мы можем обновлять эту политику. Дата обновления указана вверху.
Существенные изменения будут анонсированы в приложении.

### 10. Контакты

- Email: privacy@clinic.tld
- Телефон: +7 (XXX) XXX-XX-XX
- Адрес: [адрес клиники]

---

## 🇬🇧 English

This app collects account credentials, phone number, medical records
(appointments, diagnoses, prescriptions, lab results), and optionally
biometric data for login. All data is stored encrypted on-device
(SQLCipher AES-256, Android Keystore) and transmitted over HTTPS with
certificate pinning. We do not share data with third parties. See the
Russian section above for full details, or contact privacy@clinic.tld.

---

## 🇺🇿 O'zbekcha

Bu ilova hisob ma'lumotlari, telefon raqami, tibbiy yozuvlar
(qabullar, diagnozlar, retseptlar, laboratoriya natijalari) va
ixtiyoriy ravishda kirish uchun biometrik ma'lumotlarni yig'adi.
Barcha ma'lumotlar qurilmada shifrlangan holda saqlanadi (SQLCipher
AES-256, Android Keystore) va HTTPS orqali sertifikat mahkamlash bilan
uzatiladi. Biz ma'lumotlarni uchinchi shaxslarga bermaymiz. To'liq
tafsilotlar uchun yuqoridagi ruscha bo'limga qarang yoki
privacy@clinic.tld bilan bog'laning.
