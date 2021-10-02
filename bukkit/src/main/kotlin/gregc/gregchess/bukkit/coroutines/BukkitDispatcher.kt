package gregc.gregchess.bukkit.coroutines

import kotlinx.coroutines.*
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume

@OptIn(InternalCoroutinesApi::class)
class BukkitDispatcher(private val plugin: Plugin, private val bukkitContext: BukkitContext)
    : CoroutineDispatcher(), Delay {

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (!context.isActive) {
            return
        }

        when(bukkitContext) {
            BukkitContext.SYNC -> Bukkit.getScheduler().runTask(plugin, block)
            BukkitContext.ASYNC -> Bukkit.getScheduler().runTaskAsynchronously(plugin, block)
        }
    }

    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        val task = Runnable {
            continuation.resume(Unit)
        }
        when(bukkitContext) {
            BukkitContext.SYNC -> Bukkit.getScheduler().runTaskLater(plugin, task, timeMillis / 50)
            BukkitContext.ASYNC -> Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, timeMillis / 50)
        }
    }
}

enum class BukkitContext {
    SYNC, ASYNC
}