package gregc.gregchess.fabricutils.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

class FabricScope : CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = FabricDispatcher() + SupervisorJob()
}