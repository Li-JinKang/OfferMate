---
trust: L2
anchors:
  - app/build.gradle
  - gradle.properties
  - baselineprofile/build.gradle
  - baselineprofile/src/main/java/com/jk/offermate/baselineprofile/BaselineProfileGenerator.kt
  - app/src/androidTest/java/com/jk/offermate/ExampleInstrumentedTest.java
verified_commit: 62062a5
verified_at: 2026-09-20
supersedes: null
---

# 本机组 applicationId 后缀：整组加才对，且 buildType.applicationIdSuffix 会被 baselineprofile 插件覆盖

> 本卡描述的配置由 `62062a5`（`build(gradle): 开发包与正式包分离 applicationId…`）引入，
> 锚点即该 commit 的改动本身。
>
> 同日曾有一版候选卡把结论写反（主张"后缀只能给 debug、测量变体必须无后缀"），
> 未提交即废弃，git 里无痕。那个错误形态及其危害见下面「正解」一节——
> 它是本卡最容易被重新踩中的点，不是历史八卦。

## 现象：两对签名冲突

`debug`（debug 签名）与 `release`（`keystore.properties` 正式签名）原先共用 `applicationId`
`com.jk.offermate`，设备上装过一种再装另一种就是 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，
只能卸载重装 —— 卸载会清掉题库、简历记忆、API Key。

第二对原先被忽略了：`profileable` 被 `buildTypes.configureEach` 改成 debug 签名，
但 `applicationId` 与正式 `release` 相同，所以 **`profileable` 与 `release` 之间同样互不兼容**。
`configureEach` 那个列表按定义不可能覆盖 `release` 自己。

## 正解：整组加同一个后缀，而不是"不加后缀"

容易把 `2026-09-19-app-build-type-variant-explosion.md` 等价性验收第 4 条
（「applicationId 无后缀 —— 有后缀就装成另一个应用，本地数据带不过去」）读成字面要求
「测量变体不得有后缀」。按字面执行会让测量变体脱离 debug 包的数据目录，
正好废掉它采 profile 需要真实数据的用途。

第 4 条的成立前提是「其他变体都无后缀」，它约束的是**相对关系**：

> 测量型变体必须与 **debug 开发包**同 `applicationId`（数据才通），
> 同时与 **release** 不同 `applicationId`（才不冲突）。

两条同时满足的唯一办法：把 `debug` + `profileable` + `nonMinifiedRelease` + `benchmarkRelease`
当成一个「本机组」**整组**加同一个后缀，`release` 保持无后缀。
`applicationId` 不影响 R8、ART 行为或 profile 内容（profile 记录类/方法签名），
所以「与 release 性能等价」不受后缀影响。

后缀值放 `gradle.properties` 的 `offermateDevAppIdSuffix`，`:app` 与 `:baselineprofile` 共用。

## 坑：buildType.applicationIdSuffix 对派生变体无效

**在 `buildTypes.configureEach` 里设 `applicationIdSuffix`，对 `nonMinifiedRelease` /
`benchmarkRelease` 不生效。** baselineprofile 插件会在更后面用 variant API 把这两个变体的
`applicationId` 设回被测应用的值，把 buildType 上的后缀盖掉。

实测证据（往 `configureEach` 里塞 println 得到）：

```
[诊断] configureEach 命中 buildType=benchmarkRelease 在本机组=true
[诊断] 已设 benchmarkRelease.applicationIdSuffix=.dev
[诊断] 已设 nonMinifiedRelease.applicationIdSuffix=.dev
```

buildType 属性确实被设成了 `.dev`，但同一次构建产出的 merged manifest 里：

```
benchmarkRelease         package="com.jk.offermate"      ← 被插件盖回去了
nonMinifiedRelease       package="com.jk.offermate"      ← 同上
debug                    package="com.jk.offermate.dev"  ← 生效
profileable              package="com.jk.offermate.dev"  ← 生效
```

**规避：改用 variant API，它是最终权威，且我们的回调注册晚于插件所以压得住。**

