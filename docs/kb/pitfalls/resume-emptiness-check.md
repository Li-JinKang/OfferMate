---
trust: L1
anchors:
  - app/src/main/java/com/jk/offermate/ui/home/HomeViewModel.kt#canAnalyze
  - app/src/main/java/com/jk/offermate/work/AnalyzeResumeWorker.kt
  - app/src/main/java/com/jk/offermate/agent/resume/ResumeProfile.kt
verified_commit: 63c65fb
verified_at: 2026-09-19
supersedes: null
---

# 判"简历已配置"只能看 `rawText`，不能看 `targetRole` / `skills`

## 现象

明明已经上传了简历 PDF，导入面经却被拦下来提示"未配置简历"；或者相关性分析按"无简历模式"跑，匹配度不可用。

## 根因

简历页改版后去掉了「目标岗位」「技能」这类结构化输入框，改为"上传 PDF → 渲染预览 → 识别文本可编辑"。于是 `ResumeProfile.targetRole` / `skills` 在常规路径下**经常是空的**——它们现在只由 AI 结构化流程填充，且结果写进 `filesDir/memory/` 而不是回填这两个字段。

拿空的 `targetRole` 当"有没有简历"的判据，就会把正常上传过简历的用户误判成未配置。

## 规避规则

1. **判据统一用 `ResumeProfile.rawText.isNotBlank()`**。项目里三处都这么写：
   - `HomeViewModel.canAnalyze()` — 导入前置校验
   - `AnalyzeResumeWorker` — 空则跳过 AI 分析
   - `ProfileScreen` — 「已上传 / 未上传」副标题与识别文本区块的显示
2. **不要**新增基于 `targetRole` / `skills` 的可用性判断。需要岗位/技能信息时，走记忆工具（`list_memory_profiles` → `load_profile_overview`）而不是读这两个字段。
3. 注意校验的**严厉程度是刻意区分的**：Key 未配置是硬拦截（不入队），简历为空只弹 toast 继续跑（按无简历模式分析）。新增校验时沿用这个分级，别把简历也升级成硬拦截。

## 证据

- `HomeViewModel.canAnalyze()` 里 `resumeRepository.profile.first().rawText.isNotBlank()` 与其上方注释「只要配置了 API Key 即可分析」。
- `AnalyzeResumeWorker` 的 `if (rawText.isBlank()) { … setNeedsAiAnalysis(false) }`。
- `ProfileScreen` 的 `profile.rawText.isBlank() -> "未上传"`。
- `docs/plan/roadmap.md` 「简历页改版」条目记录了这次判据变更及其动机（避免导入误拦）。

## 触发条件

任何需要"用户有没有简历/画像"的新功能：相关性重算、简历点评、面试建议、引导流程。
