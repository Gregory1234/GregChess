package gregc.gregchess.fabric

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.Inventories
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.util.collection.DefaultedList

class DefaultedListInventory(slots: Int, private val onChanged: (() -> Unit)? = null) : Inventory {

    private val content: DefaultedList<ItemStack> = DefaultedList.ofSize(slots, ItemStack.EMPTY)

    override fun clear() {
        content.clear()
        onChanged?.invoke()
    }

    override fun size(): Int = content.size

    override fun isEmpty(): Boolean = content.all { it?.isEmpty == true }

    override fun getStack(slot: Int): ItemStack = content[slot]

    override fun removeStack(slot: Int, amount: Int): ItemStack {
        val result = Inventories.splitStack(content, slot, amount)
        if (!result.isEmpty) {
            onChanged?.invoke()
        }
        return result
    }

    override fun removeStack(slot: Int): ItemStack {
        val result = Inventories.removeStack(content, slot)
        onChanged?.invoke()
        return result
    }

    override fun setStack(slot: Int, stack: ItemStack) {
        val overriden = content[slot]
        if (overriden != stack && !(overriden.isItemEqual(stack) && overriden.count == maxCountPerStack && stack.count > maxCountPerStack)) {
            content[slot] = stack
            if (stack.count > maxCountPerStack) {
                stack.count = maxCountPerStack
            }
            onChanged?.invoke()
        }
    }

    override fun markDirty() {
    }

    override fun canPlayerUse(player: PlayerEntity?): Boolean = true

}