```groovy
androidComponents {
    onVariants(selector().all()) { variant ->
        if (localDevBuildTypes.contains(variant.buildType)) {
            variant.applicationId.set(baseApplicationId + devAppIdSuffix)
        }
    }
}
```

`configureEach` 则退回只管 `signingConfig`（签名不会被插件覆盖，那部分一直是有效的）。
改完后四个本机组变体的 merged manifest 全部为 `com.jk.offermate.dev`，`release` 仍为无后缀。

推论：**凡是 baselineprofile 插件派生的变体，buildType 层面的 applicationId 相关配置都不可信，
一律走 variant API 验证后再下结论。** 签名类配置走 buildType 有效，applicationId 类无效。

## 实操顺序：改后缀是一次性破坏动作

换 `applicationId` = 换 `/data/data/<id>` 目录，设备上现有开发包的数据全部变成孤儿
（题库、简历记忆、会话、API Key）。所以顺序必须是：

1. 先用 `tools/dev-backup.sh -p com.jk.offermate` 备份旧包数据；
2. 改后缀、装新包；
3. `tools/dev-restore.sh -p com.jk.offermate.dev <备份文件>` 搬过去
   （tar 里存的是相对路径，天然支持跨包名恢复）；
4. API Key 单独处理 —— 它拷不回来，见
   `docs/kb/inbox/2026-09-20-encrypted-prefs-not-backup-restorable.md`。

## 连带：加后缀会打断两处硬编码包名

加后缀前必须一起改，否则静默出错：

1. `BaselineProfileGenerator.PACKAGE`（原为字面量 `"com.jk.offermate"`）——
   UiAutomator 会去启动一个不存在的包，宏基准失败。
   改为读 `BuildConfig.TARGET_PACKAGE`，由 `baselineprofile/build.gradle` 的 `buildConfigField` 注入。
   注意必须是 `val` 不能是 `const val`：引用 Java 的 `static final` 字段不算 Kotlin 编译期常量。
2. `ExampleInstrumentedTest` 里 `assertEquals("com.jk.offermate", ...)` —— 断言会失败。
   改为 `BuildConfig.APPLICATION_ID`，变体无关。

两处都需要 `buildFeatures { buildConfig true }`（AGP 8 默认关闭）。

`<instrumentation android:targetPackage>` 指向 `:baselineprofile` 自己而不是被测应用，
这是 `android.experimental.self-instrumenting = true` 的预期行为，不用改。

## 证据

- `app/build.gradle`：脚本顶部的 `baseApplicationId` / `localDevBuildTypes` / `devAppIdSuffix`，
  `buildTypes.configureEach`（只设签名），文件末尾 `androidComponents.onVariants`（设 applicationId）。
- merged manifest 逐变体核对命令：
  ```bash
  for d in app/build/intermediates/merged_manifest/*/; do
      f=$(find "$d" -name AndroidManifest.xml | head -1)
      printf "%-24s %s\n" "$(basename $d)" "$(grep -o 'package="[^"]*"' "$f" | head -1)"
  done
  ```
  本卡「现象」与「坑」两节的表格就是这条命令改前/改后的实际输出。
- `:app:testDebugUnitTest` 全绿；`:app:assembleDebug` 产出 APK 的
  `output-metadata.json` 里 `applicationId` 为 `com.jk.offermate.dev`；
  `:baselineprofile:assembleNonMinifiedRelease` 成功且 `BuildConfig.TARGET_PACKAGE`
  为 `com.jk.offermate.dev`。
- 插件版本：`androidx.baselineprofile` 1.3.4 / AGP 8.7.3。**覆盖行为未在其他版本验证。**
- **未实测**：真机上完整跑一遍 baseline profile 采集（需要 API 33+ 真机）。
  配置层面自洽，但「插件把采到的 profile 正确写回 `app/src/release/generated/baselineProfiles/`」
  这一步没验过。升 L1 前必须补这次实跑。

## 触发条件

任何「又要卸载重装才能 Run」的排查；任何改 `applicationId` / `signingConfig` /
新增构建类型的改动；以及任何怀疑 baselineprofile 派生变体配置没生效的场合。
