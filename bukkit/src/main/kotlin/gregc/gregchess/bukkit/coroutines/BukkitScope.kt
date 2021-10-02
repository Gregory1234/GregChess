package gregc.gregchess.bukkit.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.bukkit.plugin.Plugin
import kotlin.coroutines.CoroutineContext

class BukkitScope(private val plugin: Plugin, private val bukkitContext: BukkitContext) : CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = BukkitDispatcher(plugin, bukkitContext) + SupervisorJob()
}