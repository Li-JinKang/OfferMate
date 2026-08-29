package com.jk.offermate.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/**
 * 生成 Baseline Profile：把启动与「滚动 AI 对话」这两条路径实际跑一遍，
 * 采集到的类/方法会被 ART 在安装时 AOT 编译，避免运行时 JIT 抢 CPU。
 *
 * 为什么对本项目特别重要 —— Markdown 渲染每个文本节点都要重建 AnnotatedString 并挂上一串修饰符，
 * LazyColumn 滚动时又要在一帧内组合 + 测量新进入视口的 item。这些代码路径没有预编译时全靠 JIT，
 * 表现就是滚动掉帧（trace 里 Jit thread pool 吃掉大半帧时间）。
 *
 * 运行：`./gradlew :app:generateReleaseBaselineProfile`（需连接 API 33+ 真机或 root 设备）。
 * 产物落在 `app/src/release/generated/baselineProfiles/`，需要提交到仓库。
 *
 * 注意：采集到什么完全取决于**设备上真实跑过什么**。要让 Markdown 的热路径进入 profile，
 * 设备上必须已有一段带内容的 AI 对话；否则这里只会滚到空列表，profile 覆盖不到渲染代码。
 */
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.pkg(PACKAGE).depth(0)), TIMEOUT_MS)

        // AI 对话 Tab：Markdown 渲染与聊天列表的热路径都在这里
        device.findObject(By.text("AI对话"))?.click()
        device.waitForIdle()

        repeat(SCROLL_ROUNDS) {
            val list = device.wait(Until.findObject(By.scrollable(true)), TIMEOUT_MS) ?: return@repeat
            // 留出手势边距，避免从屏幕边缘起手被系统返回手势拦掉
            list.setGestureMargin(device.displayWidth / 5)
            list.fling(Direction.DOWN)
            device.waitForIdle()
            list.fling(Direction.UP)
            device.waitForIdle()
        }
    }

    private companion object {
        const val PACKAGE = "com.jk.offermate"
        const val TIMEOUT_MS = 5_000L
        const val SCROLL_ROUNDS = 3
    }
}
