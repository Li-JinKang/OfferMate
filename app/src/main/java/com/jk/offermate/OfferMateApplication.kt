package com.jk.offermate

import android.app.Application
import com.jk.offermate.di.AppContainer
import com.jk.offermate.di.DefaultAppContainer

/**
 * Application 持有全局依赖容器（手动 DI 的组合根）。
 */
class OfferMateApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer()
    }
}
