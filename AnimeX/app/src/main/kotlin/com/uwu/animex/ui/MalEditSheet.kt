@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import com.uwu.animex.data.statusOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.Bookmarks
import com.uwu.animex.data.Mal
import com.uwu.animex.data.MalLibrary
import com.uwu.animex.data.Movie
import com.uwu.animex.data.WatchStatus
import com.uwu.animex.sync.SyncResult
import com.uwu.animex.sync.SyncStatus
import com.uwu.animex.sync.SyncWatchType
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private sealed interface MalState {
    data object Loading : MalState
    data object NotFound : MalState
    data class Failed(val msg: String) : MalState
    data class Ready(val anime: SyncResult) : MalState
}

private val STATUS_ORDER: List<Pair<WatchStatus?, androidx.compose.ui.graphics.vector.ImageVector>> = listOf(
    null to Icons.Filled.RemoveCircleOutline, // Tidak Ada — tidak masuk list progress
    WatchStatus.WATCHING to Icons.Filled.PlayCircleOutline,
    WatchStatus.PLAN_TO_WATCH to Icons.Filled.Schedule,
    WatchStatus.COMPLETED to Icons.Filled.CheckCircleOutline,
    WatchStatus.ON_HOLD to Icons.Filled.PauseCircleOutline,
    WatchStatus.DROPPED to Icons.Filled.DeleteOutline,
)

private val SCORE_LABELS = listOf(
    "—", "Appalling", "Horrible", "Very Bad", "Bad", "Average", "Fine", "Good", "Very Good", "Great", "Masterpiece",
)
private val PRIORITY_LABELS = listOf("Rendah", "Sedang", "Tinggi")
private val REWATCH_LABELS = listOf("—", "Sangat Rendah", "Rendah", "Sedang", "Tinggi", "Sangat Tinggi")

private fun dateFmt() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
private fun millisOf(date: String?): Long? = date?.let { runCatching { dateFmt().parse(it)?.time }.getOrNull() }
private fun dateOf(millis: Long): String = dateFmt().format(Date(millis))
private fun todayStr(): String = Mal.today()
private fun displayDate(date: String): String {
    val ms = millisOf(date) ?: return date
    val fmt = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }
    return fmt.format(Date(ms))
}

