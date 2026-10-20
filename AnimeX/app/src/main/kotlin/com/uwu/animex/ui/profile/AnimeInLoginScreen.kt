@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package com.uwu.animex.ui.profile

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.uwu.animex.R
import com.uwu.animex.data.animein.AnimeIn
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.LocalBottomInset
import com.uwu.animex.ui.common.LocalTopInset
import com.uwu.animex.ui.common.floatingTabBarSpace
import com.uwu.animex.ui.common.BadgeStyle
import com.uwu.animex.ui.common.ShapedIconBadge
import com.uwu.animex.ui.common.SheetItemSurface
import com.uwu.animex.ui.common.hapticClick
import kotlinx.coroutines.launch

internal enum class LoginMethod(
    val label: String,
) {
    GOOGLE("Google"),
    CREDENTIALS("Akun"),
    KEY_SESSION("ID & Key"),
}

/** Form login Animein, ditampilkan inline di layar profil (bukan bottom sheet), sama kayak MAL dan AniList. */
@Composable
internal fun AnimeInLoginScreen(method: LoginMethod) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    val busy by AnimeIn.busy.collectAsStateWithLifecycle()
    val message by AnimeIn.message.collectAsStateWithLifecycle()

    var usernameOrEmail by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var inputUserId by rememberSaveable { mutableStateOf("") }
    var inputKeyClient by rememberSaveable { mutableStateOf("") }

    val googleSignInClient =
        remember(context) {
            val gso =
                GoogleSignInOptions
                    .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(AnimeIn.GOOGLE_SERVER_CLIENT_ID)
                    .requestEmail()
                    .build()
            GoogleSignIn.getClient(context, gso)
        }

    val googleLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                try {
                    val account =
                        GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
                    val email = account.email.orEmpty()
                    val idToken = account.idToken.orEmpty()
                    if (email.isNotBlank() && idToken.isNotBlank()) {
                        scope.launch {
                            AnimeIn.loginWithGoogle(email, idToken)
                        }
                    } else {
                        AnimeIn.setMessage("Email atau token dari Google gak kebaca")
                    }
                } catch (e: ApiException) {
                    AnimeIn.setMessage(
                        when (e.statusCode) {
                            GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> "Login Google dibatalin"
                            10 -> "Google Sign-In butuh SHA-1 yang terdaftar (kode 10). Coba login pakai Akun atau ID & Key aja"
                            else -> "Login Google gagal (kode ${e.statusCode})"
                        },
                    )
                } catch (e: Exception) {
                    AnimeIn.setMessage("Login Google gagal: ${e.message}")
                }
            } else if (result.resultCode != Activity.RESULT_CANCELED) {
                AnimeIn.setMessage("Login Google dibatalin")
            }
        }

    fun submitCredentials() {
        if (usernameOrEmail.isBlank() || password.isBlank()) {
            AnimeIn.setMessage("Isi username/email dan kata sandinya dulu ya")
            return
        }
        scope.launch {
            AnimeIn.loginWithCredentials(usernameOrEmail.trim(), password)
        }
    }

    fun submitSession() {
        if (inputUserId.isBlank() || inputKeyClient.isBlank()) {
            AnimeIn.setMessage("ID user dan key client harus diisi")
            return
        }
        scope.launch {
            AnimeIn.loginWithSession(inputUserId, inputKeyClient)
        }
    }

    run {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(top = LocalTopInset.current + 24.dp, bottom = LocalBottomInset.current + floatingTabBarSpace() + 24.dp)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(96.dp)
                    .clip(MaterialShapes.Cookie12Sided.toShape())
                    .background(cs.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = cs.onTertiaryContainer,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Masuk ke Animein",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Sambungin akun Animein kamu buat lihat profil dan status pro-nya.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))

            message?.let { msg ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(cs.errorContainer)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = cs.onErrorContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            when (method) {
                LoginMethod.GOOGLE ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Pakai akun Google yang sama kayak di app Animein. Kalau belum punya akun, daftar dulu lewat app Animein.",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                        FilledTonalButton(
                            onClick =
                                hapticClick {
                                    scope.launch {
                                        runCatching { googleSignInClient.signOut() }
                                        googleLauncher.launch(googleSignInClient.signInIntent)
                                    }
                                },
                            enabled = !busy,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        ) {
                            if (busy) {
                                AppLoadingIndicator(Modifier.size(28.dp))
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.ic_google),
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize),
                                    tint = Color.Unspecified,
                                )
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text("Lanjutin pakai Google", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                LoginMethod.CREDENTIALS ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FieldRow(
                            icon = Icons.Outlined.Person,
                            value = usernameOrEmail,
                            onChange = { usernameOrEmail = it },
                            placeholder = "Username atau email",
                            keyboardOptions =
                                KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        )
                        FieldRow(
                            icon = Icons.Outlined.Lock,
                            value = password,
                            onChange = { password = it },
                            placeholder = "Kata sandi",
                            visualTransformation =
                                if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions =
                                KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submitCredentials() }),
                            trailing = {
                                IconButton(onClick = hapticClick { showPassword = !showPassword }) {
                                    Icon(
                                        if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = if (showPassword) "Sembunyikan sandi" else "Lihat sandi",
                                    )
                                }
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                        SubmitButton(
                            busy = busy,
                            icon = Icons.AutoMirrored.Outlined.Login,
                            label = "Masuk",
                            onClick = ::submitCredentials,
                        )
                    }

                LoginMethod.KEY_SESSION ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Isi ID user dan key client dari traffic app Animein (misal lewat ProxyPin) kalau login biasa bermasalah.",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        FieldRow(
                            icon = Icons.Outlined.Person,
                            value = inputUserId,
                            onChange = { inputUserId = it },
                            placeholder = "ID user (contoh: 995344)",
                            keyboardOptions =
                                KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        )
                        FieldRow(
                            icon = Icons.Outlined.Key,
                            value = inputKeyClient,
                            onChange = { inputKeyClient = it },
                            placeholder = "Key client",
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submitSession() }),
                        )
                        Spacer(Modifier.height(8.dp))
                        SubmitButton(
                            busy = busy,
                            icon = Icons.Outlined.Check,
                            label = "Simpan & sambungin",
                            onClick = ::submitSession,
                        )
                    }
            }
        }
    }
}

@Composable
private fun FieldRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: @Composable (() -> Unit)? = null,
) {
    SheetItemSurface(
        shape = com.uwu.animex.ui.common.SingleItemShape,
        modifier = modifier,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShapedIconBadge(icon, style = BadgeStyle.Default, contentDescription = placeholder)
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                placeholder = { Text(placeholder) },
                singleLine = true,
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                trailingIcon = trailing,
                modifier = Modifier.weight(1f),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                    ),
            )
        }
    }
}

@Composable
private fun SubmitButton(
    busy: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = hapticClick(block = onClick),
        enabled = !busy,
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        if (busy) {
            AppLoadingIndicator(Modifier.size(28.dp))
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(label, fontWeight = FontWeight.Bold)
        }
    }
}
