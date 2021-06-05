package gregc.gregchess

import java.time.Duration

interface TimeManager {
    interface CancellableContext{
        fun cancel()
    }
    fun runTaskLater(delay: Duration, callback: () -> Unit)
    fun runTaskTimer(delay: Duration, period: Duration, callback: CancellableContext.() -> Unit)
    fun runTaskAsynchronously(callback: () -> Unit)
}