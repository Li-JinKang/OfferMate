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
    val settingsUi by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val profile by resumeViewModel.profile.collectAsStateWithLifecycle()
    val pdfText by resumeViewModel.pdfText.collectAsStateWithLifecycle()
    val pdfLoading by resumeViewModel.pdfLoading.collectAsStateWithLifecycle()

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(resumeViewModel::onPdfPicked)
    }

    ProfileScreen(
        settingsUi = settingsUi,
        profile = profile,
        pdfText = pdfText,
        pdfLoading = pdfLoading,
        onPickPdf = { pdfLauncher.launch(arrayOf("application/pdf")) },
        onPdfConsumed = resumeViewModel::consumePdfText,
        onSelectProvider = settingsViewModel::onSelectProvider,
        onEnable = settingsViewModel::onEnable,
        onThresholdChange = settingsViewModel::onThresholdChange,
        onSaveResume = resumeViewModel::save
    )
}

@Composable
fun ProfileScreen(
    settingsUi: SettingsUiState,
    profile: ResumeProfile,
    pdfText: String?,
    pdfLoading: Boolean,
    onPickPdf: () -> Unit,
    onPdfConsumed: () -> Unit,
    onSelectProvider: (AiProvider) -> Unit,
    onEnable: (String, String, String) -> Unit,
    onThresholdChange: (Int) -> Unit,
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
            ResumeContent(profile, pdfText, pdfLoading, onPickPdf, onPdfConsumed, onSaveResume)
        }

        val activeLabel = AiProvider.from(settingsUi.activeProviderId).label
        ExpandableCard(title = "AI 设置", subtitle = "启用：$activeLabel") {
            AiSettingsContent(settingsUi, onSelectProvider, onEnable, onThresholdChange)
        }

        Text(
            "答案由 AI 生成，仅供参考。各服务商 Key 分别加密存储于本机。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExpandableCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp)
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

    OutlinedTextField(role, { role = it }, label = { Text("目标岗位（如：Android 开发）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(skills, { skills = it }, label = { Text("技能（逗号分隔）") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(raw, { raw = it }, label = { Text("简历文本（可手填或导入 PDF）") }, minLines = 3, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onPickPdf, enabled = !pdfLoading) { Text(if (pdfLoading) "解析中…" else "导入 PDF") }
        Button(onClick = { onSave(role, skills, raw) }) { Text("保存简历") }
    }
}

@Composable
private fun AiSettingsContent(
    uiState: SettingsUiState,
    onSelectProvider: (AiProvider) -> Unit,
    onEnable: (String, String, String) -> Unit,
    onThresholdChange: (Int) -> Unit
) {
    Text("服务商（✓ 为当前启用）", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        AiProvider.entries.forEach { p ->
            val selected = uiState.selectedProviderId == p.id
            val active = uiState.activeProviderId == p.id
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                border = if (selected) null else BorderStroke(1.dp, OutlineSoft),
                modifier = Modifier.clickable { onSelectProvider(p) }
            ) {
                Text(
                    (if (active) "✓ " else "") + p.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
            Spacer(Modifier.size(8.dp))
        }
    }

    // 选中服务商的可编辑配置（切换服务商时重置）
    val config = uiState.config
    var url by remember(config) { mutableStateOf(config.baseUrl) }
    var keyInput by remember(config) { mutableStateOf(config.apiKey) }
    var model by remember(config) { mutableStateOf(config.model) }
    var keyVisible by remember { mutableStateOf(false) }

    OutlinedTextField(url, { url = it }, label = { Text("接口地址") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(
        value = keyInput,
        onValueChange = { keyInput = it },
        label = { Text("API Key") },
        singleLine = true,
        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { TextButton(onClick = { keyVisible = !keyVisible }) { Text(if (keyVisible) "隐藏" else "显示") } },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(model, { model = it }, label = { Text("模型") }, singleLine = true, modifier = Modifier.fillMaxWidth())

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        val isActiveSelected = uiState.selectedProviderId == uiState.activeProviderId
        Text(
            if (isActiveSelected) "当前启用中" else "未启用",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Button(onClick = { onEnable(keyInput, model, url) }) {
            Text(if (isActiveSelected) "更新并启用" else "启用模型")
        }
    }

    var threshold by remember(uiState.relevanceThreshold) { mutableFloatStateOf(uiState.relevanceThreshold.toFloat()) }
    Text("相关性阈值：${threshold.toInt()}", style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = threshold,
        onValueChange = { threshold = it },
        valueRange = AppSettings.MIN_THRESHOLD.toFloat()..AppSettings.MAX_THRESHOLD.toFloat(),
        onValueChangeFinished = { onThresholdChange(threshold.toInt()) }
    )
}
