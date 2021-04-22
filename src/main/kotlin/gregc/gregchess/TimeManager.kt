package gregc.gregchess

import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitRunnable
import java.time.Duration

object TimeManager {

    class CancellableContext internal constructor(private val r: BukkitRunnable) {
        fun cancel() {
            r.cancel()
        }
    }

    fun runTaskLater(delay: Duration, callback: () -> Unit) {
        Bukkit.getScheduler().runTaskLater(GregInfo.plugin, Runnable(callback), delay.toTicks())
    }

    fun runTaskTimer(delay: Duration, period: Duration, callback: CancellableContext.() -> Unit) {
        object : BukkitRunnable() {
            val cc = CancellableContext(this)
            override fun run() {
                cc.callback()
            }
        }.runTaskTimer(GregInfo.plugin, delay.toTicks(), period.toTicks())
    }
}