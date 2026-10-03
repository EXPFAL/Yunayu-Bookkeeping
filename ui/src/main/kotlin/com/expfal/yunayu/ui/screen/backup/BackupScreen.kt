package com.expfal.yunayu.ui.screen.backup

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 「备份与恢复」全屏：SAF 导出 JSON / 导入整库替换（不含 API Key）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingExportJson by remember { mutableStateOf<String?>(null) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = !uiState.busy, onBack = onBack)

    val createDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        val json = pendingExportJson
        pendingExportJson = null
        if (uri == null || json == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("无法打开输出流")
                }.isSuccess
            }
            if (ok) {
                viewModel.onExportWritten()
            } else {
                viewModel.onExportWriteFailed("保存备份文件失败")
            }
        }
    }

    val openDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    } ?: error("无法打开文件")
                }.getOrElse { e ->
                    viewModel.onExportWriteFailed("读取备份失败：${e.message ?: "未知错误"}")
                    null
                }
            }
            if (text != null) {
                pendingImportJson = text
                showImportConfirm = true
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BackupEvent.ExportReady -> {
                    pendingExportJson = event.json
                    val name = "yunayu-backup-" +
                        LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".json"
                    createDoc.launch(name)
                }
            }
        }
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.busy) {
                    showImportConfirm = false
                    pendingImportJson = null
                }
            },
            title = { Text("确认恢复？") },
            text = {
                Text("将替换本机全部账本数据。API Key 不会随备份恢复，需在「API 设置」里重填。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val json = pendingImportJson
                        showImportConfirm = false
                        pendingImportJson = null
                        if (json != null) viewModel.onImport(json)
                    },
                    enabled = !uiState.busy,
                ) { Text("替换并恢复") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImportConfirm = false
                        pendingImportJson = null
                    },
                    enabled = !uiState.busy,
                ) { Text("取消") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("备份与恢复") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        enabled = !uiState.busy,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "导出一份 JSON 到文件管理器，换机或清数据后可整库恢复。备份不含 API Key。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = viewModel::onExport,
                enabled = !uiState.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (uiState.busy) "处理中…" else "导出备份")
            }
            OutlinedButton(
                onClick = { openDoc.launch(arrayOf("application/json", "text/*", "*/*")) },
                enabled = !uiState.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("从文件恢复")
            }
            if (uiState.busy) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            uiState.message?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
            uiState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
