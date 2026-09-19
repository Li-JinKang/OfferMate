---
trust: L2
anchors:
  - app/build.gradle
  - app/src/profileable/AndroidManifest.xml
  - baselineprofile/build.gradle
verified_commit: 2477a27
verified_at: 2026-09-19
supersedes: null
---

# 给 :app 加构建类型会被 baselineprofile 插件放大成三个变体

本卡描述的配置由 `2477a27`（`build(gradle): 新增 profileable 变体…`）引入。

## 现象

给 `app/build.gradle` 的 `buildTypes` 加**一个** `profileable` 之后，应用变体从 4 个涨到 7 个：

```
# 加之前
assembleDebug  assembleRelease  assembleBenchmarkRelease  assembleNonMinifiedRelease

# 加之后，多出两个没人要的
assembleProfileable
assembleBenchmarkProfileable      ← 噪音
assembleNonMinifiedProfileable    ← 噪音
```

后果不是构建失败（配置是成功的），而是 Build Variants 下拉框里多出两个含义不明的选项，
以及每次配置阶段多算两套变体。

## 根因

`androidx.baselineprofile` 插件会为**每个非 debug 构建类型**各派生一对
`benchmarkXxx` / `nonMinifiedXxx`。它不区分这个构建类型是否与基线 profile 有关，
所以你加的任何 release 型构建类型都会被放大成三个。

## 规避规则

新增构建类型时，同步在 `app/build.gradle` 里禁掉对应的两个派生变体：

```groovy
androidComponents {
    beforeVariants(selector().withBuildType('benchmark<新类型名首字母大写>')) { it.enable = false }
    beforeVariants(selector().withBuildType('nonMinified<新类型名首字母大写>')) { it.enable = false }
}
```

另外两条同类的连带项，加构建类型时一起处理：

1. **签名**：`initWith release` 会把 release 的 `signingConfig` 一并复制过来。仅用于本机测量的
   变体（`profileable` / `benchmarkRelease` / `nonMinifiedRelease`）必须改用 debug 签名，
   否则两种结局：没有 `keystore.properties` 时未签名装不上；有正式签名时与设备上已装的 debug
   开发包冲突（`INSTALL_FAILED_UPDATE_INCOMPATIBLE`），只能卸载——而卸载会清掉本地对话数据。
   现有的 `buildTypes.configureEach` 块就是干这个的，把新名字加进它的列表。
2. **变体专属清单**：只想给某个变体加清单条目（如 `<profileable>`）时，放
   `app/src/<变体名>/AndroidManifest.xml`，**不要**写进 `src/main`——否则会泄漏到正式 release。

## 怎么证明一个变体真的与 release 等价

「看起来继承了 release」不等于等价。逐项验，五条都过才算：

```bash
V=profileable          # 变体名
Vc=Profileable         # 首字母大写
./gradlew :app:assemble$Vc

# 1) R8 真的跑了（有 mapping 才算）
ls app/build/outputs/mapping/$V/mapping.txt

# 2) 基线 profile 真的打进 APK
unzip -l app/build/outputs/apk/$V/app-$V.apk | grep assets/dexopt/baseline.prof

# 3) 没有 debuggable（有就说明性能数据不可用）
grep -c debuggable app/build/intermediates/merged_manifest/$V/process${Vc}MainManifest/AndroidManifest.xml

# 4) applicationId 无后缀 —— 有后缀就装成另一个应用，本地数据带不过去
python3 -c "import json;print(json.load(open('app/build/outputs/apk/$V/output-metadata.json'))['applicationId'])"

# 5) 反向确认没污染正式 release
./gradlew :app:processReleaseMainManifest
grep -c profileable app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml
```

## 一个已经存在、容易重复造的变体

插件派生的 **`benchmarkRelease` 本身就已经满足上面五条**：R8 开、基线 profile 已打入、
无 `debuggable`、`<profileable android:enabled="true" android:shell="true"/>` 已注入、
applicationId 无后缀。

也就是说「我要一个能用 Profiler 的 release 包」这个需求，在加 `profileable` 之前就已经有解了。
新增 `profileable` 的理由是语义归属（自己的剖析包，不随基准工具链变动），
**不是**填补空缺。下次再冒出类似需求，先按上面五条验一遍 `benchmarkRelease`，
别直接开新变体。

## 证据

- 变体清单前后对比：`./gradlew :app:tasks --all | grep -oE "^assemble[A-Za-z]*" | sort -u`
  （本卡「现象」一节的两份清单就是这条命令的实际输出）
- `app/build.gradle`：`profileable` 构建类型、`androidComponents.beforeVariants` 禁用块、
  `buildTypes.configureEach` 的签名覆盖列表
- `benchmarkRelease` 的五条结论来自实跑 `:app:assembleBenchmarkRelease` 后按上面清单逐项检查
- 插件版本：`androidx.baselineprofile` 1.3.4 / AGP 8.7.3（`gradle/libs.versions.toml`）。
  **派生行为未在其他版本上验证**，升级插件后若变体数量变化，先重跑变体清单对比。

## 触发条件

任何给 `:app` 新增或重命名构建类型的改动。只要 `androidx.baselineprofile` 插件还在，
这个放大就会发生。
