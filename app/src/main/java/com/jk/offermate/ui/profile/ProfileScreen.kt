package com.jk.offermate.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jk.offermate.data.settings.AppSettings
import com.jk.offermate.di.AppContainer

@Composable
fun ProfileRoute(container: AppContainer) {
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.provideFactory(container.settingsRepository)
    )
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    ProfileScreen(
        settings = settings,
        onSaveApiKey = viewModel::updateApiKey,
        onSaveModel = viewModel::updateModel,
        onThresholdChange = viewModel::updateThreshold
    )
}

@Composable
fun ProfileScreen(
    settings: AppSettings,
    onSaveApiKey: (String) -> Unit,
    onSaveModel: (String) -> Unit,
    onThresholdChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("我的", style = MaterialTheme.typography.titleLarge)

        DeepSeekSettingsCard(
            settings = settings,
            onSaveApiKey = onSaveApiKey,
            onSaveModel = onSaveModel,
            onThresholdChange = onThresholdChange
        )

        Text(
            "答案由 AI 生成，仅供参考，可能存在错误。API Key 仅加密存储于本机。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DeepSeekSettingsCard(
    settings: AppSettings,
    onSaveApiKey: (String) -> Unit,
    onSaveModel: (String) -> Unit,
    onThresholdChange: (Int) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("DeepSeek 设置（BYOK）", style = MaterialTheme.typography.titleMedium)

            // API Key
            var keyInput by remember(settings.deepSeekApiKey) { mutableStateOf(settings.deepSeekApiKey) }
            var keyVisible by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { keyVisible = !keyVisible }) {
                        Text(if (keyVisible) "隐藏" else "显示")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (settings.isDeepSeekConfigured) "状态：已配置" else "状态：未配置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { onSaveApiKey(keyInput) }) { Text("保存 Key") }
            }

            // 模型
            var modelInput by remember(settings.model) { mutableStateOf(settings.model) }
            OutlinedTextField(
                value = modelInput,
                onValueChange = { modelInput = it },
                label = { Text("模型") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { onSaveModel(modelInput) }) { Text("保存模型") }
            }

            // 相关性阈值
            var threshold by remember(settings.relevanceThreshold) {
                mutableFloatStateOf(settings.relevanceThreshold.toFloat())
            }
            Text("相关性阈值：${threshold.toInt()}", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = threshold,
                onValueChange = { threshold = it },
                valueRange = AppSettings.MIN_THRESHOLD.toFloat()..AppSettings.MAX_THRESHOLD.toFloat(),
                onValueChangeFinished = { onThresholdChange(threshold.toInt()) }
            )
        }
    }
}
