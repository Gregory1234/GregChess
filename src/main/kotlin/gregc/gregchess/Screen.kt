package gregc.gregchess

import org.bukkit.Bukkit
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import kotlin.reflect.KClass

abstract class Screen<T: Any>(val cl: KClass<T>, val namePath: ConfigPath<String>) {
    abstract fun getContent(config: Configurator): List<ScreenOption<T>>
    abstract fun onClick(v: T)
    abstract fun onCancel()
}

data class InventoryPosition(val x: Int, val y: Int) {
    val index get() = x + y * 9

    companion object {
        fun fromIndex(index: Int) = InventoryPosition(index % 9, index.div(9))
    }
}

data class ScreenOption<T>(val value: T, val position: InventoryPosition)

class BukkitScreen<T: Any> internal constructor(private val screen: Screen<T>, config: Configurator) : InventoryHolder {
    companion object {
        private val renderers = mutableMapOf<KClass<*>,Any.(Configurator) -> ItemStack>()
        private fun <T : Any> render(cl: KClass<T>, c: Configurator, v: T): ItemStack = with(v) {renderers[cl]!!(c)}
        fun <T : Any> addRendererAny(cl: KClass<T>, r: Any.(Configurator) -> ItemStack) {
            renderers[cl] = r
        }
        @Suppress("unchecked_cast")
        inline fun <T : Any> addRenderer(cl: KClass<T>, crossinline r: T.(Configurator) -> ItemStack) = addRendererAny(cl) {
            (this as T).r(it)
        }
        inline fun <reified T: Any> addRenderer(crossinline r: T.(Configurator) -> ItemStack) = addRenderer(T::class, r)
    }

    private val content = screen.getContent(config)
    private val inv = Bukkit.createInventory(this,content.size - content.size % 9 + 9, screen.namePath.get(config))

    var finished: Boolean = false

    init {
        content.forEach { (v, pos) -> inv.setItem(pos.index, render(screen.cl, config, v)) }
    }

    override fun getInventory() = inv

    fun applyEvent(choice: InventoryPosition): Boolean {
        content.forEach { (v, pos) ->
            if (pos == choice) {
                screen.onClick(v)
                finished = true
            }
        }
        return finished
    }

    fun cancel() {
        finished = true
        screen.onCancel()
    }
}