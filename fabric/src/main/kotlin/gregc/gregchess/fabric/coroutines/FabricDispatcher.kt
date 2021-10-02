package gregc.gregchess.fabric.coroutines

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

class FabricDispatcher : CoroutineDispatcher() {

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (!context.isActive) {
            return
        }
        block.run()
    }

}