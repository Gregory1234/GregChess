package gregc.gregchess.bukkit

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine

suspend fun <T> Player.openMenu(name: Message, content: List<ScreenOption<T>>): T? = suspendCoroutine {
    openMenu(Menu(name.get(), it, content, null))
}

class Menu<T>(
    val name: String,
    private val cont: Continuation<T>,
    private val content: List<ScreenOption<T>>,
    private val default: T
) : InventoryHolder {
    private val inv = Bukkit.createInventory(this, content.size - content.size % 9 + 9, name)
    var finished: Boolean = false
        private set

    init {
        for ((item, _, pos) in content)
            inv.setItem(pos.index, item)
    }

    fun click(choice: InvPos): Boolean {
        content.firstOrNull { (_, _, pos) -> pos == choice }?.value?.let {
            finished = true
            click(it)
        }
        return finished
    }

    override fun getInventory() = inv

    private fun click(v: T) {
        cont.resumeWith(Result.success(v))
    }

    fun cancel() {
        finished = true
        cont.resumeWith(Result.success(default))
    }
}

data class InvPos(val x: Int, val y: Int) {
    val index get() = x + y * 9
}

fun Int.toInvPos() = InvPos(this % 9, this.div(9))

data class ScreenOption<out T>(val item: ItemStack, val value: T, val position: InvPos)


fun Player.openMenu(m: Menu<*>) = openInventory(m.inventory)