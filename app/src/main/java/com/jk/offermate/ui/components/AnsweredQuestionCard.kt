package com.jk.offermate.ui.components

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jk.offermate.data.ai.AnsweredQuestion
import com.jk.offermate.ui.theme.BadgeMatchBg
import com.jk.offermate.ui.theme.BadgeMatchText
import com.jk.offermate.ui.theme.TextSecondary

/**
 * 题目卡片：题目常显，答案默认隐藏。**整卡点击可在显示/隐藏之间来回切换**，
 * 展开状态用 rememberSaveable 按题目内容保存，滚动离屏后不丢失、也不会被强制遮挡。
 *
 * @param borderColor 可选彩色边框（题库按分类着色）。
 */
@Composable
fun AnsweredQuestionCard(
    q: AnsweredQuestion,
    modifier: Modifier = Modifier,
    borderColor: Color? = null
) {
    var revealed by rememberSaveable(q.question) { mutableStateOf(false) }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { revealed = !revealed },
        shape = RoundedCornerShape(16.dp),
        border = borderColor?.let { BorderStroke(1.5.dp, it) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
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
                Surface(shape = RoundedCornerShape(8.dp), color = BadgeMatchBg) {
                    Text(
                        "相关 ${q.relevanceScore}",
                        style = MaterialTheme.typography.labelLarge,
                        color = BadgeMatchText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(q.question, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            if (revealed) {
                Text(q.answer, style = MaterialTheme.typography.bodyMedium)
                if (q.keyPoints.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "要点：" + q.keyPoints.joinToString("；"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("点按收起答案", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            } else {
                Text("点按显示答案", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
