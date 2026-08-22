package com.jk.offermate.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import com.jk.offermate.agent.ResumeProfile
import com.jk.offermate.data.memory.DetailKind
import com.jk.offermate.data.resume.PdfPageRenderer
import com.jk.offermate.data.settings.AiProvider
import com.jk.offermate.data.settings.AppSettings
import com.jk.offermate.di.AppContainer
import com.jk.offermate.ui.components.ZoomableImage
import com.jk.offermate.ui.theme.OutlineSoft
import com.jk.offermate.ui.theme.TextSecondary

@Composable
fun ProfileRoute(container: AppContainer, onBack: () -> Unit = {}) {
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.provideFactory(container.settingsRepository, container.mcpToolRepository)
    )
    val resumeViewModel: ResumeViewModel = viewModel(
        factory = ResumeViewModel.provideFactory(
            container.resumeRepository,
            container.resumeTextExtractor,
            container.resumeFileStore,
            container.resumeIngestor
        )
    )
    val settingsUi by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val profile by resumeViewModel.profile.collectAsStateWithLifecycle()
    val resumeFilePath by resumeViewModel.resumeFilePath.collectAsStateWithLifecycle()
    val resumeLoading by resumeViewModel.loading.collectAsStateWithLifecycle()
    val resumeError by resumeViewModel.error.collectAsStateWithLifecycle()

    val memoryViewModel: MemoryViewModel = viewModel(
        factory = MemoryViewModel.provideFactory(container.memoryStore)
    )
    // 简历导入/结构化完成后刷新记忆列表
    val structuring by resumeViewModel.structuring.collectAsStateWithLifecycle()
    LaunchedEffect(structuring) { if (!structuring) memoryViewModel.refresh() }

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(resumeViewModel::onPdfPicked)
    }

    ProfileScreen(
        settingsUi = settingsUi,
        profile = profile,
        resumeFilePath = resumeFilePath,
        resumeLoading = resumeLoading,
        resumeError = resumeError,
        onPickPdf = { pdfLauncher.launch(arrayOf("application/pdf")) },
        onSaveRawText = resumeViewModel::saveRawText,
        onConsumeError = resumeViewModel::consumeError,
        onSelectProvider = settingsViewModel::onSelectProvider,
        onEnable = settingsViewModel::onEnable,
        onThresholdChange = settingsViewModel::onThresholdChange,
        onAddMcpServer = settingsViewModel::onAddMcpServer,
        onRemoveMcpServer = settingsViewModel::onRemoveMcpServer,
        onToggleMcpServer = settingsViewModel::onToggleMcpServer,
        onRefreshMcp = settingsViewModel::onRefreshMcp,
        onBack = onBack,
        memorySection = { MemorySettingsContent(memoryViewModel) }
    )
}

