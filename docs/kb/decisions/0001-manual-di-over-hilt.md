---
trust: L1
anchors:
  - app/src/main/java/com/jk/offermate/di/AppContainer.kt
  - app/src/main/java/com/jk/offermate/OfferMateApplication.kt
verified_commit: 62062a5
verified_at: 2026-09-20
supersedes: null
---

# ADR-0001 手动 DI 组合根，不用 Hilt

## 背景

项目初期规划里写的是 Hilt（`@HiltAndroidApp` + `@Module`）。实际落地时改为手动 DI：单一组合根 `di/AppContainer.kt`（接口 `AppContainer` + 实现 `DefaultAppContainer`），由 `OfferMateApplication` 持有，所有依赖 `by lazy` 装配。

## 备选方案与否决理由

| 方案 | 否决理由 |
|---|---|
| Hilt | 需要 kapt/ksp 注解处理，拖慢增量编译；本项目依赖图是**单一进程、单一组合根、几乎无 scope 分层**，注解处理换来的收益很小；Compose + ViewModel 已经能用工厂手动注入 |
| Koin | 运行时解析，依赖错误推迟到运行时才暴露；手动组合根反而是编译期安全的 |

## 决策

**手动 DI**。新依赖直接在 `DefaultAppContainer` 里 `by lazy` 装配；ViewModel 走各自的 `provideFactory(...)` 显式传参。

## 影响

- 新增依赖只需改一个文件，依赖关系一眼可见（`AppContainer` 就是整个应用的接线图）。
- ViewModel 必须写 `provideFactory`，略啰嗦，但显式。
- **测试友好是主要收益**：几乎所有组件的协作方都以接口或函数注入，JVM 单测可以直接塞 fake，不需要任何 DI 测试框架。这也是项目"测试先行"能跑起来的前提。
- 代价：`AppContainer` 会随功能增长变长，需要靠注释分段维持可读性。

## 后续若要推翻，需要满足什么条件

出现真实的 scope 需求（如按 Activity/导航图划分生命周期的依赖、多进程），且手动传参已经明显失控（同一依赖在超过 5 处手动透传）。单纯"文件变长"不是推翻理由。