@Composable
fun MalEditSheet(movie: Movie, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()

    val loggedIn by Mal.loggedIn.collectAsState()
    val autoSync by Mal.autoSync.collectAsState()
    val bookmarks by Bookmarks.entries.collectAsState()
    val pre = remember { if (Mal.loggedIn.value) Mal.preloaded(movie.id) else null }
    val preStatus = pre?.myStatus

    val cachedMalId = remember { if (Mal.loggedIn.value) (pre?.id?.toIntOrNull() ?: Mal.malIdFor(movie.id)) else null }
    val libItem = remember { cachedMalId?.let { id -> MalLibrary.items.value.firstOrNull { it.syncId == id.toString() } } }
    val cachedTotal = remember { cachedMalId?.let { Mal.cachedTotal(it) } }
    val canPrefill = pre != null || (cachedMalId != null && (libItem != null || MalLibrary.loaded))

    var state by remember {
        mutableStateOf<MalState?>(
            when {
                !Mal.loggedIn.value -> null
                pre != null -> MalState.Ready(pre)
                canPrefill -> MalState.Ready(
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

    var total by remember { mutableStateOf(pre?.totalEpisodes ?: libItem?.episodesTotal ?: cachedTotal?.takeIf { it > 0 }) }

    LaunchedEffect(Unit) {
        if (!Mal.loggedIn.value || pre != null) return@LaunchedEffect
        state = try {
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
            MalState.Failed(e.message ?: "Gagal terhubung ke MAL")
        }
    }

    fun changeProgress(value: Int) {
        val t = total
        val p = value.coerceIn(0, t ?: Int.MAX_VALUE)
        progress = p
        // Status null (Tidak Ada) = user sengaja tidak track; jangan auto-set Watching
        if (status == null) return
        if (t != null && p >= t) {
            status = WatchStatus.COMPLETED
            if (endDate == null) endDate = todayStr()
        } else if (p > 0 && (status == WatchStatus.PLAN_TO_WATCH || status == WatchStatus.COMPLETED)) {
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
                    val malId = s.anime.id.toIntOrNull() ?: throw IllegalStateException("ID MAL tidak valid")
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
                            tags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                .takeIf { detailsLoaded || it.isNotEmpty() },
                            comments = notes.takeIf { detailsLoaded || it.isNotEmpty() },
                        ),
                        hint = s.anime,
                    )
                }

                // Selalu simpan status lokal (termasuk null = Tidak Ada)
                Bookmarks.setStatus(movie, status)
                onDismiss()
            } catch (e: Exception) {
                error = e.message ?: "Gagal menyimpan"
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
                if (s is MalState.Ready && !isNew) s.anime.id.toIntOrNull()?.let { Mal.delete(it) }
                Bookmarks.setStatus(movie, null)
                onDismiss()
            } catch (e: Exception) {
                error = e.message ?: "Gagal menghapus"
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
        DatePickerDialog(
            onDismissRequest = { picker = null },
            modifier = Modifier
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
        ) { DatePicker(state = pickerState) }
    }

    if (confirmDelete) {
        AppDialog(
            icon = Icons.Filled.DeleteOutline,
            onDismiss = { confirmDelete = false },
            title = "Hapus dari daftar?",
            text = {
                Text(
                    if (state is MalState.Ready && !isNew) "Entri ini akan dihapus dari daftar MyAnimeList kamu."
                    else "Status anime ini akan dihapus dari Bookmark.",
                )
            },
            confirmButton = { DialogDestructiveButton("Hapus") { delete() } },
            dismissButton = { DialogCancelButton { confirmDelete = false } },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
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
                TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text("Batal") }
                Button(
                    onClick = { apply() },
                    enabled = !saving && state != MalState.Loading,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(if (isNew) "Tambah" else "Terapkan")
                }
            }

            val haptic = LocalHapticFeedback.current
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                STATUS_ORDER.forEach { (option, icon) ->
                    val label = option?.label ?: "Tidak Ada"
                    val tooltipState = rememberTooltipState()
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                            positioning = TooltipAnchorPosition.Above,
                        ),
                        tooltip = { PlainTooltip { Text(label) } },
                        focusable = false,
                        state = tooltipState,
                    ) {
                        FilledIconToggleButton(
                            checked = status == option,
                            onCheckedChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch { tooltipState.show() }
                                status = option
                                val t = total
                                if (option == WatchStatus.COMPLETED && t != null) changeProgress(t)
                            },
                            shapes = IconButtonDefaults.toggleableShapes(),
                            colors = IconButtonDefaults.filledIconToggleButtonColors(
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
                MalState.NotFound -> Notice("Anime ini tidak ditemukan di MAL. Hanya status lokal yang akan disimpan.")
                is MalState.Failed -> Notice("MAL: ${s.msg}. Hanya status lokal yang akan disimpan.")
                MalState.Loading, is MalState.Ready -> {
                    ProgressRow(
                        icon = Icons.Filled.PlayCircleOutline,
                        value = progress,
                        total = total,
                        label = "Episode",
                        max = total,
                        onValueChange = { changeProgress(it) },
                        onMinus = { changeProgress(progress - 1) },
                        onPlus = { changeProgress(progress + 1) },
                    )
                    ProgressRow(
                        icon = Icons.Filled.Star,
                        value = score,
                        total = 10,
                        label = if (score == 0) "" else SCORE_LABELS[score],
                        max = 10,
                        modifier = Modifier.padding(top = 8.dp),
                        onValueChange = { score = it.coerceIn(0, 10) },
                        onMinus = { score = (score - 1).coerceAtLeast(0) },
                        onPlus = { score = (score + 1).coerceAtMost(10) },
                    )

                    HorizontalDivider(Modifier.padding(vertical = 16.dp))

                    DateField(Icons.Filled.CalendarToday, "Tanggal Mulai", startDate, { picker = 0 }) { startDate = null }
                    DateField(Icons.Filled.EventAvailable, "Tanggal Selesai", endDate, { picker = 1 }) { endDate = null }

                    TextRow(Icons.AutoMirrored.Filled.Label, "Tag", tags) { tags = it }

                    ValueRow(
                        icon = Icons.Filled.PriorityHigh,
                        label = "Prioritas: ${PRIORITY_LABELS[priority]}",
                        modifier = Modifier.padding(bottom = 8.dp),
                        minusEnabled = priority > 0,
                        plusEnabled = priority < 2,
                        onMinus = { priority-- },
                        onPlus = { priority++ },
                    )

                    SwitchRow(Icons.Filled.Repeat, "Menonton Ulang", rewatching) { rewatching = it }

                    ProgressRow(
                        icon = Icons.Filled.RepeatOne,
                        value = rewatchCount,
                        total = null,
                        label = "Total Tonton Ulang",
                        max = null,
                        modifier = Modifier.padding(top = 8.dp),
                        onValueChange = { rewatchCount = it },
                        onMinus = { rewatchCount = (rewatchCount - 1).coerceAtLeast(0) },
                        onPlus = { rewatchCount++ },
                    )
                    ValueRow(
                        icon = Icons.Filled.EventRepeat,
                        label = "Nilai Tonton Ulang: ${REWATCH_LABELS[rewatchValue]}",
                        modifier = Modifier.padding(top = 8.dp),
                        minusEnabled = rewatchValue > 0,
                        plusEnabled = rewatchValue < 5,
                        onMinus = { rewatchValue-- },
                        onPlus = { rewatchValue++ },
                    )

                    TextRow(Icons.AutoMirrored.Filled.Notes, "Catatan", notes) { notes = it }
                }
            }

            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (loggedIn) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SwitchRow(Icons.Filled.Sync, "Sinkron otomatis", autoSync) { Mal.updateAutoSync(it) }

                val canDelete = (state is MalState.Ready && !isNew) || bookmarks.statusOf(movie.id) != null
                val tint = MaterialTheme.colorScheme.error.copy(alpha = if (canDelete) 1f else 0.38f)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = canDelete && !saving) { confirmDelete = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                        tint = tint,
                    )
                    Text("Hapus", Modifier.padding(horizontal = 16.dp), color = tint, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ProgressRow(
    icon: ImageVector,
    value: Int,
    total: Int?,
    label: String,
    max: Int?,
    onValueChange: (Int) -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier.fillMaxWidth().padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = label, modifier = Modifier.padding(horizontal = 16.dp))
            BasicTextField(
                value = value.toString(),
                onValueChange = { onValueChange(it.filter(Char::isDigit).toIntOrNull() ?: 0) },
                modifier = Modifier.width(IntrinsicSize.Min),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            )
            if (total != null) {
                Text(
                    "/$total",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (label.isNotEmpty()) {
                Text(
                    label,
                    modifier = Modifier.padding(start = if (total == null) 8.dp else 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        FilledTonalIconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onMinus()
            },
            enabled = value > 0,
            shapes = IconButtonDefaults.shapes(),
        ) { Icon(Icons.Filled.Remove, contentDescription = "Kurangi") }
        FilledTonalIconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onPlus()
            },
            enabled = max == null || value < max,
            shapes = IconButtonDefaults.shapes(),
        ) { Icon(Icons.Filled.Add, contentDescription = "Tambah") }
    }
}

@Composable
private fun ValueRow(
    icon: ImageVector,
    label: String,
    minusEnabled: Boolean,
    plusEnabled: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier.fillMaxWidth().padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = label, modifier = Modifier.padding(horizontal = 16.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurface, overflow = TextOverflow.Ellipsis)
        }
        FilledTonalIconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onMinus()
            },
            enabled = minusEnabled,
            shapes = IconButtonDefaults.shapes(),
        ) { Icon(Icons.Filled.Remove, contentDescription = "Kurangi") }
        FilledTonalIconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onPlus()
            },
            enabled = plusEnabled,
            shapes = IconButtonDefaults.shapes(),
        ) { Icon(Icons.Filled.Add, contentDescription = "Tambah") }
    }
}

@Composable
private fun DateField(icon: ImageVector, label: String, date: String?, onClick: () -> Unit, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.padding(start = 16.dp))
            Column(
                Modifier.heightIn(min = 64.dp).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(label, color = MaterialTheme.colorScheme.onSurface)
                if (date != null) {
                    Text(
                        displayDate(date),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        if (date != null) {
            IconButton(onClick = onClear, modifier = Modifier.padding(horizontal = 16.dp), shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Filled.Close, contentDescription = "Hapus tanggal")
            }
        }
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp))
            Text(
                title,
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun TextRow(icon: ImageVector, placeholder: String, value: String, onChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = placeholder, modifier = Modifier.padding(start = 16.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text(placeholder) },
            singleLine = false,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = Color.Transparent,
            ),
        )
    }
}
