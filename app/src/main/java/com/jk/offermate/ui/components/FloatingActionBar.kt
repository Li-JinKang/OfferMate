package com.jk.offermate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 通用底部浮动操作项（返回类为即时动作 selected=false；模式类用 selected 表达激活）。
 * 图标二选一：[icon] 单色矢量（随状态着色）；[iconRes] 多色 drawable（[coloredIcon]=true 时不着色）。
 */
data class ActionBarItemSpec(
    val icon: ImageVector?,
    val label: String,
    val selected: Boolean,
    val activeColor: Color,
    @param:DrawableRes val iconRes: Int? = null,
    val coloredIcon: Boolean = false,
    val onClick: () -> Unit
)

/** 删除/已刷 的统一强调色，供各页操作项复用。 */
val ActionDeleteColor = Color(0xFFE5484D)
val ActionPracticeColor = Color(0xFF17B26A)

/**
 * 底部浮动操作胶囊：圆角、半透明、悬浮。样式与首页 Tab 胶囊一致；
 * 选中项高亮展开显示文字，未选中仅图标。供分类页/题目页等复用。
 */
@Composable
fun FloatingActionBar(
    items: List<ActionBarItemSpec>,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shadowElevation = 12.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            items.forEach { FloatingActionBarItem(it) }
        }
    }
}

@Composable
private fun FloatingActionBarItem(spec: ActionBarItemSpec) {
    val bg = if (spec.selected) spec.activeColor.copy(alpha = 0.15f) else Color.Transparent
    val content = if (spec.selected) spec.activeColor else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = spec.onClick)
            .height(40.dp)
            .padding(horizontal = if (spec.selected) 14.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        val iconTint = if (spec.coloredIcon) Color.Unspecified else content
        when {
            spec.iconRes != null -> Icon(
                painter = painterResource(spec.iconRes),
                contentDescription = spec.label,
                tint = iconTint
            )
            spec.icon != null -> Icon(
                spec.icon,
                contentDescription = spec.label,
                tint = iconTint
            )
        }
        if (spec.selected) {
            Spacer(Modifier.width(6.dp))
            Text(
                spec.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = content
            )
        }
    }
}
