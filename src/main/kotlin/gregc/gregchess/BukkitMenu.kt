package gregc.gregchess

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import kotlin.reflect.KProperty1

abstract class BukkitMenu<T : Any>(val namePath: ConfigVal<String>) {

    inner class BukkitMenuHolder : InventoryHolder {

        private val content = getContent()
        private val inv = Bukkit.createInventory(this, content.size - content.size % 9 + 9, namePath.get())

        var finished: Boolean = false

        init {
            content.forEach { (item, _, pos) -> inv.setItem(pos.index, item) }
        }

        override fun getInventory() = inv

        fun applyEvent(choice: InventoryPosition): Boolean {
            content.forEach { (_, v, pos) ->
                if (pos == choice) {
                    onClick(v)
                    finished = true
                }
            }
            return finished
        }

        fun cancel() {
            finished = true
            onCancel()
        }
    }

    constructor(namePath: KProperty1<MessageConfig, String>): this(namePath.path)
    abstract fun getContent(): List<ScreenOption<T>>
    abstract fun onClick(v: T)
    abstract fun onCancel()

    fun openFor(p: Player) {
        p.openInventory(BukkitMenuHolder().inventory)
    }
}

data class InventoryPosition(val x: Int, val y: Int) {
    val index get() = x + y * 9

    companion object {
        fun fromIndex(index: Int) = InventoryPosition(index % 9, index.div(9))
    }
}

data class ScreenOption<T>(val item: ItemStack, val value: T, val position: InventoryPosition)


fun Player.openMenu(m: BukkitMenu<*>) = m.openFor(this)