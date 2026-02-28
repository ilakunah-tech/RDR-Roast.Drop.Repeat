package com.rdr.roast.ui

import com.rdr.roast.app.ServerApi
import com.rdr.roast.app.ServerApiException
import com.rdr.roast.app.ServerConfig
import com.rdr.roast.app.SettingsManager
import java.net.URI
import javafx.fxml.FXML
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.Hyperlink
import javafx.scene.control.Label
import javafx.scene.control.PasswordField
import javafx.scene.control.TextField
import javafx.stage.Stage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.withContext

class LoginController {

    @FXML lateinit var txtEmail: TextField
    @FXML lateinit var txtPassword: PasswordField
    @FXML lateinit var chkRemember: CheckBox
    @FXML lateinit var btnOk: Button
    @FXML lateinit var btnCancel: Button
    @FXML lateinit var linkRegister: Hyperlink
    @FXML lateinit var linkResetPassword: Hyperlink
    @FXML lateinit var lblError: Label

    private var stage: Stage? = null
    private val scope = CoroutineScope(Dispatchers.JavaFx + Job())

    var onSuccess: (() -> Unit)? = null

    fun setStage(s: Stage?) { stage = s }

    fun setInitialEmail(email: String?) {
        if (::txtEmail.isInitialized && !email.isNullOrBlank()) txtEmail.text = email
    }

    @FXML
    fun initialize() {
        btnOk.setOnAction { doLogin() }
        btnCancel.setOnAction { stage?.close() }
        linkRegister.setOnAction { openBrowser(linkRegister.text) }
        linkResetPassword.setOnAction { openBrowser(linkResetPassword.text) }
    }

    private fun openBrowser(linkText: String) {
        val base = getWebBaseUrl()
        val path = when {
            linkText.contains("Регистрация", ignoreCase = true) -> "register"
            linkText.contains("Восстановление", ignoreCase = true) -> "resetPassword"
            else -> ""
        }
        val url = if (base.isNotEmpty() && path.isNotEmpty()) "$base/$path" else null
        if (!url.isNullOrBlank()) {
            try {
                java.awt.Desktop.getDesktop().browse(URI.create(url))
            } catch (_: Exception) { }
        }
    }

    private fun getWebBaseUrl(): String = ServerConfig.WEB_BASE_URL

    private fun doLogin() {
        val email = txtEmail.text?.trim() ?: ""
        val password = txtPassword.text ?: ""
        val baseUrl = ServerConfig.API_BASE_URL.trim().removeSuffix("/")
        lblError.text = ""
        lblError.isVisible = false
        lblError.isManaged = false
        if (email.isBlank() || !email.contains("@")) {
            showError("Введите корректный email.")
            return
        }
        if (password.isBlank()) {
            showError("Введите пароль.")
            return
        }
        btnOk.isDisable = true
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    ServerApi.login(baseUrl, email, password, chkRemember.isSelected)
                }
                withContext(Dispatchers.JavaFx) {
                    val settings = SettingsManager.load()
                    val newSettings = settings.copy(
                        serverToken = result.token,
                        serverRefreshToken = result.refreshToken ?: settings.serverRefreshToken,
                        serverRememberEmail = if (chkRemember.isSelected) (result.email ?: email) else settings.serverRememberEmail
                    )
                    SettingsManager.save(newSettings)
                    onSuccess?.invoke()
                    stage?.close()
                }
            } catch (e: ServerApiException) {
                withContext(Dispatchers.JavaFx) {
                    btnOk.isDisable = false
                    val msg = when (e.statusCode) {
                        401 -> "Неверный email или пароль."
                        429 -> "Слишком много попыток. Подождите минуту."
                        else -> e.message ?: "Ошибка входа (${e.statusCode})."
                    }
                    showError(msg)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.JavaFx) {
                    btnOk.isDisable = false
                    showError("Нет связи с сервером. Проверьте интернет и адрес сервера.")
                }
            }
        }
    }

    private fun showError(msg: String) {
        lblError.text = msg
        lblError.isVisible = true
        lblError.isManaged = true
    }
}
