package gregc.gregchess

import gregc.gregchess.ConfigPath
import gregc.gregchess.Configurator
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

abstract class Screen<T>(val namePath: ConfigPath<String>) {
    abstract fun getContent(config: Configurator): List<ScreenOption<T>>
    abstract fun onClick(v: T)
    abstract fun onCancel()
}

data class InventoryPosition(val x: Int, val y: Int) {
    val index = x + y * 9

    companion object {
        fun fromIndex(index: Int) = InventoryPosition(index % 9, index.div(9))
    }
}

data class ScreenOption<T>(val item: ItemStack, val value: T, val position: InventoryPosition)

class BukkitScreen<T> internal constructor(private val screen: Screen<T>, config: Configurator) : InventoryHolder {
    private val content = screen.getContent(config)
    private val inv = Bukkit.createInventory(this,content.size - content.size % 9 + 9, screen.namePath.get(config))

    var finished: Boolean = false

    init {
        content.forEach { (item, _, pos) -> inv.setItem(pos.index, item) }
    }

    override fun getInventory() = inv

    fun applyEvent(choice: InventoryPosition): Boolean {
        content.forEach { (_, v, pos) ->
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

fun Player.openScreen(config: Configurator, s: Screen<*>) = openInventory(BukkitScreen(s, config).inventory)