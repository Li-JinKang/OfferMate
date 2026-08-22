package com.jk.offermate.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
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
        val iconTint = if (screen.coloredIcon) Color.Unspecified else contentColor
        when {
            screen.iconRes != null -> Icon(
                painter = painterResource(screen.iconRes),
                contentDescription = screen.label,
                tint = iconTint
            )
            screen.icon != null -> Icon(
                imageVector = screen.icon,
                contentDescription = screen.label,
                tint = iconTint
            )
        }
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

/** dock 中两个胶囊的统一高度与探头露出高度（供 AI 对话页的紧凑输入胶囊复用）。 */
internal val DockPillHeight = 56.dp
internal val DockPeek = 16.dp

/**
 * 常驻底部 dock：作为唯一的 Tab 胶囊实例存在（Home/Quiz/AI 对话 三个 Tab 共用），
 * 因此切页时它不重建、不淡入淡出，从根源上消除“两套底栏交叉过渡”的割裂感。
 *
 * - [inputContent] 为 null（Home/Quiz）：仅渲染悬浮 Tab 胶囊，定位与原全局底栏一致。
 * - [inputContent] 非空（AI 对话）：输入胶囊叠在 Tab 胶囊之上，保留原有的探头/前后切换动画。
 */
@Composable
fun BottomDock(
    currentDestination: NavDestination?,
    onNavigate: (Screen) -> Unit,
    inputContent: (@Composable () -> Unit)?,
    inputInFront: Boolean,
    onInputInFrontChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** dock 整体透明度提供器：AI 对话页随抽屉手势进度淡入淡出（1=显示，0=隐藏）。 */
    contentAlpha: () -> Float = { 1f }
) {
    val tabPill: @Composable () -> Unit = {
        TabPill(currentDestination = currentDestination, onNavigate = onNavigate)
    }

    if (inputContent == null) {
        // 仅 Tab：与原 FloatingBottomBar 完全一致的悬浮定位（左右 24dp、上下 12dp、居中）
        Box(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                // 此分支（Home/Quiz）不做淡出，alpha 恒为 1，无需离屏合成。
                .graphicsLayer { alpha = contentAlpha() },
            contentAlignment = Alignment.Center
        ) {
            tabPill()
        }
        return
    }

    // 堆叠 dock：输入胶囊与 Tab 胶囊等高，前者在上、后者在下露出一条边（[DockPeek]）。
    val inputOffset by animateDpAsState(
        targetValue = if (inputInFront) 0.dp else DockPeek,
        animationSpec = tween(220),
        label = "inputOffset"
    )
    val tabsOffset by animateDpAsState(
        targetValue = if (inputInFront) DockPeek else 0.dp,
        animationSpec = tween(220),
        label = "tabsOffset"
    )

    // 外层承载淡入淡出：用离屏合成把「胶囊 + 投影」作为一个整体统一调透明度，
    // 避免逐指令调制导致胶囊变半透明、底下投影透出来发灰。
    // 关键：graphicsLayer 放在内边距之外，让离屏缓冲在胶囊四周留出投影空间，
    // 否则柔和投影会被矩形缓冲边缘裁成直角。
    Box(
        modifier = modifier
            .fillMaxWidth()
            // imePadding 在外、navigationBarsPadding 在内：键盘弹起时上移并消抵与导航栏的重叠
            .imePadding()
            .navigationBarsPadding()
            .graphicsLayer {
                val a = contentAlpha()
                alpha = a
                // 仅在淡出过程中启用离屏合成；完全显示时用默认策略，省去无谓的离屏缓冲。
                compositingStrategy =
                    if (a < 1f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                // 上/下留出透明外边距作为投影缓冲区（胶囊仍贴着底部，不发生位移）。
                .padding(top = 16.dp, bottom = 12.dp)
                .height(DockPillHeight + DockPeek)
        ) {
            val inputLayer: @Composable () -> Unit = {
                DockLayer(offsetY = inputOffset, inFront = inputInFront, onTapPeek = { onInputInFrontChange(true) }) {
                    inputContent()
                }
            }
            val tabsLayer: @Composable () -> Unit = {
                DockLayer(offsetY = tabsOffset, inFront = !inputInFront, onTapPeek = { onInputInFrontChange(false) }) {
                    tabPill()
                }
            }
            // 后画的在上层：把当前在前的那个后画
            if (inputInFront) {
                tabsLayer(); inputLayer()
            } else {
                inputLayer(); tabsLayer()
            }
        }
    }
}

@Composable
private fun BoxScope.DockLayer(
    offsetY: Dp,
    inFront: Boolean,
    onTapPeek: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .offset(y = offsetY)
            .height(DockPillHeight)
    ) {
        content()
        if (!inFront) {
            // 在后：整块作为切换热区（实际只有露出的探头可点），并拦截内部交互
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(28.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTapPeek
                    )
            )
        }
    }
}
