---
trust: L1
anchors:
  - app/src/main/java/com/jk/offermate/data/local/OfferMateDatabase.kt
  - app/src/main/java/com/jk/offermate/di/AppContainer.kt
  - app/src/main/java/com/jk/offermate/data/local/entity/QuestionEntity.kt
verified_commit: 63c65fb
verified_at: 2026-09-19
supersedes: null
---

# Room 加列：不升版本会崩，升了版本会清库

## 现象

两种失败方向：

1. 改了 `@Entity` 但没升 `version` → 运行时崩溃（schema 与实际表不一致）。
2. 升了 `version` → 编译运行都正常，**但用户本地题库、刷题进度、会话全部被清空**，而且在开发机上不容易察觉（本来就没什么数据）。

## 根因

`AppContainer` 的 Room builder 用了 `fallbackToDestructiveMigration()`：

```kotlin
Room.databaseBuilder(context, OfferMateDatabase::class.java, "offermate.db")
    .fallbackToDestructiveMigration()
    .build()
```

版本不匹配时 Room 直接删库重建，不报错。项目里**没有任何手写 `Migration`**，`exportSchema = false` 所以也没有 schema json 可供比对。

当前版本是 **11**（v11 = `imported_post` 增 `failureReason` 列）。版本链一路是加列堆上来的。

## 规避规则

1. 改动 `@Entity` 字段，**必须**同步升 `OfferMateDatabase` 的 `version` **并更新其上方的版本注释**（项目惯例：注释里一句话写清这一版改了什么）。
2. 决定升版本前先问：**这次加列值得清空用户数据吗？**
   - 要保数据 → 在 `AppContainer` 加 `.addMigrations(...)` 并手写 `Migration`。
   - 只是开发期字段 → 可以接受，但要意识到已装机用户会丢数据。
3. **能不动 DB 就不动**。展示偏好、排序、开关一类的数据优先仿 `CategoryOrderStore` 用 DataStore（不涉及 schema，不会清库）。
4. 加列若要参与查询，记得同时加 `@Index`（参考 `QuestionEntity` 上 `bucketKey` / `exactHash` 的索引）。
5. 别忘了新列在 DAO 的**定向 UPDATE 语句**里（如 `updateStatus` / `updateFailure`）是否需要带上。

## 证据

- `OfferMateDatabase.kt`：`version = 11` + 上方 v11 变更注释。
- `AppContainer` 的 `fallbackToDestructiveMigration()` 调用。
- 版本链由多次加列推进：去重指纹列（`exactHash`/`simhash`/`bucketKey`）、`category`、`source`、多轮会话、`failureReason`。
- 反例：`docs/plan/roadmap.md` 长期记录版本为 9，与代码实际的 11 不符——**版本号只能查代码**。

## 触发条件

任何"给题目/帖子/分类/会话加个字段"的需求。这是本项目最高频的破坏性改动。
