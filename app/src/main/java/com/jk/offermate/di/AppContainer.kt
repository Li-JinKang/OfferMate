package com.jk.offermate.di

import com.jk.offermate.data.repository.FakePostRepository
import com.jk.offermate.domain.repository.PostRepository

/**
 * 应用级依赖容器（手动依赖注入）。
 *
 * 用轻量的容器代替重型 DI 框架：集中构造与持有依赖，向上层暴露**接口**，
 * 便于替换实现与测试。后续可在此加入 ContentReader、AiClient、Room 等。
 */
interface AppContainer {
    val postRepository: PostRepository
}

class DefaultAppContainer : AppContainer {
    override val postRepository: PostRepository by lazy { FakePostRepository() }
}
