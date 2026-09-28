@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.Bookmarks
import com.uwu.animex.data.Mal
import com.uwu.animex.data.Movie
import com.uwu.animex.data.WatchStatus
import com.uwu.animex.sync.SyncResult
import com.uwu.animex.sync.SyncStatus
import com.uwu.animex.sync.SyncWatchType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private sealed interface MalState {
    data object LoggedOut : MalState
    data object Loading : MalState
    data object NotFound : MalState
    data class Failed(val msg: String) : MalState
    data class Ready(val anime: SyncResult) : MalState
}

private val STATUS_ORDER = listOf(
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

/**
 * Bottom sheet status tontonan bergaya MAL. Kalau sudah login MAL, perubahan langsung
 * dikirim ke MAL; kalau belum, hanya status lokal (tab Bookmark) yang diubah.
 */
@Composable
fun MalEditSheet(movie: Movie, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )

    var state by remember { mutableStateOf<MalState>(if (Mal.loggedIn) MalState.Loading else MalState.LoggedOut) }
    var isNew by remember { mutableStateOf(true) }

    var status by remember { mutableStateOf(Bookmarks.status(movie.id) ?: WatchStatus.PLAN_TO_WATCH) }
    var progress by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var startDate by remember { mutableStateOf<String?>(null) }
    var endDate by remember { mutableStateOf<String?>(null) }
    var tags by remember { mutableStateOf("") }
    var priority by remember { mutableIntStateOf(0) }
    var rewatching by remember { mutableStateOf(false) }
    var rewatchCount by remember { mutableIntStateOf(0) }
    var rewatchValue by remember { mutableIntStateOf(0) }
    var notes by remember { mutableStateOf("") }

    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf<Int?>(null) } // 0 = mulai, 1 = selesai
    var confirmDelete by remember { mutableStateOf(false) }

    val total = (state as? MalState.Ready)?.anime?.totalEpisodes

    LaunchedEffect(Unit) {
        if (!Mal.loggedIn) return@LaunchedEffect
        state = try {
            val anime = Mal.resolve(movie)
            if (anime == null) {
                MalState.NotFound
            } else {
                val l = anime.myStatus
                isNew = l == null
                if (l != null) {
                    status = l.status?.toWatchStatus() ?: status
                    progress = l.watchedEpisodes ?: 0
                    score = l.score ?: 0
                    startDate = l.startDate
                    endDate = l.finishDate
                    tags = l.tags.orEmpty().joinToString(",")
                    priority = l.priority ?: 0
                    rewatching = l.isRewatching ?: false
                    rewatchCount = l.rewatchCount ?: 0
                    rewatchValue = l.rewatchValue ?: 0
                    notes = l.comments.orEmpty()
                }
                MalState.Ready(anime)
            }
        } catch (e: Exception) {
            MalState.Failed(e.message ?: "Gagal terhubung ke MAL")
        }
    }

    fun changeProgress(value: Int) {
        val p = value.coerceIn(0, total ?: Int.MAX_VALUE)
        progress = p
        if (total != null && p >= total) {
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
                            isRewatching = rewatching,
                            rewatchCount = rewatchCount,
                            rewatchValue = rewatchValue,
                            priority = priority,
                            tags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                            comments = notes,
                        ),
                    )
                }
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
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        if (pickerIndex == 0) startDate = dateOf(it) else endDate = dateOf(it)
                    }
                    picker = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picker = null }) { Text("Batal") } },
        ) { DatePicker(state = pickerState) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus dari daftar?") },
            text = {
                Text(
                    if (state is MalState.Ready && !isNew) "Entri ini akan dihapus dari daftar MyAnimeList kamu."
                    else "Status anime ini akan dihapus dari Bookmark.",
                )
            },
            confirmButton = { TextButton(onClick = { delete() }) { Text("Hapus", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
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
                TextButton(onClick = onDismiss) { Text("Batal") }
                if (saving || state == MalState.Loading) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
                Button(onClick = { apply() }, enabled = !saving && state != MalState.Loading) {
                    Text(if (isNew) "Tambah" else "Terapkan")
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                STATUS_ORDER.forEach { (option, icon) ->
                    val tooltipState = rememberTooltipState()
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                            positioning = TooltipAnchorPosition.Above,
                        ),
                        tooltip = { PlainTooltip { Text(option.label) } },
                        focusable = false,
                        state = tooltipState,
                    ) {
                        FilledIconToggleButton(
                            checked = status == option,
                            onCheckedChange = {
                                scope.launch { tooltipState.show() }
                                status = option
                                if (option == WatchStatus.COMPLETED && total != null) changeProgress(total)
                            },
                            modifier = Modifier.size(52.dp),
                            colors = IconButtonDefaults.filledIconToggleButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                checkedContainerColor = MaterialTheme.colorScheme.primary,
                                checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) { Icon(icon, contentDescription = option.label) }
                    }
                }
            }

            when (val s = state) {
                MalState.LoggedOut -> Unit
                MalState.NotFound -> Notice("Anime ini tidak ditemukan di MAL. Hanya status lokal yang akan disimpan.")
                is MalState.Failed -> Notice("MAL: ${s.msg}. Hanya status lokal yang akan disimpan.")
                MalState.Loading -> Unit
                is MalState.Ready -> {
                    s.anime.title?.let {
                        Text(
                            "MAL: $it",
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    CounterRow(
                        icon = Icons.Filled.PlayCircleOutline,
                        value = progress.toString(),
                        suffix = (total?.let { "/$it " } ?: " ") + "Episode",
                        minusEnabled = progress > 0,
                        plusEnabled = total == null || progress < total,
                        onMinus = { changeProgress(progress - 1) },
                        onPlus = { changeProgress(progress + 1) },
                    )
                    CounterRow(
                        icon = Icons.Filled.Star,
                        value = score.toString(),
                        suffix = "/10 ${SCORE_LABELS[score]}",
                        minusEnabled = score > 0,
                        plusEnabled = score < 10,
                        onMinus = { score-- },
                        onPlus = { score++ },
                    )

                    HorizontalDivider(Modifier.padding(vertical = 16.dp))

                    DateRow(Icons.Filled.CalendarToday, "Tanggal Mulai", startDate, { picker = 0 }) { startDate = null }
                    DateRow(Icons.Filled.EventAvailable, "Tanggal Selesai", endDate, { picker = 1 }) { endDate = null }

                    TextRow(Icons.AutoMirrored.Filled.Label, "Tag", tags) { tags = it }

                    CounterRow(
                        icon = Icons.Filled.PriorityHigh,
                        value = "",
                        suffix = "Prioritas: ${PRIORITY_LABELS[priority]}",
                        suffixIsPrimary = true,
                        minusEnabled = priority > 0,
                        plusEnabled = priority < 2,
                        onMinus = { priority-- },
                        onPlus = { priority++ },
                    )

                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Repeat, contentDescription = null)
                        Text("Menonton Ulang", Modifier.weight(1f).padding(start = 16.dp))
                        Switch(checked = rewatching, onCheckedChange = { rewatching = it })
                    }

                    CounterRow(
                        icon = Icons.Filled.RepeatOne,
                        value = rewatchCount.toString(),
                        suffix = "Total Tonton Ulang",
                        minusEnabled = rewatchCount > 0,
                        plusEnabled = true,
                        onMinus = { rewatchCount-- },
                        onPlus = { rewatchCount++ },
                    )
                    CounterRow(
                        icon = Icons.Filled.EventRepeat,
                        value = "",
                        suffix = "Nilai Tonton Ulang: ${REWATCH_LABELS[rewatchValue]}",
                        suffixIsPrimary = true,
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

            if (Mal.loggedIn) {
                val canDelete = (state is MalState.Ready && !isNew) || Bookmarks.status(movie.id) != null
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = canDelete && !saving) { confirmDelete = true }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val tint = MaterialTheme.colorScheme.error.copy(alpha = if (canDelete) 1f else 0.38f)
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = tint)
                    Text("Hapus", Modifier.padding(start = 16.dp), color = tint)
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
private fun CounterRow(
    icon: ImageVector,
    value: String,
    suffix: String,
    minusEnabled: Boolean,
    plusEnabled: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    suffixIsPrimary: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Row(Modifier.weight(1f).padding(start = 16.dp)) {
            if (value.isNotEmpty()) Text(value, color = MaterialTheme.colorScheme.onSurface)
            Text(
                suffix,
                color = if (suffixIsPrimary) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FilledTonalIconButton(onClick = onMinus, enabled = minusEnabled) {
            Icon(Icons.Filled.Remove, contentDescription = "Kurangi")
        }
        FilledTonalIconButton(onClick = onPlus, enabled = plusEnabled) {
            Icon(Icons.Filled.Add, contentDescription = "Tambah")
        }
    }
}

@Composable
private fun DateRow(icon: ImageVector, label: String, date: String?, onClick: () -> Unit, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Text(date ?: label, Modifier.weight(1f).padding(start = 16.dp))
        if (date != null) {
            IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Hapus tanggal")
            }
        }
    }
}

@Composable
private fun TextRow(icon: ImageVector, placeholder: String, value: String, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text(placeholder) },
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = Color.Transparent,
            ),
        )
    }
}
