package com.uwu.animex.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.data.user.UserApi
import com.uwu.animex.data.user.UserSession
import com.uwu.animex.ui.background.screenContainerColor
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.AppMotion
import com.uwu.animex.ui.common.BlurContentBox
import com.uwu.animex.ui.common.FloatingTopBar
import com.uwu.animex.ui.common.LocalBottomInset
import com.uwu.animex.ui.common.LocalTopInset
import com.uwu.animex.ui.common.RotatingCookieFrame
import com.uwu.animex.ui.common.ShapedIconBadge
import com.uwu.animex.ui.common.SheetItemSurface
import com.uwu.animex.ui.common.groupedItemShape
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.theme.rememberBlurBackdrop
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onLoggedIn: () -> Unit,
    onOpenRegister: () -> Unit,
) {
    var identity by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val backdrop = rememberBlurBackdrop()

    fun doLogin() {
        if (identity.isBlank() || password.isBlank()) {
            error = "Email/username dan password wajib diisi"
            return
        }
        busy = true
        error = null
        scope.launch {
            try {
                val profile = UserApi.login(identity.trim(), password)
                UserSession.save(profile)
                onLoggedIn()
            } catch (e: Exception) {
                error = e.toUserMessage()
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        containerColor = screenContainerColor(),
        topBar = {
            FloatingTopBar(
                backdrop = backdrop,
                onBack = onBack,
                title = {
                    Text(
                        "Login",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        },
    ) { pad ->
        BlurContentBox(pad, backdrop, contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(16.dp + LocalTopInset.current))
                RotatingCookieFrame(frameSize = 144.dp, innerSize = 96.dp) {
                    Icon(
                        Icons.Outlined.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = cs.onPrimary,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "Masuk ke akun",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Gunakan akun yang sama seperti di Animein",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                Spacer(Modifier.height(28.dp))

                LoginField(
                    icon = Icons.Outlined.Person,
                    label = "Email atau username",
                    value = identity,
                    onValueChange = { identity = it },
                    index = 0,
                    enabled = !busy,
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                )
                LoginField(
                    icon = Icons.Outlined.Lock,
                    label = "Password",
                    value = password,
                    onValueChange = { password = it },
                    index = 1,
                    enabled = !busy,
                    visualTransformation =
                        if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    trailing = {
                        IconButton(onClick = hapticClick { showPass = !showPass }) {
                            Icon(
                                if (showPass) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showPass) "Sembunyikan password" else "Tampilkan password",
                            )
                        }
                    },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                    keyboardActions = KeyboardActions(onDone = { doLogin() }),
                )

                AnimatedVisibility(
                    visible = error != null,
                    enter = AppMotion.expandEnter(),
                    exit = AppMotion.expandExit(),
                ) {
                    Surface(
                        shape = RoundedCornerShape(19.dp),
                        color = cs.errorContainer,
                        contentColor = cs.onErrorContainer,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.ErrorOutline, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text(error.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = hapticClick { doLogin() },
                    shapes = ButtonDefaults.shapes(),
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 56.dp),
                ) {
                    if (busy) {
                        AppLoadingIndicator(Modifier.size(24.dp), color = cs.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Sabar bentar ya…")
                    } else {
                        Text("Masuk", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = hapticClick(block = onOpenRegister),
                    shapes = ButtonDefaults.shapes(),
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 56.dp),
                ) {
                    Text("Buat akun baru")
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = hapticClick(block = onBack), enabled = !busy) {
                    Text("Nanti saja")
                }
                Spacer(Modifier.height(16.dp + LocalBottomInset.current))
            }
        }
    }
}

@Composable
private fun LoginField(
    icon: ImageVector,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    index: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    SheetItemSurface(shape = groupedItemShape(index, 2), modifier = modifier) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShapedIconBadge(icon, contentDescription = label)
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label) },
                singleLine = true,
                enabled = enabled,
                visualTransformation = visualTransformation,
                trailingIcon = trailing,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                modifier = Modifier.weight(1f),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                    ),
            )
        }
    }
}
