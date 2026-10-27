@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)

package com.uwu.animex.ui.profile

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.uwu.animex.data.anilist.AniList
import com.uwu.animex.data.local.SyncSource
import com.uwu.animex.data.local.SyncSourcePref
import com.uwu.animex.data.mal.Mal

@Composable
fun AccountAvatar(modifier: Modifier = Modifier) {
    val malUser by Mal.user.collectAsStateWithLifecycle()
    val malLoggedIn by Mal.loggedIn.collectAsStateWithLifecycle()
    val aniUser by AniList.user.collectAsStateWithLifecycle()
    val aniLoggedIn by AniList.loggedIn.collectAsStateWithLifecycle()
    val syncPref by SyncSourcePref.source.collectAsStateWithLifecycle()
    val pic =
        when (SyncSourcePref.effective(syncPref, malLoggedIn, aniLoggedIn)) {
            SyncSource.MAL -> malUser?.picture
            SyncSource.ANILIST -> aniUser?.avatar
            SyncSource.LOCAL -> null
        }
    if (!pic.isNullOrBlank()) {
        AsyncImage(
            model = pic,
            contentDescription = "Profil",
            contentScale = ContentScale.Crop,
            modifier = modifier.size(32.dp).clip(CircleShape),
        )
    } else {
        Icon(
            Icons.Outlined.AccountCircle,
            contentDescription = if (malLoggedIn || aniLoggedIn) "Profil" else "Login",
            modifier = modifier.size(32.dp),
        )
    }
}
