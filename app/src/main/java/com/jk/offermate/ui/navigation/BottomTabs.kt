package com.jk.offermate.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy

/**
 * 底部 Tab 胶囊：圆角、可配置底色（全局栏用半透明浮层，AI 对话 dock 用不透明）。
 * 选中项展开为带文字的小药丸，未选中只显示图标。
 */
@Composable
fun TabPill(
    currentDestination: NavDestination?,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
    shadowElevation: Dp = 12.dp,
    barHeight: Dp = 56.dp
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = shadowElevation,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Screen.bottomItems.forEach { screen ->
                val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                TabPillItem(
                    screen = screen,
                    selected = selected,
                    onClick = { onNavigate(screen) }
                )
            }
        }
    }
}

@Composable
private fun TabPillItem(
    screen: Screen,
    selected: Boolean,
    onClick: () -> Unit
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "navItemColor"
    )
    val pillColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "navItemPill"
    )
    val horizontalPadding by animateDpAsState(
        targetValue = if (selected) 16.dp else 12.dp,
        label = "navItemPadding"
    )
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(pillColor)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .height(40.dp)
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = screen.icon, contentDescription = screen.label, tint = contentColor)
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = screen.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}
