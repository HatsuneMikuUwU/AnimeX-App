@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui.animein

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.animein.AnimeinAuth
import com.uwu.animex.data.animein.AnimeinSession
import com.uwu.animex.ui.background.screenContainerColor
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.FloatingTopBar
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.theme.rememberBlurBackdrop
import kotlinx.coroutines.launch

@Composable
fun AnimeinLoginScreen(
    onBack: () -> Unit = {},
    onSuccess: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val busy by AnimeinAuth.busy.collectAsStateWithLifecycle()
    val message by AnimeinSession.message.collectAsStateWithLifecycle()
    val loggedIn by AnimeinSession.loggedIn.collectAsStateWithLifecycle()

    var registerMode by rememberSaveable { mutableStateOf(false) }
    var identity by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            AnimeinSession.clearMessage()
        }
    }
    LaunchedEffect(loggedIn) {
        if (loggedIn) onSuccess()
    }

    val backdrop = rememberBlurBackdrop()
    Scaffold(
        topBar = {
            FloatingTopBar(
                backdrop = backdrop,
                onBack = onBack,
                title = {
                    Text(
                        if (registerMode) "Daftar Animein" else "Login Animein",
                        fontWeight = FontWeight.Bold,
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = screenContainerColor(),
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.Login,
                contentDescription = null,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Pakai akun Animein yang sama",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            if (registerMode) {
                OutlinedTextField(
                    value = identity,
                    onValueChange = { identity = it },
                    label = { Text("Username") },
                    leadingIcon = { Icon(Icons.Outlined.Person, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    enabled = !busy,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Outlined.Email, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                    enabled = !busy,
                )
            } else {
                OutlinedTextField(
                    value = identity,
                    onValueChange = { identity = it },
                    label = { Text("Username atau email") },
                    leadingIcon = { Icon(Icons.Outlined.Person, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    enabled = !busy,
                )
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (showPassword) "Sembunyikan" else "Tampilkan",
                        )
                    }
                },
                singleLine = true,
                visualTransformation =
                    if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onDone = {
                            if (!busy) {
                                scope.launch {
                                    if (registerMode) {
                                        AnimeinAuth.register(identity.trim(), email.trim(), password)
                                    } else {
                                        AnimeinAuth.login(identity.trim(), password)
                                    }
                                }
                            }
                        },
                    ),
                enabled = !busy,
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick =
                    hapticClick {
                        scope.launch {
                            if (registerMode) {
                                AnimeinAuth.register(identity.trim(), email.trim(), password)
                            } else {
                                AnimeinAuth.login(identity.trim(), password)
                            }
                        }
                    },
                enabled = !busy && identity.isNotBlank() && password.isNotBlank() &&
                    (!registerMode || email.isNotBlank()),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (busy) {
                    AppLoadingIndicator()
                } else {
                    Text(if (registerMode) "Daftar" else "Login", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { registerMode = !registerMode },
                enabled = !busy,
            ) {
                Text(
                    if (registerMode) "Sudah punya akun? Login" else "Belum punya akun? Daftar",
                )
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onBack, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Nanti saja")
            }
        }
    }
}
