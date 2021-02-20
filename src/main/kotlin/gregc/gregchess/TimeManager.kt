package gregc.gregchess

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import java.time.Duration

class TimeManager(private val plugin: JavaPlugin) {
    class CancellableContext internal constructor(private val r: BukkitRunnable){
        fun cancel(){
            r.cancel()
        }
    }

    fun runTaskLater(delay: Duration, callback: () -> Unit) {
        Bukkit.getScheduler().runTaskLater(plugin, Runnable(callback), delay.toTicks())
    }

    fun runTaskTimer(delay: Duration, period: Duration, callback: CancellableContext.() -> Unit) {
        object : BukkitRunnable(){
            val cc = CancellableContext(this)
            override fun run(){
                cc.callback()
            }
        }.runTaskTimer(plugin, delay.toTicks(), period.toTicks())
    }
}