@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.anilist

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material.icons.outlined.SentimentNeutral
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.data.anilist.AniList
import com.uwu.animex.data.anilist.AniListLibrary
import com.uwu.animex.data.local.Bookmarks
import com.uwu.animex.data.local.WatchStatus
import com.uwu.animex.data.local.statusOf
import com.uwu.animex.data.model.Movie
import com.uwu.animex.data.tracker.SyncSource
import com.uwu.animex.data.tracker.SyncSourcePref
import com.uwu.animex.sync.SyncStatus
import com.uwu.animex.sync.SyncWatchType
import com.uwu.animex.sync.anilist.AniListEditData
import com.uwu.animex.sync.anilist.AniListEditOptions
import com.uwu.animex.sync.anilist.AniListEditUpdate
import com.uwu.animex.ui.common.AppDatePickerDialog
import com.uwu.animex.ui.common.AppDialog
import com.uwu.animex.ui.common.AppModalBottomSheet
import com.uwu.animex.ui.common.DialogCancelButton
import com.uwu.animex.ui.common.DialogConfirmButton
import com.uwu.animex.ui.common.DialogDestructiveButton
import com.uwu.animex.ui.common.groupedItemShape
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.hapticToggle
import com.uwu.animex.ui.tracker.DateField
import com.uwu.animex.ui.tracker.DeleteRow
import com.uwu.animex.ui.tracker.Notice
import com.uwu.animex.ui.tracker.PRIORITY_LABELS
import com.uwu.animex.ui.tracker.ProgressRow
import com.uwu.animex.ui.tracker.STATUS_ORDER
import com.uwu.animex.ui.tracker.SheetGroupGap
import com.uwu.animex.ui.tracker.SwitchRow
import com.uwu.animex.ui.tracker.TextRow
import com.uwu.animex.ui.tracker.TrackerMirror
import com.uwu.animex.ui.tracker.ValueRow
import com.uwu.animex.ui.tracker.dateOf
import com.uwu.animex.ui.tracker.millisOf
import com.uwu.animex.ui.tracker.rememberMirrorCandidate
import com.uwu.animex.ui.tracker.todayStr
import com.uwu.animex.ui.util.WindowBlurEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private sealed interface AniState {
    data object Loading : AniState

    data class Failed(
        val msg: String,
    ) : AniState

    data class Ready(
        val data: AniListEditData,
    ) : AniState
}

private enum class AniStatus(
    val api: String,
    val label: String,
    val icon: ImageVector,
    val watch: WatchStatus,
) {
    CURRENT("CURRENT", "Lagi Ditonton", Icons.Outlined.PlayCircleOutline, WatchStatus.WATCHING),
    PLANNING("PLANNING", "Mau Ditonton", Icons.Outlined.Schedule, WatchStatus.PLAN_TO_WATCH),
    COMPLETED("COMPLETED", "Selesai", Icons.Outlined.CheckCircleOutline, WatchStatus.COMPLETED),
    DROPPED("DROPPED", "Dihentikan", Icons.Outlined.DeleteOutline, WatchStatus.DROPPED),
    PAUSED("PAUSED", "Ditunda", Icons.Outlined.PauseCircleOutline, WatchStatus.ON_HOLD),
    REPEATING("REPEATING", "Nonton Ulang", Icons.Outlined.Repeat, WatchStatus.COMPLETED),
    ;

    companion object {
        fun fromApi(value: String?): AniStatus? = entries.firstOrNull { it.api == value }

        fun fromWatch(value: WatchStatus?): AniStatus? =
            when (value) {
                null -> null
                WatchStatus.WATCHING -> CURRENT
                WatchStatus.COMPLETED -> COMPLETED
                WatchStatus.ON_HOLD -> PAUSED
                WatchStatus.DROPPED -> DROPPED
                WatchStatus.PLAN_TO_WATCH -> PLANNING
            }
    }
}

internal val SMILEYS =
    listOf(
        Triple(1, Icons.Outlined.SentimentDissatisfied, "Kurang"),
        Triple(2, Icons.Outlined.SentimentNeutral, "Biasa"),
        Triple(3, Icons.Outlined.SentimentSatisfied, "Bagus"),
    )

