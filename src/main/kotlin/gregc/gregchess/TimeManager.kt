package gregc.gregchess

import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import java.time.Duration

interface TimeManager {
    interface CancellableContext{
        fun cancel()
    }
    fun runTaskLater(delay: Duration, callback: () -> Unit)
    fun runTaskTimer(delay: Duration, period: Duration, callback: CancellableContext.() -> Unit)
    fun runTaskAsynchronously(callback: () -> Unit)
}



class BukkitTimeManager(private val plugin: Plugin): TimeManager {

    private class BukkitCancellableContext constructor(private val r: BukkitRunnable): TimeManager.CancellableContext {
        override fun cancel() {
            r.cancel()
        }
    }

    override fun runTaskLater(delay: Duration, callback: () -> Unit) {
        object : BukkitRunnable() {
            override fun run() {
                callback()
            }
        }.runTaskLater(plugin, delay.toTicks())
    }

    override fun runTaskTimer(delay: Duration, period: Duration, callback: TimeManager.CancellableContext.() -> Unit) {
        object : BukkitRunnable() {
            val cc = BukkitCancellableContext(this)
            override fun run() {
                cc.callback()
            }
        }.runTaskTimer(plugin, delay.toTicks(), period.toTicks())
    }

    override fun runTaskAsynchronously(callback: () -> Unit) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, callback)
    }
}