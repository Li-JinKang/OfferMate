---
inclusion: auto
name: 题库拼图与分类
description: 当需求涉及题库页、拼图网格（PuzzleGrid）、分类的增删改与显示顺序、分类配色、刷题页、题目搜索、手动新增题目时激活。包括修改拼图外观/形状/布局、给分类增加属性、调整分类排序与合并规则、刷题进度统计等。
---

# 代码地图 · 题库拼图与分类

完整地图见 `docs/kb/map/quiz-puzzle.md`，**动手前请先读它**。

#[[file:docs/kb/map/quiz-puzzle.md]]

## 三条最容易踩的

1. 分类不是一张表：是"题目派生（`CategoryResolver.displayCategory`）+ 用户手建（`category` 表）"在 `QuizViewModel.uiState` 里合出来的。`observeCategories()` **不是全量分类**。
2. `QuizViewModel.uiState` 已用满 5 路 `combine` 重载，**再加一路 Flow 必须改写成嵌套 combine 或 `combine(vararg)`**。
3. `categoryColor` 被 4 个界面共用；改 `PuzzleGrid` 的 `tab` 必须同步改 `sidePad`。
