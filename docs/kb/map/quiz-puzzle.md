---
trust: L1
anchors:
  - app/src/main/java/com/jk/offermate/ui/quiz/QuizViewModel.kt#moveCategory
  - app/src/main/java/com/jk/offermate/ui/quiz/QuizScreen.kt#categoryColor
  - app/src/main/java/com/jk/offermate/ui/quiz/CategoryResolver.kt#displayCategory
  - app/src/main/java/com/jk/offermate/ui/components/PuzzleGrid.kt#PuzzleGrid
  - app/src/main/java/com/jk/offermate/ui/components/WaveFillBlob.kt
  - app/src/main/java/com/jk/offermate/data/repository/CategoryRepository.kt#saveOrder
  - app/src/main/java/com/jk/offermate/data/repository/CategoryOrderStore.kt#CategoryOrderStore
  - app/src/main/java/com/jk/offermate/data/local/entity/CategoryEntity.kt
verified_commit: 63c65fb
verified_at: 2026-09-19
supersedes: null
---

# 题库页 / 拼图 / 分类 / 刷题

## 一句话

分类**不是一张表**，而是"题目派生 + 用户手建"在 `QuizViewModel.uiState` 里合出来的；顺序存 DataStore、分类名存 Room，两套存储不一致。动这块几乎一定要碰 `QuizViewModel` 的 combine。

## 涉及文件

| 文件 | 职责 |
|---|---|
| `ui/quiz/QuizScreen.kt` | 总览页 UI + `CategoryPalette`/`categoryColor` 取色 + 增删分类/新增题目三个弹窗 |
| `ui/quiz/QuizViewModel.kt` | **中枢**：分类合并、排序应用、`moveCategory`、级联 `deleteCategory` |
| `ui/quiz/CategoryResolver.kt` | 纯函数 `displayCategory(q)`：`category` → 首个 tag → `"其他"` |
| `ui/components/PuzzleGrid.kt` | 拼图布局（自定义 `Layout`）+ 形状生成 + 长按拖拽手势，**无持久化职责** |
| `ui/components/WaveFillBlob.kt` | 单块拼图的波浪水位填充渲染 |
| `ui/quiz/QuizCategoryScreen.kt` / `QuizCategoryViewModel.kt` | 分类详情 / 刷题页 |
| `data/repository/CategoryRepository.kt` | 分类名（Room）+ 顺序（DataStore）的统一门面 |
| `data/repository/CategoryOrderStore.kt` | 顺序持久化：DataStore `offermate_category_order`，key `category_order`，`\n` 连接 |
| `data/local/entity/CategoryEntity.kt` | `@PrimaryKey name` + `createdAt`，**只存用户手建的分类** |

## 数据流

```
question 表 ──► QuestionRepository.observeAll() ─┐
category 表 ──► observeCategories() ─────────────┤
DataStore 顺序 ──► observeOrder() ───────────────┼─► 5 路 combine（QuizViewModel.uiState）
query(防抖) ────────────────────────────────────┤
search 结果 ────────────────────────────────────┘
                     ▼
     groupBy{displayCategory} → fromQuestions
     + 用户分类中未出现的 → 补 CategorySummary(0,0)
     + sortedWith(orderIndex → -total → name)
                     ▼
     QuizOverviewScreen → PuzzleGrid(columns=3, cellHeight=112.dp)
                     ▼ piece(index, shape, contentPadding)
     WaveFillBlob(progress=ratio, color=categoryColor(name), shape)
```

拖拽保存：`PuzzleGrid.onReorder(from,to)` → `QuizViewModel.moveCategory` → 取当前展示顺序 → `add(to, removeAt(from))` → `saveOrder(全量名单)`。

## 改动清单

### A. 纯外观（换色、改列数/格高、改拼图形状）

1. 取色/调色板在 `QuizScreen.kt` 的 `CategoryPalette` + `categoryColor`。
2. 列数与格高是 `QuizOverviewScreen` 调 `PuzzleGrid` 时的实参（`columns = 3`、`cellHeight = 112.dp`）。
3. 改 `PuzzleGrid` 的 `tab`（当前 `maxWpx * 0.06f`）**必须同步改 `sidePad`**，否则文字会探进凹口被形状裁掉；`Layout` 里的 `childW/childH/boardW/boardH` 也按 tab 计算。
4. ⚠️ `categoryColor` 被 **4 个界面**共用（`QuizScreen`、`QuizCategoryScreen`、`QuestionsScreen`、`AiChatHubScreen`），改色必须回归这四处。

