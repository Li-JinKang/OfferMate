package com.jk.offermate.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.jk.offermate.agent.AnsweredQuestion
import com.jk.offermate.ui.theme.BadgeHotBg
import com.jk.offermate.ui.theme.BadgeHotText
import com.jk.offermate.ui.theme.TextSecondary

/**
 * 题目卡片：题目常显，答案默认隐藏，整卡点击可来回显示/隐藏。展开状态用 rememberSaveable 按题目保存。
 *
 * @param borderColor 可选彩色边框（题库按分类着色）。
 */
@Composable
fun AnsweredQuestionCard(
    q: AnsweredQuestion,
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    onTogglePracticed: (() -> Unit)? = null,
    onFollowUp: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    /** 置 true 时播放一次“放大→复原”提示动画（用于从搜索结果定位到本卡片）。 */
    pulse: Boolean = false
) {
    var revealed by rememberSaveable(q.question) { mutableStateOf(false) }

    // 提示动画：缩放 1 → 1.06 → 1，仅在 pulse 首次为 true 时播放一次
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pulse) {
        if (pulse) {
            scale.animateTo(1.06f, tween(200))
            scale.animateTo(1f, tween(340))
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clickable { revealed = !revealed },
        shape = RoundedCornerShape(16.dp),
        border = borderColor?.let { BorderStroke(1.5.dp, it) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            if (q.tags.isNotEmpty() || q.practiced) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (q.tags.isNotEmpty()) {
                        Text(
                            q.tags.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (q.practiced) {
                        Surface(shape = RoundedCornerShape(8.dp), color = BadgeHotBg) {
                            Text(
                                "已刷 ✓",
                                style = MaterialTheme.typography.labelLarge,
                                color = BadgeHotText,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Text(q.question, style = MaterialTheme.typography.titleMedium)

            if (revealed) {
                Spacer(Modifier.height(12.dp))
                MarkdownText(q.answer, modifier = Modifier.fillMaxWidth())
                if (q.keyPoints.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "要点：" + q.keyPoints.joinToString("；"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            if (onTogglePracticed != null || onFollowUp != null || onDelete != null) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    onTogglePracticed?.let { toggle ->
                        TextButton(onClick = toggle) {
                            Text(if (q.practiced) "取消已刷" else "标记为已刷")
                        }
                    }
                    onFollowUp?.let { followUp ->
                        TextButton(onClick = followUp) { Text("追问") }
                    }
                    onDelete?.let { delete ->
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = delete) { Text("删除") }
                    }
                }
            }
        }
    }
}
