package com.jk.offermate.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jk.offermate.data.ai.AnsweredQuestion
import com.jk.offermate.ui.theme.BadgeMatchBg
import com.jk.offermate.ui.theme.BadgeMatchText
import com.jk.offermate.ui.theme.TextSecondary

/**
 * 题目卡片：题目常显，答案默认隐藏，点击"显示答案"才展开。刷题与题目页共用。
 */
@Composable
fun AnsweredQuestionCard(q: AnsweredQuestion, modifier: Modifier = Modifier) {
    var revealed by remember { mutableStateOf(false) }
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
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
            } else {
                OutlinedButton(onClick = { revealed = true }) { Text("显示答案") }
            }
        }
    }
}
