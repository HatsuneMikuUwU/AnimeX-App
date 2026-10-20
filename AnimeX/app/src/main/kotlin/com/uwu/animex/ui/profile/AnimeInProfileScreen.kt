@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package com.uwu.animex.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.uwu.animex.R
import com.uwu.animex.ui.common.FloatingTabBarOverlay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.MonetizationOn
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.animein.AnimeIn
import com.uwu.animex.data.animein.AnimeInUser
import com.uwu.animex.ui.common.AppDialog
import com.uwu.animex.ui.common.DialogCancelButton
import com.uwu.animex.ui.common.DialogDestructiveButton
import kotlinx.coroutines.launch

@Composable
fun AnimeInProfileScreen(onBack: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val loggedIn by AnimeIn.loggedIn.collectAsStateWithLifecycle()
    val user by AnimeIn.user.collectAsStateWithLifecycle()
    val message by AnimeIn.message.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }
    var method by rememberSaveable { mutableStateOf(LoginMethod.GOOGLE) }

    LaunchedEffect(loggedIn) {
        if (loggedIn) runCatching { AnimeIn.refreshUser() }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            AnimeIn.clearMessage()
        }
    }

    if (confirmLogout) {
        AppDialog(
            icon = Icons.AutoMirrored.Outlined.Logout,
            onDismiss = { confirmLogout = false },
            title = "Keluar dari Animein?",
            text = {
                Text("Sesi Animein di app ini bakal dihapus. Akun MAL atau AniList kamu gak ikut kena.")
            },
            confirmButton = {
                DialogDestructiveButton("Keluar") {
                    AnimeIn.logout()
                    confirmLogout = false
                }
            },
            dismissButton = { DialogCancelButton { confirmLogout = false } },
        )
    }

    ProfileScaffold(
        title = "Animein",
        onBack = onBack,
        snackbar = snackbar,
        overlay = { backdrop ->
            if (!loggedIn) {
                FloatingTabBarOverlay(
                    items = LoginMethod.entries,
                    selectedIndex = { method.ordinal },
                    onSelected = { method = LoginMethod.entries[it] },
                    backdrop = backdrop,
                    label = { it.label },
                    icon = { item, _ ->
                        when (item) {
                            LoginMethod.GOOGLE ->
                                Icon(
                                    painter = painterResource(R.drawable.ic_google),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = Color.Unspecified,
                                )
                            LoginMethod.CREDENTIALS -> Icon(Icons.Outlined.Person, contentDescription = null)
                            LoginMethod.KEY_SESSION -> Icon(Icons.Outlined.Key, contentDescription = null)
                        }
                    },
                )
            }
        },
    ) {
        if (!loggedIn) {
            AnimeInLoginScreen(method)
        } else {
            val u = user
            ProfileContent(
                info =
                    ProfileInfo(
                        name = u?.username?.ifBlank { "User Animein" },
                        picture = u?.displayAvatar,
                        joined = u?.date_join,
                    ),
                openLabel = "Segarkan profil",
                onOpen = { scope.launch { AnimeIn.refreshUser() } },
                onLogout = { confirmLogout = true },
            ) {
                u?.let { AnimeInStatsSection(it) }
            }
        }
    }
}

@Composable
private fun AnimeInStatsSection(user: AnimeInUser) {
    val cs = MaterialTheme.colorScheme
    val big = 36.dp
    val small = 12.dp
    val topLeaning = RoundedCornerShape(topStart = big, topEnd = small, bottomEnd = big, bottomStart = small)
    val topMirrored = RoundedCornerShape(topStart = small, topEnd = big, bottomEnd = small, bottomStart = big)
    val midLeaning = RoundedCornerShape(topStart = small, topEnd = big, bottomEnd = big, bottomStart = small)
    val midMirrored = RoundedCornerShape(topStart = big, topEnd = small, bottomEnd = small, bottomStart = big)
    val botLeaning = RoundedCornerShape(topStart = small, topEnd = big, bottomEnd = small, bottomStart = big)
    val botMirrored = RoundedCornerShape(topStart = big, topEnd = small, bottomEnd = big, bottomStart = small)

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HighlightTile(
                Icons.Outlined.WorkspacePremium,
                if (user.pro) "Pro" else "Free",
                "Status akun",
                cs.primaryContainer,
                cs.onPrimaryContainer,
                topLeaning,
                Modifier.weight(1f),
            )
            HighlightTile(
                Icons.Outlined.Badge,
                "#${user.id}",
                "ID user",
                cs.secondaryContainer,
                cs.onSecondaryContainer,
                topMirrored,
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HighlightTile(
                Icons.Outlined.MonetizationOn,
                user.money_coin,
                "Koin",
                cs.tertiaryContainer,
                cs.onTertiaryContainer,
                midLeaning,
                Modifier.weight(1f),
            )
            HighlightTile(
                Icons.Outlined.Diamond,
                user.money_gem,
                "Permata",
                cs.primaryContainer,
                cs.onPrimaryContainer,
                midMirrored,
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HighlightTile(
                Icons.Outlined.Visibility,
                user.views,
                "Tayangan",
                cs.secondaryContainer,
                cs.onSecondaryContainer,
                botLeaning,
                Modifier.weight(1f),
            )
            HighlightTile(
                Icons.Outlined.ThumbUp,
                user.likes,
                "Suka",
                cs.tertiaryContainer,
                cs.onTertiaryContainer,
                botMirrored,
                Modifier.weight(1f),
            )
        }
    }
}
