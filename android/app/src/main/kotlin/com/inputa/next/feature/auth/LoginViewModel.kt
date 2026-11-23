package com.inputa.next.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inputa.next.core.data.LingqApi
import com.inputa.next.core.data.LoginResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val api: LingqApi,
) : ViewModel() {

    data class State(
        val username: String = "",
        val password: String = "",
        val token: String = "",
        /** true = 用 Token 登录（Google 账号也适用）；false = 用户名/密码。 */
        val tokenMode: Boolean = false,
        val loading: Boolean = false,
        val error: String? = null,
        val loggedIn: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun setUsername(value: String) = _state.update { it.copy(username = value, error = null) }
    fun setPassword(value: String) = _state.update { it.copy(password = value, error = null) }
    fun setToken(value: String) = _state.update { it.copy(token = value, error = null) }

    fun setTokenMode(enabled: Boolean) {
        _state.update { it.copy(tokenMode = enabled, error = null) }
    }

    fun submit() {
        val current = _state.value
        if (current.loading) return
        if (current.tokenMode) {
            submitToken()
            return
        }
        if (current.username.isBlank() || current.password.isBlank()) {
            _state.update { it.copy(error = "Enter your LingQ username and password") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = api.login(current.username, current.password)) {
                is LoginResult.Success -> _state.update {
                    it.copy(loading = false, loggedIn = true)
                }
                is LoginResult.Error -> _state.update {
                    it.copy(loading = false, error = result.message)
                }
            }
        }
    }

    private fun submitToken() {
        val token = _state.value.token
        if (token.isBlank()) {
            _state.update { it.copy(error = "Paste your LingQ token") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = api.loginWithToken(token)) {
                is LoginResult.Success -> _state.update {
                    it.copy(loading = false, loggedIn = true)
                }
                is LoginResult.Error -> _state.update {
                    it.copy(loading = false, error = result.message)
                }
            }
        }
    }
}
