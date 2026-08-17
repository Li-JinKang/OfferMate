package com.jk.offermate.ui.quiz

import com.jk.offermate.data.ai.AnsweredQuestion
import com.jk.offermate.data.ai.QuestionSource

/**
 * 分类归并：把 AI 产出的细粒度标签（如 handler、sharedpreference）按关键词归并到**粗类目**，
 * 避免题库分类过多、过散。纯逻辑，可 JVM 单测。
 *
 * 规则：把题目的标签 + 题干拼成检索串，按 [TAXONOMY] 顺序命中第一个类目即返回；
 * 都不命中则回退到首个原标签（保留特异性），再不行为"其他"。
 *
 * 手动添加的题目尊重用户所填分类，不做归并。
 */
object CategoryCanonicalizer {

    // 顺序即优先级；关键词均为小写（中文不受 lowercase 影响）
    private val TAXONOMY: List<Pair<String, List<String>>> = listOf(
        "Android" to listOf(
            "android", "activity", "fragment", "handler", "looper", "messagequeue", "binder", "aidl",
            "sharedpreference", "sharedpreferences", "room", "jetpack", "compose", "recyclerview",
            "listview", "自定义view", "view绘制", "measure", "layout", "draw", "lifecycle", "livedata",
            "viewmodel", "service", "broadcast", "contentprovider", "intent", "anr", "apk", "aar",
            "gradle", "okhttp", "retrofit", "glide", "coil", "dalvik", "art", "内存泄漏", "卡顿",
            "启动优化", "hook", "插件化", "热修复", "屏幕适配", "事件分发", "touch", "window", "wms", "ams"
        ),
        "Kotlin" to listOf(
            "kotlin", "协程", "coroutine", "flow", "suspend", "委托", "高阶函数", "扩展函数",
            "data class", "密封类", "sealed", "内联", "inline", "空安全"
        ),
        "Java" to listOf(
            "java", "jvm", "gc", "垃圾回收", "反射", "注解", "泛型", "集合", "hashmap",
            "concurrenthashmap", "arraylist", "synchronized", "volatile", "aqs", "线程池",
            "threadlocal", "类加载", "字节码", "cas", "锁升级"
        ),
        "计算机网络" to listOf(
            "网络", "tcp", "udp", "http", "https", "三次握手", "四次挥手", "dns", "cdn",
            "websocket", "tls", "ssl", "cookie", "session", "restful", "拥塞控制", "滑动窗口"
        ),
        "操作系统" to listOf(
            "操作系统", "进程", "线程", "内存管理", "虚拟内存", "分页", "分段", "死锁",
            "信号量", "互斥", "用户态", "内核态", "中断", "上下文切换", "零拷贝"
        ),
        "数据结构与算法" to listOf(
            "算法", "数据结构", "排序", "二分", "链表", "二叉树", "红黑树", "图", "动态规划",
            "dp", "贪心", "回溯", "递归", "哈希", "栈", "队列", "堆", "leetcode", "时间复杂度", "字符串匹配"
        ),
        "数据库" to listOf(
            "数据库", "sql", "mysql", "索引", "事务", "隔离级别", "redis", "sqlite", "b+树",
            "乐观锁", "悲观锁", "分库分表", "范式"
        ),
        "系统设计" to listOf(
            "系统设计", "架构", "设计模式", "单例", "工厂", "观察者", "责任链", "mvc", "mvp",
            "mvvm", "mvi", "高并发", "限流", "缓存", "消息队列", "分布式", "幂等"
        )
    )

    /**
     * 题目的展示类目：优先用 LLM 归好的 [AnsweredQuestion.category]；
     * 为空时（旧数据/离线/无 Key）回退到本地启发式归并。
     */
    fun displayCategory(question: AnsweredQuestion): String {
        val assigned = question.category.trim()
        if (assigned.isNotEmpty()) return assigned
        return categoryOf(question)
    }

    /** 本地启发式归并（无 LLM 分类时的兜底）。手动题用用户所填标签。 */
    fun categoryOf(question: AnsweredQuestion): String =
        if (question.source == QuestionSource.MANUAL) {
            question.tags.firstOrNull()?.takeIf { it.isNotBlank() } ?: OTHER
        } else {
            canonical(question.tags, question.question)
        }

    /** 按关键词把标签/题干归并为粗类目。 */
    fun canonical(tags: List<String>, question: String = ""): String {
        val haystack = (tags.joinToString(" ") + " " + question).lowercase()
        for ((category, keywords) in TAXONOMY) {
            if (keywords.any { haystack.contains(it) }) return category
        }
        return tags.firstOrNull()?.takeIf { it.isNotBlank() } ?: OTHER
    }

    const val OTHER = "其他"
}
