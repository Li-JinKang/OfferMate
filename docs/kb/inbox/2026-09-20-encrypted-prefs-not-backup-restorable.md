---
trust: L2
anchors:
  - app/src/main/java/com/jk/offermate/data/settings/EncryptedPrefsKeyStore.kt
  - app/src/main/AndroidManifest.xml
  - app/src/main/res/xml/backup_rules.xml
  - app/src/main/res/xml/data_extraction_rules.xml
  - app/src/main/java/com/jk/offermate/OfferMateApplication.kt#seedDevApiKeyIfBlank
  - tools/dev-backup.sh
verified_commit: 62062a5
verified_at: 2026-09-20
supersedes: null
---

# API Key 是唯一"拷文件也带不回来"的数据，且当前备份配置会把它搬成一颗雷

## 现象

开发期因签名冲突被迫卸载重装后，App 私有目录里除 API Key 之外的数据都可以靠拷文件恢复
（Room 库、四个 DataStore、`files/memory/**` 的简历记忆），**只有 API Key 必须手动重新粘贴**。

## 归因

`EncryptedPrefsKeyStore` 用 `MasterKey.Builder(context)` + `EncryptedSharedPreferences`，
主密钥落在 Android Keystore，而 Keystore 条目按 app UID 归属、随包卸载一起删除。
`shared_prefs/offermate_secure_prefs.xml` 里只有密文，拷回去也没有能解开它的密钥。

由此推出两条后果：

1. 任何文件级备份/恢复（`adb run-as tar`、手动拷目录）**都必须排除** `offermate_secure_prefs.xml`。
   把它一起恢复比不恢复更糟：得到一个打不开的密文文件，而不是一个"空 Key"的干净状态。
2. `AndroidManifest.xml` 目前是 `allowBackup="true"`，而 `backup_rules.xml` 与
   `data_extraction_rules.xml` **两份都还是 AGP 生成的空模板**（内容全被注释掉，等于全量备份）。
   走一次云备份或换机 D2D 恢复，这个 xml 会被搬到新设备，主密钥不会 ——
   归因是首次读 Key 时 `EncryptedSharedPreferences.create` 抛异常。

## 规避规则

1. 写任何备份/迁移能力（脚本或应用内功能）时，**API Key 走"明文导出 → 导入时重新加密"**，
   不要试图搬运密文。其余数据才可以走文件级拷贝。
   已落地的两件：`tools/dev-backup.sh`（刻意不含 `shared_prefs`）+
   `local.properties` 的 `devApiKey` → debug 包 `BuildConfig` → `OfferMateApplication`
   启动时在 Key 为空才回填（`seedDevApiKeyIfBlank`）。
2. `allowBackup` 保持 true 的前提下，两份规则文件都要显式排除这个 sharedpref：
   `<exclude domain="sharedpref" path="offermate_secure_prefs.xml"/>`。
   只改一份不够 —— `dataExtractionRules`（API 31+ 的云备份/D2D）和 `fullBackupContent`（API 30-）
   是两套独立生效的配置，Manifest 里两个属性都挂着。
3. 新增任何 `EncryptedSharedPreferences` 存储时，同步把文件名加进上面两份排除列表。

## 证据与待验证

- 代码现状可直接核对：`EncryptedPrefsKeyStore.kt` 的 `MasterKey.Builder` 调用；
  `AndroidManifest.xml` 的 `android:allowBackup="true"` + `dataExtractionRules` + `fullBackupContent`；
  两份 xml 的空模板内容（`git show 5ece84e:app/src/main/res/xml/backup_rules.xml`）。
- 存储清单来自通读 `AppContainer` + 四处 `preferencesDataStore(name=...)` 声明
  （`offermate_settings` / `offermate_category_order` / `offermate_answer_update` / `offermate_resume`）
  与 `MemoryStore` / `ResumeFileStore` 的目录约定。
- **未实测**：本项目没有复现过"恢复密文后崩溃"这一步。第 2 条后果是按 Android Keystore
  的平台行为推出来的，不是观测到的崩溃栈。审阅时若要升级为 L1，需要补一次真实的
  备份-卸载-恢复复现（或至少一条崩溃日志）。

## 触发条件

任何涉及备份、迁移、导出/导入、换机恢复的需求；以及任何新增加密存储的改动。
