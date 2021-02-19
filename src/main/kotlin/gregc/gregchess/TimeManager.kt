package gregc.gregchess

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable

class TimeManager(private val plugin: JavaPlugin) {
    class CancellableContext internal constructor(private val r: BukkitRunnable){
        fun cancel(){
            r.cancel()
        }
    }

    fun runTaskLater(delay: Long, callback: () -> Unit) {
        Bukkit.getScheduler().runTaskLater(plugin, Runnable(callback), delay)
    }

    fun runTaskTimer(delay: Long, period: Long, callback: CancellableContext.() -> Unit) {
        object : BukkitRunnable(){
            val cc = CancellableContext(this)
            override fun run(){
                cc.callback()
            }
        }.runTaskTimer(plugin, delay, period)
    }
}