package dev.aakrit.fastlog

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.aakrit.fastlog.data.FastingRepository
import dev.aakrit.fastlog.notify.FastNotifier
import dev.aakrit.fastlog.ui.ChartScreen
import dev.aakrit.fastlog.ui.CompletionScreen
import dev.aakrit.fastlog.ui.FastLogTheme
import dev.aakrit.fastlog.ui.FastingViewModel
import dev.aakrit.fastlog.ui.HistoryScreen
import dev.aakrit.fastlog.ui.TimerScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    /** Set when the activity is relaunched by the notification's End fast action. */
    private val endFastRequest = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        FastNotifier.ensureChannels(this)
        handleIntent(intent)
        setContent {
            FastLogTheme { FastLogApp(endFastRequest = endFastRequest) }
        }
    }

    // singleTask, so a second tap on the action arrives here rather than in onCreate.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_END_FAST) {
            endFastRequest.value = true
            // Clear it so a configuration change cannot replay the request.
            intent.action = null
        }
    }

    companion object {
        const val ACTION_END_FAST = "dev.aakrit.fastlog.END_FAST"
    }
}

private enum class Tab(val label: String) {
    Timer("Timer"),
    Chart("Chart"),
    History("History"),
}

private sealed interface Dialog {
    data class ImportConfirm(val uri: Uri, val summary: FastingRepository.ImportSummary) : Dialog
    data class Message(val title: String, val body: String) : Dialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FastLogApp(
    endFastRequest: MutableStateFlow<Boolean>,
    viewModel: FastingViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val completed by viewModel.justCompleted.collectAsStateWithLifecycle()
    val endRequested by endFastRequest.collectAsStateWithLifecycle()

    // Arriving from the notification's End fast action: finish the fast here so
    // the user lands on the summary instead of a shade that just disappears.
    LaunchedEffect(endRequested, state.active) {
        if (endRequested && state.active != null) {
            viewModel.endFast()
            endFastRequest.value = false
        } else if (endRequested && state.active == null) {
            endFastRequest.value = false
        }
    }
    var tab by rememberSaveable { mutableStateOf(Tab.Timer) }
    var menuOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<Dialog?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Asked for only when a fast actually starts, not on cold launch.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshNotification() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(viewModel.exportCsv().toByteArray())
                    } ?: error("could not open destination")
                }.isSuccess
            }
            dialog = if (ok) {
                Dialog.Message(
                    "Exported",
                    "${state.history.size} fasts written. The file opens in Excel or " +
                        "any spreadsheet app, and can be imported back here later."
                )
            } else {
                Dialog.Message("Export failed", "Could not write to that location. Try another folder.")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val csv = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().decodeToString()
                    }
                }.getOrNull()
            }
            dialog = if (csv == null) {
                Dialog.Message("Import failed", "Could not read that file.")
            } else {
                Dialog.ImportConfirm(uri, viewModel.previewImport(csv))
            }
        }
    }

    // The completion summary takes over the whole screen while it is showing.
    completed?.let { record ->
        CompletionScreen(
            record = record,
            historyCount = state.history.size,
            currentStreakDays = state.stats.currentStreakDays,
            onDone = { viewModel.acknowledgeCompletion(); tab = Tab.Timer },
            onSeeHistory = { viewModel.acknowledgeCompletion(); tab = Tab.History },
            modifier = Modifier.safeDrawingPadding(),
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("FastLog", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Text("⋮", style = MaterialTheme.typography.titleLarge)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Export CSV") },
                            onClick = {
                                menuOpen = false
                                exportLauncher.launch(defaultFileName())
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Import CSV") },
                            onClick = {
                                menuOpen = false
                                importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*"))
                            },
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        label = { Text(entry.label) },
                        icon = {},
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.Timer -> TimerScreen(
                    state = state,
                    onSelectProtocol = viewModel::selectProtocol,
                    onStart = {
                        viewModel.startFast()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !FastNotifier.hasPermission(context)
                        ) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onStartAt = { startMs ->
                        viewModel.startFast(startMs)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !FastNotifier.hasPermission(context)
                        ) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onEnd = { endMs -> viewModel.endFast(endMs) },
                    onEditStart = viewModel::updateStartTime,
                    onChangePlan = viewModel::changePlan,
                    onCancel = viewModel::cancelFast,
                )

                Tab.Chart -> ChartScreen(state = state)

                Tab.History -> HistoryScreen(
                    state = state,
                    onDelete = viewModel::deleteRecord,
                    onEdit = viewModel::updateRecord,
                )
            }
        }
    }

    when (val d = dialog) {
        is Dialog.ImportConfirm -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Import this backup?") },
            text = {
                Text(
                    buildString {
                        append("Adds ${d.summary.added} new ")
                        append(if (d.summary.added == 1) "fast" else "fasts")
                        append(".")
                        if (d.summary.duplicates > 0) {
                            append("\n\nSkips ${d.summary.duplicates} already in your history.")
                        }
                        if (d.summary.skipped > 0) {
                            append("\n\n${d.summary.skipped} rows could not be read and will be ignored.")
                        }
                        append("\n\nNothing already saved will be changed or removed.")
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = d.uri
                    dialog = null
                    scope.launch {
                        val csv = withContext(Dispatchers.IO) {
                            runCatching {
                                context.contentResolver.openInputStream(uri)?.use {
                                    it.readBytes().decodeToString()
                                }
                            }.getOrNull()
                        }
                        if (csv == null) {
                            dialog = Dialog.Message("Import failed", "Could not read that file.")
                        } else {
                            viewModel.importCsv(csv) { result ->
                                dialog = Dialog.Message(
                                    "Imported",
                                    "${result.added} fasts added to your history."
                                )
                            }
                        }
                    }
                }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )

        is Dialog.Message -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(d.title) },
            text = { Text(d.body) },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("OK") } },
        )

        null -> Unit
    }
}

private fun defaultFileName(): String = "fastlog-${LocalDate.now()}.csv"
