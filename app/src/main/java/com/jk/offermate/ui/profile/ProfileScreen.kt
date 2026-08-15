package com.jk.offermate.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jk.offermate.data.ai.ResumeProfile
import com.jk.offermate.data.settings.AiProvider
import com.jk.offermate.data.settings.AppSettings
import com.jk.offermate.di.AppContainer
import com.jk.offermate.ui.theme.OutlineSoft

@Composable
fun ProfileRoute(container: AppContainer) {
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.provideFactory(container.settingsRepository)
    )
    val resumeViewModel: ResumeViewModel = viewModel(
        factory = ResumeViewModel.provideFactory(container.resumeRepository, container.resumeTextExtractor)
    )
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val profile by resumeViewModel.profile.collectAsStateWithLifecycle()
    val pdfText by resumeViewModel.pdfText.collectAsStateWithLifecycle()
    val pdfLoading by resumeViewModel.pdfLoading.collectAsStateWithLifecycle()

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(resumeViewModel::onPdfPicked)
    }

    ProfileScreen(
        settings = settings,
        profile = profile,
        pdfText = pdfText,
        pdfLoading = pdfLoading,
        onPickPdf = { pdfLauncher.launch(arrayOf("application/pdf")) },
        onPdfConsumed = resumeViewModel::consumePdfText,
        onSaveApiKey = settingsViewModel::updateApiKey,
        onSaveModel = settingsViewModel::updateModel,
        onThresholdChange = settingsViewModel::updateThreshold,
        onSelectProvider = settingsViewModel::updateProvider,
        onSaveBaseUrl = settingsViewModel::updateBaseUrl,
        onSaveResume = resumeViewModel::save
    )
}

@Composable
fun ProfileScreen(
    settings: AppSettings,
    profile: ResumeProfile,
    pdfText: String?,
    pdfLoading: Boolean,
    onPickPdf: () -> Unit,
    onPdfConsumed: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    onSaveModel: (String) -> Unit,
    onThresholdChange: (Int) -> Unit,
    onSelectProvider: (AiProvider) -> Unit,
    onSaveBaseUrl: (String) -> Unit,
    onSaveResume: (String, String, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("我的", style = MaterialTheme.typography.titleLarge)

        val resumeSubtitle = if (profile.targetRole.isBlank()) "未设置" else profile.targetRole
        ExpandableCard(title = "我的简历", subtitle = resumeSubtitle) {
            ResumeContent(
                profile = profile,
                pdfText = pdfText,
                pdfLoading = pdfLoading,
                onPickPdf = onPickPdf,
                onPdfConsumed = onPdfConsumed,
                onSave = onSaveResume
            )
        }

        val settingsSubtitle = if (settings.isDeepSeekConfigured) "已配置" else "未配置"
        ExpandableCard(title = "AI 设置（DeepSeek）", subtitle = settingsSubtitle) {
            AiSettingsContent(
                settings = settings,
                onSaveApiKey = onSaveApiKey,
                onSaveModel = onSaveModel,
                onThresholdChange = onThresholdChange,
                onSelectProvider = onSelectProvider,
                onSaveBaseUrl = onSaveBaseUrl
            )
        }

        Text(
            "答案由 AI 生成，仅供参考，可能存在错误。API Key 仅加密存储于本机。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExpandableCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (expanded) "  ▲" else "  ▼", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expanded) {
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun ResumeContent(
    profile: ResumeProfile,
    pdfText: String?,
    pdfLoading: Boolean,
    onPickPdf: () -> Unit,
    onPdfConsumed: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var role by remember(profile.targetRole) { mutableStateOf(profile.targetRole) }
    var skills by remember(profile.skills) { mutableStateOf(profile.skills.joinToString("，")) }
    var raw by remember(profile.rawText) { mutableStateOf(profile.rawText) }

    LaunchedEffect(pdfText) {
        val t = pdfText
        if (!t.isNullOrBlank()) {
            raw = t
            onPdfConsumed()
        }
    }

    OutlinedTextField(
        value = role,
        onValueChange = { role = it },
        label = { Text("目标岗位（如：Android 开发）") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = skills,
        onValueChange = { skills = it },
        label = { Text("技能（逗号分隔）") },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = raw,
        onValueChange = { raw = it },
        label = { Text("简历文本（可手填或导入 PDF）") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth()
    )
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onPickPdf, enabled = !pdfLoading) {
            Text(if (pdfLoading) "解析中…" else "导入 PDF")
        }
        Button(onClick = { onSave(role, skills, raw) }) { Text("保存简历") }
    }
}

@Composable
private fun AiSettingsContent(
    settings: AppSettings,
    onSaveApiKey: (String) -> Unit,
    onSaveModel: (String) -> Unit,
    onThresholdChange: (Int) -> Unit,
    onSelectProvider: (AiProvider) -> Unit,
    onSaveBaseUrl: (String) -> Unit
) {
    // 服务商选择
    Text("服务商", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        AiProvider.entries.forEach { p ->
            val selected = settings.provider == p
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                border = if (selected) null else BorderStroke(1.dp, OutlineSoft),
                modifier = Modifier.clickable { onSelectProvider(p) }
            ) {
                Text(
                    p.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
            Spacer(Modifier.size(8.dp))
        }
    }

    // 接口地址：自定义可编辑，其余显示默认（只读）
    var urlInput by remember(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
    OutlinedTextField(
        value = urlInput,
        onValueChange = { urlInput = it },
        label = { Text("接口地址") },
        singleLine = true,
        enabled = settings.provider.isCustom,
        modifier = Modifier.fillMaxWidth()
    )
    if (settings.provider.isCustom) {
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { onSaveBaseUrl(urlInput) }) { Text("保存地址") }
        }
    }

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
