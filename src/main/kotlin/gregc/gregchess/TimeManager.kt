package gregc.gregchess

import org.bukkit.scheduler.BukkitRunnable
import java.time.Duration

object TimeManager {

    class CancellableContext constructor(private val r: BukkitRunnable) {
        fun cancel() {
            r.cancel()
        }
    }

    inline fun runTaskLater(delay: Duration, crossinline callback: () -> Unit) {
        object : BukkitRunnable() {
            override fun run() {
                callback()
            }
        }.runTaskLater(GregInfo.plugin, delay.toTicks())
    }

    inline fun runTaskTimer(delay: Duration, period: Duration, crossinline callback: CancellableContext.() -> Unit) {
        object : BukkitRunnable() {
            val cc = CancellableContext(this)
            override fun run() {
                cc.callback()
            }
        }.runTaskTimer(GregInfo.plugin, delay.toTicks(), period.toTicks())
    }
}