@Composable
fun ProfileScreen(
    settingsUi: SettingsUiState,
    profile: ResumeProfile,
    resumeFilePath: String?,
    resumeLoading: Boolean,
    resumeError: String?,
    onPickPdf: () -> Unit,
    onSaveRawText: (String) -> Unit,
    onConsumeError: () -> Unit,
    onSelectProvider: (AiProvider) -> Unit,
    onEnable: (String, String, String) -> Unit,
    onThresholdChange: (Int) -> Unit,
    onAddMcpServer: (String, String) -> Unit = { _, _ -> },
    onRemoveMcpServer: (String) -> Unit = {},
    onToggleMcpServer: (String, Boolean) -> Unit = { _, _ -> },
    onRefreshMcp: () -> Unit = {},
    onBack: () -> Unit = {},
    memorySection: @Composable ColumnScope.() -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("‹ 返回") }
            Spacer(Modifier.size(8.dp))
            Text("设置", style = MaterialTheme.typography.titleLarge)
        }

        val resumeSubtitle = if (profile.rawText.isBlank()) "未上传" else "已上传"
        ExpandableCard(title = "我的简历", subtitle = resumeSubtitle) {
            ResumeContent(profile, resumeFilePath, resumeLoading, resumeError, onPickPdf, onSaveRawText, onConsumeError)
        }

        val activeLabel = AiProvider.from(settingsUi.activeProviderId).label
        ExpandableCard(title = "AI 设置", subtitle = "启用：$activeLabel") {
            AiSettingsContent(settingsUi, onSelectProvider, onEnable, onThresholdChange)
        }

        memorySection()

        val enabledCount = settingsUi.mcpServers.count { it.enabled }
        ExpandableCard(title = "MCP 工具服务器", subtitle = "$enabledCount 台启用 · ${settingsUi.mcpToolCount} 个工具") {
            McpSettingsContent(settingsUi, onAddMcpServer, onRemoveMcpServer, onToggleMcpServer, onRefreshMcp)
        }

        Text(
            "答案由 AI 生成，仅供参考。各服务商 Key 分别加密存储于本机。" +
                "配置 MCP 服务器后，AI 可自主调用其工具（连同读简历/查题库等本地工具）。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun McpSettingsContent(
    uiState: SettingsUiState,
    onAdd: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onRefresh: () -> Unit
) {
    Text(
        "MCP（Model Context Protocol）服务器把外部工具暴露给 AI。填写 Streamable HTTP 端点即可，AI 会在对话/分析时按需调用。",
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary
    )

    // 已配置的服务器列表
    uiState.mcpServers.forEach { server ->
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(server.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        server.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }
                Switch(checked = server.enabled, onCheckedChange = { onToggle(server.name, it) })
                Spacer(Modifier.size(4.dp))
                TextButton(onClick = { onRemove(server.name) }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    // 新增服务器
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    OutlinedTextField(name, { name = it }, label = { Text("服务器名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(url, { url = it }, label = { Text("端点 URL（https://…/mcp）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = {
                onAdd(name, url)
                name = ""; url = ""
            },
            enabled = name.isNotBlank() && url.isNotBlank()
        ) { Text("添加") }
        OutlinedButton(onClick = onRefresh, enabled = !uiState.mcpRefreshing) {
            Text(if (uiState.mcpRefreshing) "刷新中…" else "重连刷新")
        }
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
    resumeFilePath: String?,
    loading: Boolean,
    error: String?,
    onPickPdf: () -> Unit,
    onSaveRawText: (String) -> Unit,
    onConsumeError: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(
            if (resumeFilePath == null) "上传 PDF 简历，App 会自动识别内容" else "简历已上传",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(onClick = onPickPdf, enabled = !loading) {
            Text(if (loading) "解析中…" else if (resumeFilePath == null) "导入 PDF" else "重新导入")
        }
    }

    error?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        LaunchedEffect(it) {
            kotlinx.coroutines.delay(4000)
            onConsumeError()
        }
    }

    // 简历预览（PDF 渲染为图片，可缩放）
    if (resumeFilePath != null) {
        ResumePreview(resumeFilePath)
    }

    // 识别出的内容（可折叠、可编辑）
    if (profile.rawText.isNotBlank()) {
        RecognizedTextSection(profile.rawText, onSaveRawText)
    }
}

@Composable
private fun ResumePreview(path: String) {
    val bitmaps by produceState(initialValue = emptyList<Bitmap>(), path) {
        value = PdfPageRenderer.render(path)
    }
    if (bitmaps.isEmpty()) {
        Text("预览加载中…", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        return
    }
    Text("简历预览（双指缩放）", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        bitmaps.forEach { bmp ->
            ZoomableImage(image = bmp.asImageBitmap())
        }
    }
}

@Composable
private fun RecognizedTextSection(rawText: String, onSave: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var edited by remember(rawText) { mutableStateOf(rawText) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
    ) {
        Text("识别出的内容", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(if (expanded) "收起 ▲" else "展开 ▼", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
    if (expanded) {
        OutlinedTextField(
            value = edited,
            onValueChange = { edited = it },
            label = { Text("可校正识别文本") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { onSave(edited) }, enabled = edited != rawText) { Text("保存") }
        }
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

@Composable
private fun MemorySettingsContent(viewModel: MemoryViewModel) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val global by viewModel.global.collectAsStateWithLifecycle()

    ExpandableCard(title = "简历记忆", subtitle = "${profiles.size} 份") {
        Text(
            "导入简历后，AI 会将其结构化为分层记忆（按求职方向分份），并在分析/对话时按需调用。" +
                "你可以在此查看、编辑或删除。",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        if (profiles.isEmpty() && global.isBlank()) {
            Text("暂无记忆，导入简历后自动生成。", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            return@ExpandableCard
        }

        if (global.isNotBlank()) {
            EditableFileBlock(
                label = "通用事实（跨方向共享）",
                content = global,
                onSave = viewModel::saveGlobal
            )
        }

        profiles.forEach { detail ->
            MemoryProfileCard(detail, viewModel)
        }
    }
}

@Composable
private fun MemoryProfileCard(detail: MemoryViewModel.ProfileDetail, viewModel: MemoryViewModel) {
    val id = detail.entry.id
    val subtitle = detail.entry.targetRole.ifBlank { "${detail.projects.size + detail.experiences.size} 个文件" }
    ExpandableCard(title = detail.entry.name.ifBlank { id }, subtitle = subtitle) {
        if (detail.entry.summary.isNotBlank()) {
            Text(detail.entry.summary, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }

        EditableFileBlock(
            label = "概览（profile.md）",
            content = detail.overview,
            onSave = { viewModel.saveOverview(id, it) }
        )

        detail.projects.forEach { item ->
            EditableFileBlock(
                label = "项目：${item.id}",
                content = item.content,
                onSave = { viewModel.saveDetail(id, DetailKind.PROJECT, item.id, it) },
                onDelete = { viewModel.deleteDetail(id, DetailKind.PROJECT, item.id) }
            )
        }
        detail.experiences.forEach { item ->
            EditableFileBlock(
                label = "经历：${item.id}",
                content = item.content,
                onSave = { viewModel.saveDetail(id, DetailKind.EXPERIENCE, item.id, it) },
                onDelete = { viewModel.deleteDetail(id, DetailKind.EXPERIENCE, item.id) }
            )
        }

        var confirmDelete by remember(id) { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            if (confirmDelete) {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
                Spacer(Modifier.size(4.dp))
                Button(onClick = { viewModel.deleteProfile(id) }) {
                    Text("确认删除整份记忆", color = MaterialTheme.colorScheme.onError)
                }
            } else {
                TextButton(onClick = { confirmDelete = true }) {
                    Text("删除此记忆集", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** 可折叠、可编辑、可选删除的单个记忆文件块。 */
@Composable
private fun EditableFileBlock(
    label: String,
    content: String,
    onSave: (String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var expanded by rememberSaveable(label) { mutableStateOf(false) }
    var edited by remember(content) { mutableStateOf(content) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(if (expanded) "收起 ▲" else "展开 ▼", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
    if (expanded) {
        OutlinedTextField(
            value = edited,
            onValueChange = { edited = it },
            minLines = 4,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            onDelete?.let {
                TextButton(onClick = it) { Text("删除", color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.size(4.dp))
            }
            Button(onClick = { onSave(edited) }, enabled = edited != content) { Text("保存") }
        }
    }
}
