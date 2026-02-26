# Формат .alog на сервере (test-server-qqplus)

Проверка: как сайт **принимает** и **отдаёт** данные в формате .alog.

---

## 1. Приём .alog (upload)

### Эндпоинты
- **POST /api/v1/profiles/local** — загрузка .alog с ПК (multipart, файл `.alog`).
- При сохранении обжарки с Artisan — тот же пайплайн через `build_artisan_payload_from_profile(parse_alog_bytes(content))`.

### Парсинг (`app/services/file_service.py`)

1. **`parse_alog_bytes(content: bytes)`**  
   Пишет байты во временный файл и вызывает `read_and_parse_alog(path)`.

2. **`read_and_parse_alog(path)`** поддерживает:
   - **ZIP** (первые 2 байта `PK`): ищет внутри файл `.json` / `.JSON` или первый файл, парсит как JSON или как Python dict.
   - **Обычный файл**: сначала `json.loads(content)`; при ошибке — **Python dict literal** через `_parse_python_dict(content)`.

3. **`_parse_python_dict(content)`**:
   - Заменяет `True`→`true`, `False`→`false`, `None`→`null`.
   - **Заменяет все одинарные кавычки на двойные** (`content.replace("'", '"')`), затем `json.loads`.
   - Ограничение: строки с апострофом внутри (например `"Artisan's"`) могут сломать парсинг, т.к. замена глобальная.

### Ожидаемая структура (как в Artisan .alog)
- **timex** — список float, время в секундах.
- **temp1** — ET (environment/exhaust).
- **temp2** — BT (bean temp).
- **timeindex** — список из 8 индексов: `[CHARGE, DRY, FCs, FCe, SCs, SCe, DROP, COOL]`.
- **mode** — `'C'` или `'F'`.
- Дополнительно: title, roastisodate, roasttime, weight, computed, specialevents, extratimex, extratemp1, extratemp2 и т.д.

После парсинга вызывается **`compute_computed_from_timeindex(profile)`** — достраивает `computed` (CHARGE_BT, DROP_time, фазы и т.д.) по timeindex и timex/temp1/temp2.

Данные уходят в создание roast через **`build_artisan_payload_from_profile(profile)`**: в т.ч. в `payload["telemetry"]` попадают `timex`, `temp1`, `temp2`, `timeindex` (и другие массивы). Сам .alog хранится как blob в `RoastProfile.data` или файл на диске.

---

## 2. Отдача .alog и profile data

### Скачать файл .alog
- **GET /api/v1/roasts/{roast_id}/profile**  
  Возвращает **сырой файл** .alog (то, что сохранено при загрузке): либо blob из БД, либо файл с диска. Формат тот же, что и при загрузке (Python dict repr или JSON в одном файле / в ZIP).

### Данные профиля для графика (JSON)
- **GET /api/v1/roasts/{roast_id}/profile/data**  
  Возвращает **JSON** (не сырой .alog), подходящий для фона в Artisan и для RDR.

Приоритет источников:
1. Blob в БД (`RoastProfile.data`) → парсинг как .alog → `compute_computed_from_timeindex` → `ensure_artisan_background_profile`.
2. Файл .alog на диске → то же.
3. Телеметрия в полях roast (timex, temp1, temp2, timeindex) → собирается объект `{ timex, temp1, temp2, timeindex, mode, computed, ... }` → `ensure_artisan_background_profile`.
4. Если ничего нет — минимальный объект с `computed` по полям roast.

Формат ответа (ключевые поля):
- **timex** — список float, секунды.
- **temp1** — ET.
- **temp2** — BT.
- **timeindex** — список из 8 элементов (индексы в timex).
- **mode** — `"C"` или `"F"`.
- **computed** — CHARGE_BT, DROP_time, TP_time, фазы и т.д.
- Дополнительно: title, beans, extratimex, extratemp1, extratemp2, specialevents, flavors, weight и т.д. (см. `ensure_artisan_background_profile`).

То есть конвенция **как в Artisan**: temp1 = ET, temp2 = BT.

---

## 3. Соответствие RDR

В RDR:
- **Загрузка референса с сервера**: `ReferenceApi.getProfileData()` дергает **GET …/roasts/{id}/profile/data** и передаёт JSON в **`ProfileStorage.profileFromServerJson(json)`**.
- **`profileFromServerJson`** ожидает в JSON:
  - **timex**, **temp1** (ET), **temp2** (BT), **timeindex**, **mode**.
  - temp1 → ET (domain temp2), temp2 → BT (domain temp1), строится `RoastProfile` с событиями Charge/Drop по timeindex[0] и timeindex[6].

Формат сервера и ожидания RDR совпадают: сервер отдаёт timex, temp1=ET, temp2=BT, timeindex, mode — RDR это корректно маппит в домен (temp1=BT, temp2=ET) и на график.

---

## 4. Итог

| Направление | Формат | Где на сервере |
|------------|--------|-----------------|
| **Приём** | Файл .alog (Python dict repr или JSON, опционально в ZIP) | `parse_alog_bytes` → `read_and_parse_alog` → `_parse_python_dict` при необходимости |
| **Отдача файла** | Сырой .alog (как хранится) | GET `…/roasts/{id}/profile` |
| **Отдача данных** | JSON: timex, temp1 (ET), temp2 (BT), timeindex, mode, computed, … | GET `…/roasts/{id}/profile/data` |

RDR с сервером совместим: загрузка референса через `/profile/data` и разбор через `profileFromServerJson` соответствуют тому, что сервер отдаёт.

**Замечание по серверу:** `_parse_python_dict` делает глобальную замену `'` → `"`. Для .alog из Artisan с простыми строками это обычно ок; строки с апострофами внутри в будущем лучше парсить через `ast.literal_eval` (на сервере) или доработать замену кавычек.
