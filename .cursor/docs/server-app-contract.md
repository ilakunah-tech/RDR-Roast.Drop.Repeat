# Договорённость: сайт (test-server-qqplus) ↔ приложение RDR

По аналогии с Cropster/Cropster Ri и Artisan/Artisan Plus: что ожидает сайт от приложения и что приложение должно принимать от сайта.

---

## 1. Что сайт ожидает от приложения (RDR)

Сервер (test-server-qqplus) рассчитан на два типа клиентов: **веб-интерфейс** и **десктопное приложение** (Artisan Plus–совместимый протокол). RDR пока **ничего не отправляет** на сервер (нет загрузки обжарок); ниже — что сервер готов принять от десктопного клиента.

### 1.1. Аутентификация

| Действие | Метод | Путь | Тело / заголовки |
|----------|--------|------|-------------------|
| Вход | POST | `/api/v1/auth/login` | `email`, `password`, опционально `remember`. Лимит: 5 запросов/мин с IP. |
| Обновление токена | POST | `/api/v1/auth/refresh` | `refresh_token` в теле. Без заголовка `Authorization`. |
| Текущий пользователь | GET | `/api/v1/auth/me` | Заголовок: `Authorization: Bearer <access_token>`. |

Ответ логина: `data`: `token`, `refresh_token`, `user_id`, `email`, `username`, `role`, `is_super_admin`, `can_manage_advisor`.

Для всех защищённых запросов: **`Authorization: Bearer <access_token>`**, **`Content-Type: application/json`** при отправке JSON.

### 1.2. Загрузка / синхронизация обжарки (Artisan Plus)

Если RDR в будущем будет загружать обжарки (как Artisan):

| Действие | Метод | Путь | Особенности |
|----------|--------|------|-------------|
| Создать/обновить обжарку | POST | `/api/v1/aroast` или `/apiv1/aroast` | Bearer обязателен. Опционально: `Content-Encoding: gzip`, заголовок `Idempotency-Key` (повторные запросы в течение 24 ч возвращают кэш). При конфликте по `modified_at` — **409 Conflict**. |

Тело (JSON):

- Обязательно: **`roast_id`** или **`id`** (UUID или 32 hex), **`date`** (ISO8601) для новых обжарок.
- Мета: `location`, `coffee`, `blend`, `amount`, `end_weight`, `batch_number`, `label`, `notes`.
- События: `FCe_temp`, `drop_time`, `modified_at` и т.д.
- Телеметрия: либо на верхнем уровне, либо в объекте **`telemetry`**: `timex`, `temp1`, `temp2`, `extra_temp1`, `extra_temp2`, `air`, `drum`, `gas`, `fan`, `heater`, `timeindex` и др.
- Подавление: опущенные поля восстанавливаются значениями по умолчанию (0, `""`, `[]`).

Ответ при успехе (200): обёртка Artisan: `success`, `data` (обжарка), `result`, `ol`, `pu`, `notifications` (`unqualified`, `machines`).

### 1.3. Получение одной обжарки с сервера (sync/pull)

| Метод | Путь | Запрос | Ответы |
|--------|------|--------|--------|
| GET | `/api/v1/aroast/{roast_id}` | Опционально `?modified_at=<ms>` (integer, миллисекунды). | 200 — данные; 204 — у клиента уже актуальная версия; 404 — не найдено. |

Формат ответа 200: та же обёртка `data`, `result`, `success`, `ol`, `pu`, `notifications`.

### 1.4. Прочие эндпоинты, полезные десктопу

- **Здоровье:** GET `/api/v1/health` — без авторизации.
- **Дашборд одним запросом:** GET `/api/v1/dashboard/summary` — кофе, обжарки, расписание, бленды, задачи, машины.
- **Скачать установщик:** GET `/api/v1/downloads/desktop/windows` — файл установщика (Bearer).
- **Список обжарок (веб-формат):** `/api/v1/roasts` — list/get/create/update/delete, цели, импорт/экспорт.
- **Инвентарь, бленды, расписание, машины, задачи:** `/api/v1/inventory`, `/api/v1/blends`, `/api/v1/schedule`, `/api/v1/machines`, `/api/v1/production-tasks`.
- **Профили .alog:** `/api/v1/profiles/local` — list, upload (multipart), delete, download.

Все перечисленные эндпоинты требуют Bearer, кроме health. Префикс **`/apiv1`** (без слеша в конце) поддерживается для совместимости с Artisan desktop.

---

## 2. Что приложение (RDR) принимает от сайта

RDR уже использует сервер только как **источник эталонных профилей**: список эталонов → выбор одного → загрузка данных профиля для графика. Дополнительно сервер может слать уведомления по WebSocket.

### 2.1. Текущая интеграция RDR

