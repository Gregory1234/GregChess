package gregc.gregchess.bukkit

import gregc.gregchess.TimeManager
import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitRunnable
import java.time.Duration

object BukkitTimeManager : TimeManager {

    private class BukkitCancellableContext constructor(private val r: BukkitRunnable) : TimeManager.CancellableContext {
        override fun cancel() {
            r.cancel()
        }
    }

    override fun runTaskLater(delay: Duration, callback: () -> Unit) {
        if (delay.isZero) {
            callback()
            return
        }
        object : BukkitRunnable() {
            override fun run() {
                callback()
            }
        }.runTaskLater(GregChess.plugin, delay.toTicks())
    }

    override fun runTaskNextTick(callback: () -> Unit) = runTaskLater(1.ticks, callback)

    override fun runTaskTimer(delay: Duration, period: Duration, callback: TimeManager.CancellableContext.() -> Unit) {
        object : BukkitRunnable() {
            val cc = BukkitCancellableContext(this)
            override fun run() {
                cc.callback()
            }
        }.runTaskTimer(GregChess.plugin, delay.toTicks(), period.toTicks())
    }

    override fun runTaskAsynchronously(callback: () -> Unit) {
        Bukkit.getScheduler().runTaskAsynchronously(GregChess.plugin, callback)
    }
}