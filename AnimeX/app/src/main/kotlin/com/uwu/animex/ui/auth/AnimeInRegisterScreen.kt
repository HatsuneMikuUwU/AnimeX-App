package com.uwu.animex.ui.auth

import android.util.Patterns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.animein.AnimeInAccount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val pokemonChoices = listOf(
    "152" to "Chikorita",
    "155" to "Cyndaquil",
    "158" to "Totodile",
    "025" to "Pikachu",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeInRegisterScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var selectedPokemon by rememberSaveable { mutableStateOf("152") }
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var registrationComplete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Buat akun ANIMEIN") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (registrationComplete) {
                Icon(
                    imageVector = Icons.Outlined.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                )
                Text("Pendaftaran berhasil", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Akun sudah dibuat. Silakan masuk dengan username/email dan password tadi.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text("Kembali ke login")
                }
            } else {
                Text(
                    "Isi data untuk membuat akun ANIMEIN.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Username") },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Next,
                    ),
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email") },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    singleLine = true,
                    enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    ),
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ulangi password") },
                    singleLine = true,
                    enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                )
                Text(
                    "Pilih Pokémon",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleMedium,
                )
                pokemonChoices.chunked(2).forEach { rowChoices ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowChoices.forEach { (code, name) ->
                            FilterChip(
                                selected = selectedPokemon == code,
                                onClick = {
                                    selectedPokemon = code
                                    errorMessage = null
                                },
                                enabled = !busy,
                                label = { Text(name) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rowChoices.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                if (errorMessage != null) {
                    Text(
                        text = errorMessage.orEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Button(
                    onClick = {
                        val normalizedEmail = email.trim()
                        when {
                            !username.matches(Regex("^[A-Za-z0-9]+$")) -> {
                                errorMessage = "Username hanya boleh menggunakan huruf dan angka"
                            }
                            !Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches() -> {
                                errorMessage = "Format email tidak valid"
                            }
                            password != confirmPassword -> {
                                errorMessage = "Password tidak cocok"
                            }
                            else -> {
                                scope.launch {
                                    busy = true
                                    errorMessage = null
                                    try {
                                        AnimeInAccount.register(username, normalizedEmail, password, selectedPokemon)
                                        password = ""
                                        confirmPassword = ""
                                        registrationComplete = true
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        errorMessage =
                                            e.message?.takeIf { it.isNotBlank() } ?: "Pendaftaran gagal. Coba lagi."
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        }
                    },
                    enabled = !busy &&
                        username.isNotBlank() &&
                        email.isNotBlank() &&
                        password.isNotBlank() &&
                        confirmPassword.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Buat akun")
                    }
                }
                TextButton(onClick = onBack, enabled = !busy) {
                    Text("Sudah punya akun? Login")
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
