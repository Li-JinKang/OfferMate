---
trust: L1
anchors:
  - app/src/main/java/com/jk/offermate/ui/components/ZoomableImage.kt#ZoomableImage
verified_commit: 63c65fb
verified_at: 2026-09-19
supersedes: null
---

# 可缩放图片吞掉手势，拖到边缘后外层滚不动

## 现象

简历页的 PDF 预览（放在 `verticalScroll` 里）出现两个问题：

1. 未放大时单指拖动图片，**外层页面不滚动**——手势被图片的 `pointerInput` 吃掉了。
2. 放大后把图片拖到边缘，继续拖**卡死**，既不能再移动图片，也带不动外层滚动。
3. 早期还能把图片无限拖走，内容拖出容器外看不见。

## 根因

`detectTransformGestures` 会消费它收到的全部手势，Compose 的嵌套滚动**不会自动**把"没用完的位移"退还给外层可滚动容器。所以内层只要挂了手势检测，外层就失去了滚动机会；而如果不对 `offset` 做边界约束，平移量会无限累加。

## 规避规则

内层手势组件放进可滚动容器时，按 `ZoomableImage` 的三段式处理：

1. **算出真实可拖边界**，别让位移无限累加：
   `maxOffset = (containerSize * (scale - 1f)) / 2f`，然后 `coerceIn(-max, max)`。
2. **把"用不掉的位移"显式转发给外层**：挂一个不消费的 `NestedScrollConnection`，只借它的 `NestedScrollDispatcher`：
   ```kotlin
   val leftover = pan - consumed
   if (!isPinch && leftover != Offset.Zero) {
       scrollDispatcher.dispatchPostScroll(consumed, leftover, NestedScrollSource.UserInput)
   }
   ```
3. **未放大时整段转发**（`scale <= MIN_SCALE`）：此时图片没有平移需求，单指拖动应完全归外层。
4. 双指缩放（`zoom != 1f`）时**不要转发**，否则捏合会顺带滚动页面——用 `isPinch` 把两类手势分开。

## 证据

- `ui/components/ZoomableImage.kt`：`PassThroughNestedScrollConnection`（空实现，只为拿 dispatcher）、`MIN_SCALE = 1f` / `MAX_SCALE = 5f`、`maxOffsetX/Y` 计算、两处 `dispatchPostScroll`，以及函数注释里记录的"拖到边缘后可继续带动外层滚动"意图。
- `docs/plan/roadmap.md` 「简历预览区缩放/拖拽越界」条目记录了这次修复。

## 触发条件

任何"内层要处理手势 + 外层要滚动"的组合：图片预览、画布、可拖拽卡片、图表。本项目里除了简历预览，拼图拖拽排序也在可滚动列表内（它用 `detectDragGesturesAfterLongPress` 避开了冲突——长按才接管手势，这是另一种可行解法）。

⚠️ 该组件**只经过手动/真机验证，没有 Compose UI 测试**。改动后必须实机验证。
