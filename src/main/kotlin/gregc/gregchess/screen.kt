package gregc.gregchess

import gregc.gregchess.chess.SettingsManager
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

abstract class Screen<T>(val namePath: String) {
    abstract fun getContent(): List<ScreenOption<T>>
    abstract fun onClick(v: T)
    abstract fun onCancel()
    fun create() = Holder(this)
    class Holder<T> internal constructor(private val screen: Screen<T>): InventoryHolder {
        private val inv = Bukkit.createInventory(
            this,
            9,
            ConfigManager.getString(screen.namePath)
        )
        var finished: Boolean = false

        init {
            screen.getContent().forEach { (item, _, pos) -> inv.setItem(pos.index, item) }
        }

        override fun getInventory() = inv

        fun applyEvent(choice: InventoryPosition): Boolean {
            screen.getContent().forEach { (_, v, pos) ->
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
}

data class InventoryPosition(val x: Int, val y: Int) {
    val index = x + y * 9
    companion object {
        fun fromIndex(index: Int) = InventoryPosition(index % 9, index.div(9))
    }
}

data class ScreenOption<T>(val item: ItemStack, val value: T, val position: InventoryPosition)

fun Player.openScreen(s: Screen<*>) = openInventory(s.create().inventory)