package com.expfal.yunayu.ui.screen.apiconfig

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 「API 管理」全屏：查看/修改在线 NL 解析 API 端点、模型与密钥，并支持测试连接。
 *
 * 内容为单个可滚动 [Column]（三个输入框 + 说明 + 操作按钮 + 结果反馈），保存与测试状态
 * 均由 [ApiSettingsViewModel] 驱动，密钥框带明文/密文切换。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiSettingsScreen(
    onBack: () -> Unit,
    viewModel: ApiSettingsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("API 管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.loading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                EndpointField(uiState.baseUrlText, viewModel::onBaseUrlChange)
                ModelField(uiState.modelText, viewModel::onModelChange)
                ApiKeyField(uiState.apiKeyText, viewModel::onApiKeyChange)
                HintText()
                ActionRow(
                    saving = uiState.saving,
                    testing = uiState.testState == TestConnectionState.Testing,
                    onSave = viewModel::save,
                    onTest = viewModel::testConnection,
                )
                FeedbackSection(saved = uiState.saved, testState = uiState.testState)
            }
        }
    }
}

/** 端点 URL 输入框。 */
@Composable
private fun EndpointField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("端点 URL") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 模型名输入框。 */
@Composable
private fun ModelField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("模型名") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 密钥输入框：密文显示 + 「显示/隐藏」文字切换（material-icons-core 无可见性图标）。 */
@Composable
private fun ApiKeyField(value: String, onValueChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("密钥") },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            TextButton(onClick = { visible = !visible }) {
                Text(if (visible) "隐藏" else "显示")
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 一行说明文案：配置保存在本机，未填写时回退构建默认。 */
@Composable
private fun HintText() {
    Text(
        "配置保存在本机，未填写时回退构建默认。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 保存与测试连接操作行：保存/测试中分别禁用并显示进度圈。 */
@Composable
private fun ActionRow(
    saving: Boolean,
    testing: Boolean,
    onSave: () -> Unit,
    onTest: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = onSave, enabled = !saving, modifier = Modifier.weight(1f)) {
            ActionLabel(text = "保存", busy = saving)
        }
        OutlinedButton(onClick = onTest, enabled = !testing, modifier = Modifier.weight(1f)) {
            ActionLabel(text = "测试连接", busy = testing)
        }
    }
}

/** 按钮内容：忙碌时显示小号进度圈，否则显示文字。 */
@Composable
private fun ActionLabel(text: String, busy: Boolean) {
    if (busy) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
    } else {
        Text(text)
    }
}

/** 保存提示与连接测试结果反馈区。 */
@Composable
private fun FeedbackSection(saved: Boolean, testState: TestConnectionState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (saved) {
            Text(
                "已保存",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        when (testState) {
            TestConnectionState.Idle, TestConnectionState.Testing -> Unit
            TestConnectionState.Success -> Text(
                "连接成功",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
            is TestConnectionState.Failed -> Text(
                testState.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
