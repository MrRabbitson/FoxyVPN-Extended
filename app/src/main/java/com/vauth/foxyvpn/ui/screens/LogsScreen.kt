package com.vauth.foxyvpn.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import com.vauth.foxyvpn.ui.components.GlassButton
import com.vauth.foxyvpn.ui.components.GlassSurface
import com.vauth.foxyvpn.ui.components.GlassTopBar
import androidx.compose.ui.res.painterResource
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vauth.foxyvpn.R
import com.vauth.foxyvpn.data.AppLogger
import com.vauth.foxyvpn.data.LogEntry
import com.vauth.foxyvpn.data.LogLevel
import com.vauth.foxyvpn.ui.LogFilter
import com.vauth.foxyvpn.ui.LogsUserMessage
import com.vauth.foxyvpn.ui.LogsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun LogFilter.label(): String =
    when (this) {
        LogFilter.ALL -> stringResource(R.string.logs_filter_all)
        LogFilter.INFO -> stringResource(R.string.logs_filter_info)
        LogFilter.PROBLEMS -> stringResource(R.string.logs_filter_problems)
    }

@Composable
fun LogsScreen(
    onBack: () -> Unit,
    viewModel: LogsViewModel = viewModel(),
) {
    val entries by AppLogger.entries.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    val chooserTitle = stringResource(R.string.logs_share_chooser_title)

    LaunchedEffect(state.shareFileIntent) {
        val intent = state.shareFileIntent ?: return@LaunchedEffect
        context.startActivity(intent)
        viewModel.consumeShareIntent()
    }

    LaunchedEffect(state.userMessage) {
        val msg = state.userMessage ?: return@LaunchedEffect
        val text =
            when (msg) {
                is LogsUserMessage.StringRes -> context.getString(msg.resId)
                is LogsUserMessage.PlainText -> msg.text
            }
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        viewModel.consumeUserMessage()
    }

    val countsByFilter: Map<LogFilter, Int> =
        remember(entries) {
            LogFilter.entries.associateWith { option ->
                entries.count { it.level in option.levels }
            }
        }
    val visible: List<LogEntry> =
        remember(entries, state.filter) {
            if (state.filter == LogFilter.ALL) {
                entries
            } else {
                entries.filter { it.level in state.filter.levels }
            }
        }

    Scaffold(
        topBar = {
            GlassTopBar(
                title =
                    if (visible.size == entries.size) {
                        stringResource(R.string.logs_title_all, entries.size)
                    } else {
                        stringResource(R.string.logs_title_filtered, visible.size, entries.size)
                    },
                onBack = onBack,
            ) {
                BarAction(
                    icon = R.drawable.lucide_share_2,
                    label = stringResource(R.string.logs_action_share),
                    enabled = !state.isExporting && entries.isNotEmpty(),
                    onClick = { viewModel.exportLogs(context, chooserTitle) },
                )
                BarAction(
                    icon = R.drawable.lucide_copy,
                    label = stringResource(R.string.logs_action_copy),
                    enabled = !state.isExporting && entries.isNotEmpty(),
                    onClick = { viewModel.copyLogs(context) },
                )
                BarAction(
                    icon = R.drawable.lucide_trash_2,
                    label = stringResource(R.string.logs_action_clear),
                    enabled = entries.isNotEmpty(),
                    onClick = { viewModel.clearLogs() },
                )
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (option in LogFilter.entries) {
                    val count = countsByFilter[option] ?: 0
                    val active = state.filter == option
                    GlassButton(
                        onClick = { viewModel.setFilter(option) },
                        tint =
                            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
                            else Color.Unspecified,
                    ) {
                        Text("${option.label()} ($count)")
                    }
                }
            }

            if (visible.isEmpty()) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    val emptyText =
                        when {
                            entries.isEmpty() -> stringResource(R.string.logs_empty_none)
                            state.filter == LogFilter.PROBLEMS ->
                                stringResource(R.string.logs_empty_problems)
                            else -> stringResource(R.string.logs_empty_filter)
                        }
                    Text(
                        text = emptyText,
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                GlassSurface(
                    Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp)
                ) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(visible.asReversed(), key = { it.id }) { entry ->
                        val color =
                            when (entry.level) {
                                LogLevel.ERROR -> MaterialTheme.colorScheme.error
                                LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
                                LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
                            }
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "${timeFormat.format(Date(entry.timestampMillis))} ${entry.level.name} [${entry.tag}]",
                                style = MaterialTheme.typography.labelSmall,
                                color = color,
                            )
                            Text(entry.message, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun BarAction(icon: Int, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp)
            .clip(CircleShape)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size(21.dp))
    }
}
