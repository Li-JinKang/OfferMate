---
trust: L2
anchors:
  - app/build.gradle
  - baselineprofile/src/main/java/com/jk/offermate/baselineprofile/BaselineProfileGenerator.kt
verified_commit: 07f92fa
verified_at: 2026-09-20
supersedes: null
---

# 打进包里的 baseline profile 不含任何应用代码（只有库规则）

**现象确凿，根因待查。** 从流式渲染的性能排查里顺带发现，但影响面远超流式——
它影响启动、首屏、以及所有 Compose 路径的前几秒，所以单独记一张。

## 现象

`app/build.gradle` 配了 `baselineProfile project(':baselineprofile')`，
`assembleProfileable` / `assembleRelease` 也确实会把 `.dm` 打进 APK
（`app/build/outputs/apk/profileable/baselineProfiles/*/app-profileable.dm`，约 7KB）。

但那份 profile 里**没有一行应用自己的代码**：

```bash
./gradlew assembleProfileable
P=app/build/intermediates/merged_art_profile/profileable/mergeProfileableArtProfile/baseline-prof.txt
wc -l $P               # 5446
grep -c offermate $P   # 0   ← 关键
```

5446 行全部来自各个 AAR 自带的 `baseline-prof.txt`（Compose / AndroidX 等库规则）。
另外 `app/src/release/generated/baselineProfiles/` 与
`app/src/profileable/generated/baselineProfiles/` 都是**空目录**。

## 影响

应用侧的 composable、lambda、以及整条流式渲染链路**全靠 JIT**：
进程启动后这些方法先解释执行，要跑到足够次数才会被 JIT 编译。

两个具体后果：

1. **启动与首屏偏慢**，这是 baseline profile 本来要解决的主要问题。
2. **性能 trace 会系统性偏悲观。** 抓 trace 时最容易采到的就是"刚进页面、刚开始流式"
   那几秒，而那正是 JIT 还没追上的窗口。用 profileable 包测出来的帧耗时里，
   有一部分是"缺 profile"造成的，不是代码本身的问题。

   **所以：在补上 profile 之前，不要用 profileable 包的帧数据去判断某段渲染代码贵不贵。**
   这是本轮流式排查里一个未被排除的干扰项
   （见 `inbox/2026-09-20-fluid-markdown-lightweight-block-path.md` 第十三节）。

## 根因：待查

只确认了现象，没确认为什么。三个方向，按可能性排序：

1. **`generateBaselineProfile` 从未成功跑过。** 它需要连真机跑
   `:baselineprofile` 的 instrumented test，本地没设备时会跳过/失败，而
   `assembleProfileable` 仍然能成功（只是用库规则凑出一份 profile），**不会报错也不会警告**——
   这是最容易长期不被发现的形态。
2. 跑过但输出没落进 `app/src/*/generated/baselineProfiles/`（那两个目录是空的，与此一致）。
3. `BaselineProfileGenerator` 的采集路径没覆盖到目标屏幕，导致采出来的规则为空。
   注意它的注释说明采集**依赖设备上真实的对话/题库数据**，数据不足时可能采不到东西。

## 怎么验证修好了

```bash
./gradlew :app:generateBaselineProfile            # 需要连真机
ls app/src/profileable/generated/baselineProfiles/ # 应当非空
./gradlew assembleProfileable
grep -c offermate app/build/intermediates/merged_art_profile/profileable/mergeProfileableArtProfile/baseline-prof.txt
# 应当远大于 0（数百到数千行量级）
```

**把最后那条 grep 当成验收判据**，不要只看"构建成功"或"`.dm` 存在"——
这两者在 profile 为空时同样成立，正是本问题长期隐身的原因。

## 顺带：改了热路径要重采

即便修好了，profile 也是**按当时的代码路径**采的。本轮新增了
`ui/components/InlineMarkdown.kt` 这条轻量渲染路径（段落/标题/列表项走 `BasicText`
而不是 `Markdown()`），是全新的热路径，旧 profile 不可能覆盖。
**动过渲染/滚动这类高频路径之后要重采一次。**

## 证据

- 上面两段命令的实际输出（`wc -l` = 5446、`grep -c` = 0），在 `07f92fa` + 本轮未提交改动上测得。
- `app/build.gradle`：`baselineProfile project(':baselineprofile')`，以及 `profileable`
  构建类型 `initWith release` 的定义与其长注释（解释了为什么用 profileable 而非 debuggable 做剖析）。
