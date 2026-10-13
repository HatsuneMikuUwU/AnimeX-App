@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.animein

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.data.animein.AnimeIn
import com.uwu.animex.data.animein.AnimeInSession
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.show
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AnimeInLoginScreen(onBack: () -> Unit) {
    val user by AnimeInSession.user.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    fun submit() {
        if (busy) return
        if (identifier.isBlank() || password.isBlank()) {
            snackbar.show(scope, "Isi email dan password-nya dulu ya")
            return
        }
        busy = true
        scope.launch {
            try {
                val u = AnimeIn.login(identifier.trim(), password)
                password = ""
                snackbar.show(scope, "Berhasil masuk sebagai ${u.name ?: u.id}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbar.show(scope, e.toUserMessage())
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("ANIMEIN", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(horizontal = 8.dp),
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Balik")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val u = user
            if (u != null) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = cs.secondaryContainer,
                    contentColor = cs.onSecondaryContainer,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(64.dp).clip(MaterialTheme.shapes.large),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!u.avatar.isNullOrBlank()) {
                                AsyncImage(model = u.avatar, contentDescription = null, modifier = Modifier.fillMaxSize())
                            } else {
                                Icon(Icons.Outlined.AccountCircle, contentDescription = null, modifier = Modifier.size(48.dp))
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(u.name ?: "Pengguna ANIMEIN", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("ID ${u.id}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Text(
                    "Kamu sudah masuk. Sekarang bisa ikut diskusi di tab Diskusi pada halaman detail anime.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
                FilledTonalButton(
                    onClick = {
                        AnimeIn.logout()
                        snackbar.show(scope, "Kamu sudah keluar dari ANIMEIN")
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Keluar")
                }
            } else {
                Text(
                    "Masuk ke akun ANIMEIN buat ikut diskusi dan ngobrol soal anime favoritmu.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = cs.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = identifier,
                    onValueChange = { identifier = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email atau username") },
                    leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showPassword) "Sembunyiin password" else "Tampilin password",
                            )
                        }
                    },
                    singleLine = true,
                    enabled = !busy,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); submit() }),
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { focus.clearFocus(); submit() },
                    enabled = !busy,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy) {
                        AppLoadingIndicator(Modifier.size(24.dp), color = cs.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Sabar bentar ya…")
                    } else {
                        Text("Masuk")
                    }
                }
            }
        }
    }
}
