package gregc.gregchess

import java.time.Duration
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

interface TimeManager {
    interface CancellableContext {
        fun cancel()
    }

    fun runTaskLater(delay: Duration, callback: () -> Unit)
    fun runTaskTimer(delay: Duration, period: Duration, callback: CancellableContext.() -> Unit)
    fun runTaskAsynchronously(callback: () -> Unit)
}

suspend fun TimeManager.wait(delay: Duration) = suspendCoroutine<Unit> {
    runTaskLater(delay) {
        it.resume(Unit)
    }
}