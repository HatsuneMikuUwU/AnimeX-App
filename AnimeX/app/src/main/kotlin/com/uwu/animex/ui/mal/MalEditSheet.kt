@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.mal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.data.local.Bookmarks
import com.uwu.animex.data.local.SyncSource
import com.uwu.animex.data.local.SyncSourcePref
import com.uwu.animex.data.local.WatchStatus
import com.uwu.animex.data.local.statusOf
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.mal.MalLibrary
import com.uwu.animex.data.model.Movie
import com.uwu.animex.sync.SyncResult
import com.uwu.animex.sync.SyncStatus
import com.uwu.animex.sync.SyncWatchType
import com.uwu.animex.ui.common.AppDatePickerDialog
import com.uwu.animex.ui.common.AppDialog
import com.uwu.animex.ui.common.AppModalBottomSheet
import com.uwu.animex.ui.common.DialogCancelButton
import com.uwu.animex.ui.common.DialogConfirmButton
import com.uwu.animex.ui.common.DialogDestructiveButton
import com.uwu.animex.ui.common.groupedItemShape
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.label
import com.uwu.animex.ui.common.rememberHapticAction
import com.uwu.animex.ui.common.show
import com.uwu.animex.ui.tracker.DateField
import com.uwu.animex.ui.tracker.DeleteRow
import com.uwu.animex.ui.tracker.Notice
import com.uwu.animex.ui.tracker.PRIORITY_LABELS
import com.uwu.animex.ui.tracker.ProgressRow
import com.uwu.animex.ui.tracker.SCORE_LABELS
import com.uwu.animex.ui.tracker.STATUS_ORDER
import com.uwu.animex.ui.tracker.SheetGroupGap
import com.uwu.animex.ui.tracker.SwitchRow
import com.uwu.animex.ui.tracker.TextRow
import com.uwu.animex.ui.tracker.TrackerMirror
import com.uwu.animex.ui.tracker.ValueRow
import com.uwu.animex.ui.tracker.dateOf
import com.uwu.animex.ui.tracker.millisOf
import com.uwu.animex.ui.tracker.rememberMirrorCandidate
import com.uwu.animex.ui.tracker.rememberTrackerSource
import com.uwu.animex.ui.tracker.todayStr
import com.uwu.animex.ui.util.WindowBlurEffect
import kotlinx.coroutines.launch

private sealed interface MalState {
    data object Loading : MalState

    data object NotFound : MalState

    data class Failed(
        val msg: String,
    ) : MalState

    data class Ready(
        val anime: SyncResult,
    ) : MalState
}

private val REWATCH_LABELS = listOf("—", "Very Low", "Low", "Medium", "High", "Very High")

