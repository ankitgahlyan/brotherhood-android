package com.tonapps.wallet.features.brotherhood

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.internal.MainDispatcherFactory

@OptIn(InternalCoroutinesApi::class)
class TestMainDispatcherFactory : MainDispatcherFactory {
    override val loadPriority: Int = Int.MAX_VALUE

    override fun createDispatcher(allFactories: List<MainDispatcherFactory>): MainCoroutineDispatcher {
        return object : MainCoroutineDispatcher() {
            override val immediate: MainCoroutineDispatcher = this

            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
                Dispatchers.Unconfined.dispatch(context, block)
            }
        }
    }
}