### B. 给分类新增可配置属性（自选颜色、图标、置顶…）

1. `CategoryEntity` 加列 → **必须升 `OfferMateDatabase.version`**（见 `pitfalls/room-column-migration.md`，当前升版本会清库）。
2. `CategoryDao` 加方法 → **必须同步补 `CategoryRepositoryTest.FakeCategoryDao`**，否则单测编译失败。
3. `CategoryRepository` 接口加方法 → 注意 `RoomCategoryRepository` 的 `orderStore` 可空降级模式（为 null 时 `observeOrder` 返回空、`saveOrder` no-op，便于 JVM 单测）。
4. `CategorySummary` 带上新字段 + `QuizOverviewState`。
5. ⚠️ **`QuizViewModel.uiState` 已用满 5 路 `combine` 重载**，再加一路 Flow 必须改成嵌套 combine 或 `combine(vararg)`，否则编译不过。这是这块最常踩的坑。
6. UI：`QuizOverviewScreen` 的 piece 渲染 + 相关弹窗 + `QuizRoute` 回调透传。

### C. 只是展示偏好（列数偏好、排序模式…）

**优先避开 Room**：仿 `CategoryOrderStore` 另起一个 DataStore key，`CategoryRepository` 加 `observeX/saveX`，接进 combine。不动 DB 就不会清库。

## 约束与易错点

1. **`observeCategories()` 不是全量分类**。AI/标签派生的分类不落 `category` 表，只有用户手建与"移动分类"的目标分类会 `addCategory`。任何"遍历所有分类"的逻辑都必须再并上题目派生集合（参考 `QuizViewModel.uiState` 与 `AppContainer` 里 `CategoryListTool` 的写法）。
2. **分类是弱标识**：`CategoryEntity.name` 是主键，`QuestionEntity.category` 是裸字符串，无外键。所以目前**没有重命名功能**——重命名会产生孤儿数据。
3. **顺序与分类名不事务一致**：`deleteCategory` 只删 Room 行，**不会从 `category_order` 清单里剔除名字**。残留名字不会显示幽灵分类，但同名分类重建后会"继承"旧位置。
4. **删分类 = 级联删题**，且在应用层筛 id（因为显示分类是启发式，无法写成 SQL WHERE）。删「其他」会删掉所有无分类无标签的题，不可恢复，UI 有二次确认。
5. **判手动题看 `QuestionEntity.source`**（`AI` / `MANUAL`），不要靠 `id` 的 `manual_` 前缀猜。手动题 `relevanceScore = 100` 用于置顶。
6. **空分类是"只有底色的空块"**：`ratio` 在 `total == 0` 时返回 `0f`，`WaveFillBlob` 对 `progress <= 0f` 直接不画水位。
7. **配色语义不统一**（已知缺陷）：`QuestionsScreen` 用 `categoryColor(q.tags.firstOrNull())`，题库页用 `categoryColor(displayCategory(q))`。同一道题在两个页面可能不同色。做配色相关需求时值得顺手统一为 `displayCategory`。
8. `PuzzleGrid` 的 `pointerInput(count)` 以数量为 key：顺序变但数量不变时手势 lambda 不重建。当前闭包只捕获循环下标 `i` 所以安全，但**若后续在手势里捕获分类名会拿到过期值**。
9. `moveCategory` 的 `from/to` 是**当前展示列表**的下标；`saveOrder` 存全量名单，因此新出现的分类天然排在末尾。

## 相关单测

- `ui/quiz/CategoryResolverTest.kt` — `displayCategory` 三级回退
- `data/repository/CategoryRepositoryTest.kt` — 分类增删（`FakeCategoryDao`；**`orderStore` 传 null，顺序逻辑无覆盖**）
- `data/repository/QuestionRepositoryTest.kt` — 题目读写、手动题

⚠️ **没有** `QuizViewModel` 单测，也没有 `PuzzleGrid` 的 Compose 测试。分类合并与排序逻辑改动没有自动化兜底，改这两处要么手动验证，要么自备 fake repository 补测试。