internal fun scoreText(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

@Composable
fun AniListEditSheet(
    movie: Movie,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    val mirrorOn by SyncSourcePref.mirror.collectAsStateWithLifecycle()
    val mirrorCandidate = rememberMirrorCandidate(SyncSource.ANILIST)
    val mirrorTo = mirrorCandidate.takeIf { mirrorOn }
    val autoSync by AniList.autoSync.collectAsStateWithLifecycle()
    val bookmarks by Bookmarks.entries.collectAsStateWithLifecycle()

    val pre = remember { AniList.editPreloaded(movie.id) }
    val preEntry = pre?.entry

    val preRes = remember { AniList.preloaded(movie.id) }
    val preStatus = preRes?.myStatus
    val cachedId = remember { preRes?.id?.toIntOrNull() ?: AniList.aniListIdFor(movie.id) }
    val libItem =
        remember {
            cachedId?.let { id -> AniListLibrary.items.value.firstOrNull { it.syncId == id.toString() } }
        }
    val cachedTotal = remember { cachedId?.let { AniList.cachedTotal(it) } }
    val canPrefill =
        pre == null &&
            cachedId != null &&
            (preRes != null || libItem != null || AniListLibrary.items.value.isNotEmpty())
    val libStatus = libItem?.status?.toWatchStatus()
    val preAniStatus =
        if (preStatus?.isRewatching == true) {
            AniStatus.REPEATING
        } else {
            AniStatus.fromWatch(preStatus?.status?.toWatchStatus())
        }

    var state by remember {
        mutableStateOf<AniState>(
            when {
                pre != null -> AniState.Ready(pre)
                canPrefill ->
                    AniState.Ready(
                        AniListEditData(
                            mediaId = cachedId,
                            totalEpisodes =
                                preRes?.totalEpisodes ?: libItem?.episodesTotal ?: cachedTotal?.takeIf { it > 0 },
                            entry = null,
                            options = AniList.lastEditOptions ?: AniListEditOptions(),
                        ),
                    )
                else -> AniState.Loading
            },
        )
    }
    var detailsLoaded by remember { mutableStateOf(!canPrefill) }
    var isNew by remember {
        mutableStateOf(
            if (canPrefill) (if (preRes != null) preStatus == null else libItem == null) else preEntry == null,
        )
    }
    var status by remember {
        mutableStateOf(
            AniStatus.fromApi(preEntry?.status)
                ?: preAniStatus
                ?: AniStatus.fromWatch(libStatus)
                ?: AniStatus.fromWatch(Bookmarks.status(movie.id)),
        )
    }
    var progress by remember {
        mutableIntStateOf(preEntry?.progress ?: preStatus?.watchedEpisodes ?: libItem?.episodesCompleted ?: 0)
    }
    var score by remember { mutableDoubleStateOf(preEntry?.score ?: libItem?.personalRating?.toDouble() ?: 0.0) }
    val advanced = remember { mutableStateMapOf<String, Double>().apply { putAll(preEntry?.advancedScores.orEmpty()) } }
    var startDate by remember { mutableStateOf(preEntry?.startedAt ?: preStatus?.startDate ?: libItem?.startDate) }
    var endDate by remember { mutableStateOf(preEntry?.completedAt ?: preStatus?.finishDate ?: libItem?.finishDate) }
    var repeat by remember { mutableIntStateOf(preEntry?.repeat ?: preStatus?.rewatchCount ?: 0) }
    var priority by remember { mutableIntStateOf(preEntry?.priority ?: 0) }
    var isPrivate by remember { mutableStateOf(preEntry?.isPrivate ?: false) }
    var hidden by remember { mutableStateOf(preEntry?.hiddenFromStatusLists ?: false) }
    var notes by remember { mutableStateOf(preEntry?.notes ?: preStatus?.comments.orEmpty()) }
    var customSelected by remember { mutableStateOf(preEntry?.customLists.orEmpty()) }
    var total by remember {
        mutableStateOf(
            pre?.totalEpisodes ?: preRes?.totalEpisodes ?: libItem?.episodesTotal ?: cachedTotal?.takeIf { it > 0 },
        )
    }

    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showCustomLists by remember { mutableStateOf(false) }

    val ready = state as? AniState.Ready
    val options = ready?.data?.options ?: AniListEditOptions()
    val advancedVisible =
        options.advancedScoringEnabled &&
            options.advancedScoring.isNotEmpty() &&
            (options.scoreFormat == "POINT_100" || options.scoreFormat == "POINT_10_DECIMAL")

    LaunchedEffect(Unit) {
        if (pre != null) return@LaunchedEffect
        state =
            try {
                val data = AniList.editData(movie)
                val entry = data.entry
                isNew = entry == null
                total = data.totalEpisodes
                if (entry != null) {
                    status = AniStatus.fromApi(entry.status) ?: status
                    progress = entry.progress ?: 0
                    score = entry.score ?: 0.0
                    startDate = entry.startedAt
                    endDate = entry.completedAt
                    repeat = entry.repeat ?: 0
                    priority = entry.priority ?: 0
                    isPrivate = entry.isPrivate ?: false
                    hidden = entry.hiddenFromStatusLists ?: false
                    notes = entry.notes.orEmpty()
                    customSelected = entry.customLists
                    advanced.putAll(entry.advancedScores)
                }
                detailsLoaded = true
                AniState.Ready(data)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                detailsLoaded = true
                AniState.Failed(e.toUserMessage())
            }
    }

    fun changeStatus(value: AniStatus) {
        val started = (isNew || status == AniStatus.PLANNING) && value == AniStatus.CURRENT
        if (started) startDate = todayStr()
        if (value == AniStatus.COMPLETED) {
            total?.let { progress = it }
            if (startDate == null) startDate = todayStr()
            if (endDate == null) endDate = todayStr()
        }
        status = value
    }

    fun changeProgress(value: Int) {
        val t = total
        val p = value.coerceIn(0, t ?: Int.MAX_VALUE)
        val current = status
        progress = p
        if (t != null && p >= t && p > 0) {
            if (current == AniStatus.REPEATING) repeat++
            if (current != AniStatus.COMPLETED) changeStatus(AniStatus.COMPLETED)
        } else if (p > 0 && (current == null || current == AniStatus.PLANNING || current == AniStatus.PAUSED)) {
            changeStatus(AniStatus.CURRENT)
        }
    }

    fun changeAdvanced(
        name: String,
        value: Double,
    ) {
        advanced[name] = value
        val positives = advanced.values.filter { it > 0.0 }
        score = if (positives.isEmpty()) 0.0 else positives.sum() / positives.size
    }

    fun apply() {
        scope.launch {
            saving = true
            error = null
            try {
                val s = state
                if (s is AniState.Ready && status == null) {
                    if (!isNew && !AniList.delete(s.data.mediaId)) throw IllegalStateException("Gagal menghapus dari AniList")
                } else if (s is AniState.Ready) {
                    val opts = s.data.options
                    AniList.saveEntry(
                        s.data.mediaId,
                        AniListEditUpdate(
                            status = status?.api,
                            score = score,
                            progress = progress,
                            startedAt = startDate,
                            completedAt = endDate,
                            repeat = repeat,
                            priority = priority,
                            isPrivate = isPrivate,
                            hiddenFromStatusLists = hidden,
                            notes = notes,
                            advancedScores =
                                if (advancedVisible) opts.advancedScoring.map { advanced[it] ?: 0.0 } else null,
                            customLists = if (opts.customLists.isNotEmpty()) customSelected.toList() else null,
                        ),
                    )
                    if (mirrorTo != null) {
                        val mirrored =
                            SyncStatus(
                                status = SyncWatchType.from(status?.watch),
                                score = opts.scoreToTen(score),
                                watchedEpisodes = progress,
                                startDate = startDate?.takeIf { it.length == 10 },
                                finishDate = endDate?.takeIf { it.length == 10 },
                                isRewatching = status == AniStatus.REPEATING,
                                rewatchCount = repeat,
                                comments = notes,
                            )
                        TrackerMirror.update(mirrorTo, movie, mirrored)
                    }
                }
                Bookmarks.setStatus(movie, status?.watch)
                onDismiss()
            } catch (e: CancellationException) {
                throw e
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
                if (s is AniState.Ready && !isNew && !AniList.delete(s.data.mediaId)) throw IllegalStateException("Gagal menghapus dari AniList")
                if (mirrorTo != null) {
                    TrackerMirror.delete(mirrorTo, movie)
                }
                Bookmarks.setStatus(movie, null)
                onDismiss()
            } catch (e: CancellationException) {
                throw e
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

    if (showCustomLists) {
        var draft by remember { mutableStateOf(customSelected) }
        AppDialog(
            icon = Icons.AutoMirrored.Outlined.ListAlt,
            onDismiss = { showCustomLists = false },
            title = "Custom list",
            text = {
                if (options.customLists.isEmpty()) {
                    Text("Akun AniList kamu belum punya custom list.")
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        options.customLists.forEach { name ->
                            val checked = name in draft
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        onClick = hapticToggle(checked) {
                                            draft =
                                                if (checked) draft - name else draft + name
                                        },
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = null,
                                )
                                Text(name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                DialogConfirmButton("OK") {
                    customSelected = draft
                    showCustomLists = false
                }
            },
            dismissButton = { DialogCancelButton { showCustomLists = false } },
        )
    }

    if (confirmDelete) {
        AppDialog(
            icon = Icons.Outlined.DeleteOutline,
            onDismiss = { confirmDelete = false },
            title = "Buang dari daftar?",
            text = {
                Text(
                    if (ready != null && !isNew) {
                        "Entri ini bakal dihapus dari daftar AniList kamu."
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
                    enabled = !saving && state != AniState.Loading && detailsLoaded,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(if (isNew) "Tambahin" else "Simpan")
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                STATUS_ORDER.forEach { (option, icon) ->
                    ToggleIcon(
                        icon = icon,
                        label = option?.label ?: "Kosong",
                        checked = status != AniStatus.REPEATING && status?.watch == option,
                    ) {
                        val target = AniStatus.fromWatch(option)
                        if (target == null) status = null else changeStatus(target)
                    }
                }
            }

            when (val s = state) {
                is AniState.Failed -> Notice("AniList: ${s.msg}. Cuma status lokal yang bakal disimpan.")
                AniState.Loading, is AniState.Ready -> {
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

                    ScoreSection(
                        format = options.scoreFormat,
                        score = score,
                        shape = groupedItemShape(1, 2),
                        onChange = { score = it },
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

                    ValueRow(
                        icon = Icons.Outlined.PriorityHigh,
                        label = "Prioritas: ${PRIORITY_LABELS[priority.coerceIn(0, 2)]}",
                        shape = groupedItemShape(0, 3),
                        minusEnabled = priority > 0,
                        plusEnabled = priority < 2,
                        onMinus = { priority = (priority - 1).coerceIn(0, 2) },
                        onPlus = { priority = (priority + 1).coerceIn(0, 2) },
                    )
                    SwitchRow(
                        Icons.Outlined.Repeat,
                        "Nonton Ulang",
                        status == AniStatus.REPEATING,
                        { status = if (it) AniStatus.REPEATING else AniStatus.CURRENT },
                        groupedItemShape(1, 3),
                    )
                    ProgressRow(
                        icon = Icons.Outlined.RepeatOne,
                        value = repeat,
                        total = null,
                        label = "Total Nonton Ulang",
                        max = null,
                        shape = groupedItemShape(2, 3),
                        onValueChange = { repeat = it },
                        onMinus = { repeat = (repeat - 1).coerceAtLeast(0) },
                        onPlus = { repeat++ },
                    )

                    Spacer(Modifier.height(SheetGroupGap))

                    ActionRow(
                        icon = Icons.AutoMirrored.Outlined.ListAlt,
                        title = "Custom list",
                        shape = groupedItemShape(0, 3),
                    ) { showCustomLists = true }
                    SwitchRow(
                        Icons.Outlined.VisibilityOff,
                        "Sembunyikan dari daftar status",
                        hidden,
                        { hidden = it },
                        groupedItemShape(1, 3),
                    )
                    SwitchRow(Icons.Outlined.Lock, "Privat", isPrivate, { isPrivate = it }, groupedItemShape(2, 3))

                    Spacer(Modifier.height(SheetGroupGap))

                    TextRow(Icons.AutoMirrored.Outlined.Notes, "Catatan pribadi", notes, { notes = it })

                    if (advancedVisible) {
                        Spacer(Modifier.height(SheetGroupGap))
                        val advancedCount = options.advancedScoring.size
                        options.advancedScoring.forEachIndexed { index, name ->
                            val value = advanced[name] ?: 0.0
                            if (options.scoreFormat == "POINT_100") {
                                ProgressRow(
                                    icon = Icons.Outlined.Star,
                                    value = value.toInt(),
                                    total = 100,
                                    label = name,
                                    max = 100,
                                    shape = groupedItemShape(index, advancedCount),
                                    onValueChange = { changeAdvanced(name, it.coerceIn(0, 100).toDouble()) },
                                    onMinus = { changeAdvanced(name, (value - 1).coerceAtLeast(0.0)) },
                                    onPlus = { changeAdvanced(name, (value + 1).coerceAtMost(100.0)) },
                                )
                            } else {
                                DecimalScoreRow(
                                    icon = Icons.Outlined.Star,
                                    value = value,
                                    label = name,
                                    shape = groupedItemShape(index, advancedCount),
                                    onChange = { changeAdvanced(name, it) },
                                )
                            }
                        }
                    }
                }
            }

            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            Spacer(Modifier.height(SheetGroupGap))
            val footerCount = if (mirrorCandidate != null) 3 else 2
            var footerIndex = 0
            if (mirrorCandidate != null) {
                SwitchRow(
                    Icons.Outlined.Cloud,
                    "Sinkron juga ke MAL",
                    mirrorOn,
                    { SyncSourcePref.setMirror(it) },
                    groupedItemShape(footerIndex++, footerCount),
                )
            }
            SwitchRow(
                Icons.Outlined.Sync,
                "Sinkron otomatis",
                autoSync,
                { AniList.updateAutoSync(it) },
                groupedItemShape(footerIndex++, footerCount),
            )

            val canDelete = (ready != null && !isNew) || bookmarks.statusOf(movie.id) != null
            DeleteRow(
                enabled = canDelete && !saving,
                shape = groupedItemShape(footerIndex, footerCount),
            ) { confirmDelete = true }
        }
    }
}
