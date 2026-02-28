package com.rdr.roast.app

/**
 * URL сайта (сервера) записан в коде приложения.
 * Используется для API (references, profile/data, auth, aroast) и для ссылок (регистрация, восстановление пароля).
 */
object ServerConfig {
    /** Базовый URL API (например https://www.artqqplus.ru/api/v1). Без завершающего слеша. */
    const val API_BASE_URL: String = "https://www.artqqplus.ru/api/v1"

    /** Базовый URL веб-сайта для регистрации и восстановления пароля (например https://www.artqqplus.ru). */
    const val WEB_BASE_URL: String = "https://www.artqqplus.ru"
}
