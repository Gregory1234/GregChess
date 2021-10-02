package gregc.gregchess.bukkit

import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitRunnable
import java.time.Duration
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class CancellableContext internal constructor(private val r: BukkitRunnable) {
    fun cancel() {
        r.cancel()
    }
}

fun runTaskTimer(delay: Duration, period: Duration, callback: CancellableContext.() -> Unit) {
    object : BukkitRunnable() {
        val cc = CancellableContext(this)
        override fun run() {
            cc.callback()
        }
    }.runTaskTimer(GregChess.plugin, delay.toTicks(), period.toTicks())
}

// TODO: make this less confusing
suspend fun toSync() = suspendCoroutine<Unit> {
    Bukkit.getScheduler().runTask(GregChess.plugin, Runnable {
        it.resume(Unit)
    })
}

suspend fun toAsync() = suspendCoroutine<Unit> {
    Bukkit.getScheduler().runTaskAsynchronously(GregChess.plugin, Runnable {
        it.resume(Unit)
    })
}

// TODO: consider switching to kotlin.time
suspend fun wait(delay: Duration) = suspendCoroutine<Unit> {
    if (delay.isZero) {
        it.resume(Unit)
    } else {
        object : BukkitRunnable() {
            override fun run() {
                it.resume(Unit)
            }
        }.runTaskLater(GregChess.plugin, delay.toTicks())
    }
}

suspend fun waitTick() = wait(1.ticks)