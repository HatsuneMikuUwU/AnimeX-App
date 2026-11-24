@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.discussion

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.user.UserApi
import com.uwu.animex.data.user.UserSession
import com.uwu.animex.ui.background.screenContainerColor
import com.uwu.animex.ui.common.AnimatedEmptyState
import com.uwu.animex.ui.common.AppDialog
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.BlurContentBox
import com.uwu.animex.ui.common.DialogCancelButton
import com.uwu.animex.ui.common.DialogConfirmButton
import com.uwu.animex.ui.common.DialogDestructiveButton
import com.uwu.animex.ui.common.DiscussionListPlaceholder
import com.uwu.animex.ui.common.DiscussionRowPlaceholder
import com.uwu.animex.ui.common.ExpressivePullToRefreshBox
import com.uwu.animex.ui.common.FloatingGlassButton
import com.uwu.animex.ui.common.FloatingTopBar
import com.uwu.animex.ui.common.LocalTopInset
import com.uwu.animex.ui.common.UiState
import com.uwu.animex.ui.common.UiStateContent
import com.uwu.animex.ui.common.UserAvatar
import com.uwu.animex.ui.common.animateItemExpressive
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.rememberStableHapticAction
import com.uwu.animex.ui.common.show
import com.uwu.animex.ui.theme.barGlass
import com.uwu.animex.ui.theme.bottomScrim
import com.uwu.animex.ui.theme.rememberBlurBackdrop
import com.uwu.animex.ui.theme.resolveGlassStyle
import com.uwu.animex.ui.viewmodel.DiscussionScope
import com.uwu.animex.ui.viewmodel.DiscussionViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import kotlin.math.roundToInt

@Composable
internal fun DiscussionInputBar(
    draft: String,
    onDraftChange: (String) -> Unit,
    loggedIn: Boolean,
    sending: Boolean,
    backdrop: LayerBackdrop?,
    onSend: (String) -> Unit,
    onNeedLogin: () -> Unit,
    replyTo: ReplyTarget?,
    onCancelReply: () -> Unit,
    focusRequester: FocusRequester,
    placeholder: String = "Tulis diskusi...",
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val appearance by Appearance.settings.collectAsStateWithLifecycle()
    val glassStyle = resolveGlassStyle(backdrop, appearance.barEffect)
    val brush = remember(cs.background) { bottomScrim(cs.background) }
    val shape = RoundedCornerShape(DiscussionInputBarHeight / 2)
    val previewShape = CircleShape
    val canSend = loggedIn && !sending && draft.isNotBlank()

    Box(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val extra = DiscussionInputBarFade.toPx()
                drawRect(brush, topLeft = Offset(0f, -extra), size = Size(size.width, size.height + extra))
            }.navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 8.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
            AnimatedContent(
                targetState = replyTo,
                transitionSpec = {
                    (fadeIn() + expandVertically()) togetherWith (fadeOut() + shrinkVertically())
                },
                label = "reply-preview",
            ) { target ->
                if (target != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .barGlass(glassStyle, backdrop, previewShape, cs.surfaceContainer)
                            .clip(previewShape)
                            .padding(start = 20.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(32.dp)
                                .clip(CircleShape)
                                .background(cs.primary),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Membalas ${target.username}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                target.snippet.replace('\n', ' '),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = hapticClick(block = onCancelReply)) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "Batal membalas",
                                tint = cs.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = DiscussionInputBarHeight)
                        .barGlass(glassStyle, backdrop, shape, cs.surfaceContainer, strokeCornerRadius = DiscussionInputBarHeight / 2)
                        .clip(shape)
                        .then(if (loggedIn) Modifier else Modifier.hapticClick(onClick = onNeedLogin))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (loggedIn) {
                        BasicTextField(
                            value = draft,
                            onValueChange = onDraftChange,
                            enabled = !sending,
                            maxLines = 4,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
                            cursorBrush = SolidColor(cs.primary),
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                            decorationBox = { inner ->
                                if (draft.isEmpty()) {
                                    Text(
                                        placeholder,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = cs.onSurfaceVariant,
                                    )
                                }
                                inner()
                            },
                        )
                    } else {
                        Text(
                            "Login untuk ikut diskusi",
                            style = MaterialTheme.typography.bodyLarge,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
                FloatingGlassButton(
                    contentDescription = if (loggedIn) "Kirim" else "Login",
                    onClick =
                        hapticClick {
                            if (!loggedIn) {
                                onNeedLogin()
                            } else if (canSend) {
                                onSend(draft.trim())
                            }
                        },
                    backdrop = backdrop,
                    size = DiscussionInputBarHeight,
                ) {
                    when {
                        sending -> AppLoadingIndicator(Modifier.size(24.dp))
                        !loggedIn -> Icon(
                            Icons.AutoMirrored.Outlined.Login,
                            contentDescription = null,
                            tint = cs.onSurface,
                        )
                        else ->
                            Icon(
                                Icons.AutoMirrored.Outlined.Send,
                                contentDescription = null,
                                tint = if (canSend) cs.primary else cs.onSurface.copy(alpha = 0.38f),
                            )
                    }
                }
            }
        }
    }
}