- **Настройки:** `serverBaseUrl` (например `https://artqqplus.ru/api/v1`), опционально `serverToken` (Bearer).
- **Вызовы:** только GET через `ReferenceApi.kt`.

### 2.2. Список эталонов — GET `/roasts/references`

- **URL:** `{baseUrl}/roasts/references`
- **Опциональные query:** `coffee_id`, `blend_id`, `coffee_hr_id`, `blend_hr_id`, `machine`, `include_archived_ref`, `archived_only_ref`.
- **Авторизация:** Bearer, если указан токен.
- **Ответ:** JSON вида  
  `{ "data": { "items": [ ... ], "total": N } }`  
  Каждый элемент — объект из `RoastResponse` + обогащения (например `coffee_label`, `blend_spec`). RDR использует:
  - **`id`** — идентификатор обжарки (UUID строка).
  - **`label`** или **`reference_name`** — подпись для списка.
  - **`roasted_at`** — дата/время обжарки (опционально для отображения).

Если `data` или `data.items` отсутствуют, RDR считает список пустым.

### 2.3. Данные профиля эталона — GET `/roasts/{roast_id}/profile/data`

- **URL:** `{baseUrl}/roasts/{roast_id}/profile/data`
- **Авторизация:** Bearer по необходимости.
- **Ответ:** JSON профиля в формате Artisan (.alog-совместимый), обработанный `ensure_artisan_background_profile()`.

RDR парсит ответ в `ProfileStorage.profileFromServerJson(json)`. Ожидания:

- **Обязательно для непустого профиля:**
  - **`timex`** — массив чисел (время в секундах).
  - **`temp1`** — ET (environment temperature).
  - **`temp2`** — BT (bean temperature).
- **Опционально:**
  - **`timeindex`** — до 8 индексов в массиве: [0]=CHARGE, [1]=DRY END, [2]=FCs, [3]=FCe, [4]=SCs, [5]=SCe, [6]=DROP, [7]=COOL.
  - **`mode`** — `"C"` или `"F"` (градусы).

Сервер отдаёт **temp1 = ET, temp2 = BT** (соглашение Artisan). RDR внутри хранит **temp1 = BT, temp2 = ET** и при разборе маппит соответственно; события строятся по `timeindex` (Charge, DE, FC, DROP, при необходимости TP).

Дополнительные поля в ответе (например `computed`, `extratimex`, `specialevents`, `flavors`, `weight`, `beans`) не обязательны для RDR; парсер их не использует.

### 2.4. Уведомления от сервера (WebSocket)

Сервер может слать события в реальном времени через WebSocket (RDR пока **не подключается**).

- **URL:** `ws(s)://<host>/ws/notifications?token=<access_token>`
- **Авторизация:** query-параметр `token` = JWT access token. Без/с неверным токеном соединение закрывается (код 1008).
- **Ограничения:** до 5 соединений на пользователя; макс. размер входящего сообщения 1 KB; таймаут неактивности 5 мин. Клиент может слать `"ping"`, сервер отвечает `"pong"`.

Формат сообщения сервер → клиент (JSON):

```json
{
  "type": "notification",
  "event_type": "<string>",
  "payload": { ... },
  "timestamp": "<ISO8601 UTC>"
}
```

Сейчас реализован только тип **`production_task`** (напоминание о производственной задаче): в `payload` приходят `task_id`, `history_id`, `user_id`, `title`, `notification_text`, `task_type`, `machine_id`, `machine_name`, `triggered_at`, `trigger_reason`. Остальные push-события (новая обжарка, изменение расписания и т.д.) по WebSocket не отдаются.

### 2.5. Вебхуки

Сервер **не вызывает** URL клиента (вебхуков нет). Уведомления клиенту — только через WebSocket или через логику самого приложения (опрос API).

---

## 3. Краткая сводка

| Направление | Что происходит сейчас | Что возможно в будущем |
|-------------|------------------------|-------------------------|
| **Сайт → RDR** | RDR запрашивает список эталонов и данные одного профиля (GET references + GET profile/data). | Подписка на WebSocket для уведомлений (например production_task). |
| **RDR → Сайт** | Ничего (нет отправки обжарок). | Логин/refresh, загрузка обжарок (POST /aroast), синхронизация по GET /aroast/{id}, использование dashboard, downloads и др. |

Файлы для справки:

- Сервер: `backend/app/api/v1/endpoints/auth.py`, `roasts.py` (references, profile/data, aroast), `backend/app/ws/notifications.py`, `backend/app/schemas/roast.py`, `backend/app/services/file_service.py` (`ensure_artisan_background_profile`).
- RDR: `ReferenceApi.kt`, `ProfileStorage.kt` (`profileFromServerJson`), `AppSettings.kt`, `SettingsController.kt`.

Полный список API при DEBUG: Swagger `/docs`, ReDoc `/redoc` на сервере.