@Composable
fun MalEditSheet(
    movie: Movie,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    val source = rememberTrackerSource()
    val malLoggedIn = source == SyncSource.MAL
    val mirrorOn by SyncSourcePref.mirror.collectAsStateWithLifecycle()
    val mirrorCandidate = rememberMirrorCandidate(source)
    val mirrorTo = mirrorCandidate.takeIf { mirrorOn }
    val autoSync by Mal.autoSync.collectAsStateWithLifecycle()
    val bookmarks by Bookmarks.entries.collectAsStateWithLifecycle()
    val pre = remember { if (malLoggedIn) Mal.preloaded(movie.id) else null }
    val preStatus = pre?.myStatus

    val cachedMalId = remember { if (malLoggedIn) (pre?.id?.toIntOrNull() ?: Mal.malIdFor(movie.id)) else null }
    val libItem =
        remember {
            cachedMalId?.let { id -> MalLibrary.items.value.firstOrNull { it.syncId == id.toString() } }
        }
    val cachedTotal = remember { cachedMalId?.let { Mal.cachedTotal(it) } }
    val canPrefill = pre != null || (cachedMalId != null && (libItem != null || MalLibrary.loaded))

    var state by remember {
        mutableStateOf<MalState?>(
            when {
                !malLoggedIn -> null
                pre != null -> MalState.Ready(pre)
                canPrefill ->
                    MalState.Ready(
                        SyncResult(
                            id = cachedMalId.toString(),
                            title = libItem?.name,
                            totalEpisodes = libItem?.episodesTotal ?: cachedTotal?.takeIf { it > 0 },
                        ),
                    )
                else -> MalState.Loading
            },
        )
    }
    var isNew by remember { mutableStateOf(if (pre != null) preStatus == null else libItem == null) }
    var detailsLoaded by remember { mutableStateOf(pre != null || !canPrefill) }

    var status by remember {
        mutableStateOf<WatchStatus?>(
            preStatus?.status?.toWatchStatus()
                ?: libItem?.status?.toWatchStatus()
                ?: Bookmarks.status(movie.id),
        )
    }
    var progress by remember { mutableIntStateOf(preStatus?.watchedEpisodes ?: libItem?.episodesCompleted ?: 0) }
    var score by remember { mutableIntStateOf(preStatus?.score ?: libItem?.personalRating ?: 0) }
    var startDate by remember { mutableStateOf(preStatus?.startDate ?: libItem?.startDate) }
    var endDate by remember { mutableStateOf(preStatus?.finishDate ?: libItem?.finishDate) }
    var tags by remember { mutableStateOf(preStatus?.tags.orEmpty().joinToString(",")) }
    var priority by remember { mutableIntStateOf(preStatus?.priority ?: 0) }
    var rewatching by remember { mutableStateOf(preStatus?.isRewatching ?: false) }
    var rewatchCount by remember { mutableIntStateOf(preStatus?.rewatchCount ?: 0) }
    var rewatchValue by remember { mutableIntStateOf(preStatus?.rewatchValue ?: 0) }
    var notes by remember { mutableStateOf(preStatus?.comments.orEmpty()) }

    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    var total by remember {
        mutableStateOf(
            pre?.totalEpisodes ?: libItem?.episodesTotal ?: cachedTotal?.takeIf { it > 0 },
        )
    }

    LaunchedEffect(Unit) {
        if (malLoggedIn && pre == null) {
            state =
                try {
                    val anime = Mal.resolve(movie)
                    if (anime == null) {
                        MalState.NotFound
                    } else {
                        val l = anime.myStatus
                        isNew = l == null
                        if (l != null) {
                            if (libItem == null) {
                                status = l.status?.toWatchStatus() ?: status
                                progress = l.watchedEpisodes ?: 0
                                score = l.score ?: 0
                            }
                            if (startDate == null) startDate = l.startDate
                            if (endDate == null) endDate = l.finishDate
                            if (tags.isEmpty()) tags = l.tags.orEmpty().joinToString(",")
                            if (priority == 0) priority = l.priority ?: 0
                            if (!rewatching) rewatching = l.isRewatching ?: false
                            if (rewatchCount == 0) rewatchCount = l.rewatchCount ?: 0
                            if (rewatchValue == 0) rewatchValue = l.rewatchValue ?: 0
                            if (notes.isEmpty()) notes = l.comments.orEmpty()
                        }
                        anime.totalEpisodes?.let { total = it }
                        detailsLoaded = true
                        MalState.Ready(anime)
                    }
                } catch (e: Exception) {
                    MalState.Failed(e.toUserMessage())
                }
        }
    }

    fun changeProgress(value: Int) {
        val t = total
        val p = value.coerceIn(0, t ?: Int.MAX_VALUE)
        progress = p
        if (status == null && p > 0) status = WatchStatus.WATCHING
        if (status == null) return
        if (t != null && p >= t) {
            if (rewatching) {
                rewatching = false
                rewatchCount++
            }
            status = WatchStatus.COMPLETED
            if (endDate == null) endDate = todayStr()
        } else if (p > 0 && (status == WatchStatus.PLAN_TO_WATCH || (status == WatchStatus.COMPLETED && !rewatching))) {
            status = WatchStatus.WATCHING
            if (startDate == null) startDate = todayStr()
        }
    }

    fun apply() {
        scope.launch {
            saving = true
            error = null
            try {
                val s = state
                if (s is MalState.Ready) {
                    val malId = s.anime.id.toIntOrNull() ?: throw IllegalStateException("ID MAL-nya gak valid")
                    Mal.update(
                        malId,
                        SyncStatus(
                            status = SyncWatchType.from(status),
                            score = score,
                            watchedEpisodes = progress,
                            startDate = startDate,
                            finishDate = endDate,
                            isRewatching = rewatching.takeIf { detailsLoaded || it },
                            rewatchCount = rewatchCount.takeIf { detailsLoaded || it != 0 },
                            rewatchValue = rewatchValue.takeIf { detailsLoaded || it != 0 },
                            priority = priority.takeIf { detailsLoaded || it != 0 },
                            tags =
                                tags
                                    .split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                    .takeIf { detailsLoaded || it.isNotEmpty() },
                            comments = notes.takeIf { detailsLoaded || it.isNotEmpty() },
                        ),
                        hint = s.anime,
                    )
                }

                if (mirrorTo != null) {
                    val mirrored =
                        SyncStatus(
                            status = SyncWatchType.from(status),
                            score = score,
                            watchedEpisodes = progress,
                            startDate = startDate,
                            finishDate = endDate,
                            isRewatching = rewatching.takeIf { detailsLoaded || it },
                            rewatchCount = rewatchCount.takeIf { detailsLoaded || it != 0 },
                            comments = notes.takeIf { detailsLoaded || it.isNotEmpty() },
                        )
                    TrackerMirror.update(mirrorTo, movie, mirrored)
                }

                Bookmarks.setStatus(movie, status)
                onDismiss()
            } catch (e: Exception) {
                error = e.toUserMessage()
            } finally {
                saving = false
            }
        }
    }

    fun delete() {
        scope.launch {
            saving = true
            error = null
            try {
                val s = state
                if (s is MalState.Ready && !isNew) {
                    s.anime.id
                        .toIntOrNull()
                        ?.let { Mal.delete(it) }
                }
                if (mirrorTo != null) {
                    TrackerMirror.delete(mirrorTo, movie)
                }
                Bookmarks.setStatus(movie, null)
                onDismiss()
            } catch (e: Exception) {
                error = e.toUserMessage()
            } finally {
                saving = false
                confirmDelete = false
            }
        }
    }

    val pickerIndex = picker
    if (pickerIndex != null) {
        val initial = millisOf(if (pickerIndex == 0) startDate else endDate)
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial)
        AppDatePickerDialog(
            onDismissRequest = { picker = null },
            modifier =
                Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 560.dp),
            shape = MaterialTheme.shapes.extraLarge,
            confirmButton = {
                DialogConfirmButton("OK") {
                    pickerState.selectedDateMillis?.let {
                        if (pickerIndex == 0) startDate = dateOf(it) else endDate = dateOf(it)
                    }
                    picker = null
                }
            },
            dismissButton = { DialogCancelButton { picker = null } },
        ) {
            WindowBlurEffect()
            DatePicker(state = pickerState)
        }
    }

    if (confirmDelete) {
        AppDialog(
            icon = Icons.Outlined.DeleteOutline,
            onDismiss = { confirmDelete = false },
            title = "Buang dari daftar?",
            text = {
                Text(
                    if (state is MalState.Ready && !isNew) {
                        "Entri ini bakal dihapus dari daftar MyAnimeList kamu."
                    } else {
                        "Status anime ini bakal dihapus dari Bookmark."
                    },
                )
            },
            confirmButton = { DialogDestructiveButton("Hapus") { delete() } },
            dismissButton = { DialogCancelButton { confirmDelete = false } },
        )
    }

    AppModalBottomSheet(
        modifier = Modifier.statusBarsPadding(),
        onDismissRequest = onDismiss,
    ) {
        WindowBlurEffect()
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(bottom = 32.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = hapticClick(block = onDismiss),
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Gak usah deh") }
                Button(
                    onClick = hapticClick { apply() },
                    enabled = !saving && state != MalState.Loading,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(if (isNew) "Tambahin" else "Simpan")
                }
            }

            val doHaptic = rememberHapticAction(HapticFeedbackType.ToggleOn)
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                STATUS_ORDER.forEach { (option, icon) ->
                    val label = option?.label ?: "Kosong"
                    val tooltipState = rememberTooltipState()
                    TooltipBox(
                        positionProvider =
                            TooltipDefaults.rememberTooltipPositionProvider(
                                positioning = TooltipAnchorPosition.Above,
                            ),
                        tooltip = { PlainTooltip { Text(label) } },
                        focusable = false,
                        state = tooltipState,
                    ) {
                        FilledIconToggleButton(
                            checked = status == option && !rewatching,
                            onCheckedChange = {
                                doHaptic()
                                scope.launch { tooltipState.show() }
                                status = option
                                rewatching = false
                                val t = total
                                if (option == WatchStatus.COMPLETED && t != null) changeProgress(t)
                            },
                            shapes = IconButtonDefaults.toggleableShapes(),
                            colors =
                                IconButtonDefaults.filledIconToggleButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                                    checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                        ) { Icon(icon, contentDescription = label) }
                    }
                }
            }

            when (val s = state) {
                null -> Unit
                MalState.NotFound -> Notice("Anime ini gak ketemu di MAL. Cuma status lokal yang bakal disimpan.")
                is MalState.Failed -> Notice("MAL: ${s.msg}. Cuma status lokal yang bakal disimpan.")
                MalState.Loading, is MalState.Ready -> {
                    ProgressRow(
                        icon = Icons.Outlined.PlayCircleOutline,
                        value = progress,
                        total = total,
                        label = "Episode",
                        max = total,
                        shape = groupedItemShape(0, 2),
                        onValueChange = { changeProgress(it) },
                        onMinus = { changeProgress(progress - 1) },
                        onPlus = { changeProgress(progress + 1) },
                    )
                    ProgressRow(
                        icon = Icons.Outlined.Star,
                        value = score,
                        total = 10,
                        label = if (score == 0) "" else SCORE_LABELS[score],
                        max = 10,
                        shape = groupedItemShape(1, 2),
                        onValueChange = { score = it.coerceIn(0, 10) },
                        onMinus = { score = (score - 1).coerceAtLeast(0) },
                        onPlus = { score = (score + 1).coerceAtMost(10) },
                    )

                    Spacer(Modifier.height(SheetGroupGap))

                    DateField(
                        Icons.Outlined.CalendarToday,
                        "Tanggal Mulai",
                        startDate,
                        { picker = 0 },
                        { startDate = null },
                        groupedItemShape(0, 2),
                    )
                    DateField(
                        Icons.Outlined.EventAvailable,
                        "Tanggal Selesai",
                        endDate,
                        { picker = 1 },
                        { endDate = null },
                        groupedItemShape(1, 2),
                    )

                    Spacer(Modifier.height(SheetGroupGap))

                    TextRow(Icons.AutoMirrored.Outlined.Label, "Tag", tags, { tags = it }, groupedItemShape(0, 2))
                    ValueRow(
                        icon = Icons.Outlined.PriorityHigh,
                        label = "Prioritas: ${PRIORITY_LABELS[priority]}",
                        shape = groupedItemShape(1, 2),
                        minusEnabled = priority > 0,
                        plusEnabled = priority < 2,
                        onMinus = { priority-- },
                        onPlus = { priority++ },
                    )

                    Spacer(Modifier.height(SheetGroupGap))

                    SwitchRow(
                        Icons.Outlined.Repeat,
                        "Nonton Ulang",
                        rewatching,
                        { rewatching = it },
                        groupedItemShape(0, 3),
                    )
                    ProgressRow(
                        icon = Icons.Outlined.RepeatOne,
                        value = rewatchCount,
                        total = null,
                        label = "Total Nonton Ulang",
                        max = null,
                        shape = groupedItemShape(1, 3),
                        onValueChange = { rewatchCount = it },
                        onMinus = { rewatchCount = (rewatchCount - 1).coerceAtLeast(0) },
                        onPlus = { rewatchCount++ },
                    )
                    ValueRow(
                        icon = Icons.Outlined.EventRepeat,
                        label = "Nilai Nonton Ulang: ${REWATCH_LABELS[rewatchValue]}",
                        shape = groupedItemShape(2, 3),
                        minusEnabled = rewatchValue > 0,
                        plusEnabled = rewatchValue < 5,
                        onMinus = { rewatchValue-- },
                        onPlus = { rewatchValue++ },
                    )

                    Spacer(Modifier.height(SheetGroupGap))

                    TextRow(Icons.AutoMirrored.Outlined.Notes, "Catatan pribadi", notes, { notes = it })
                }
            }

            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (malLoggedIn) {
                Spacer(Modifier.height(SheetGroupGap))
                val footerCount = if (mirrorCandidate != null) 3 else 2
                var footerIndex = 0
                if (mirrorCandidate != null) {
                    SwitchRow(
                        Icons.Outlined.Cloud,
                        "Sinkron juga ke AniList",
                        mirrorOn,
                        { SyncSourcePref.setMirror(it) },
                        groupedItemShape(footerIndex++, footerCount),
                    )
                }
                SwitchRow(
                    Icons.Outlined.Sync,
                    "Sinkron otomatis",
                    autoSync,
                    { Mal.updateAutoSync(it) },
                    groupedItemShape(footerIndex++, footerCount),
                )

                val canDelete = (state is MalState.Ready && !isNew) || bookmarks.statusOf(movie.id) != null
                DeleteRow(
                    enabled = canDelete && !saving,
                    shape = groupedItemShape(footerIndex, footerCount),
                ) { confirmDelete = true }
            }
        }
    }
}
