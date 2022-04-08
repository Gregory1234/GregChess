package gregc.gregchess.fabric.client

import gregc.gregchess.fabric.DefaultedListInventory
import gregc.gregchess.fabric.GregChessMod
import io.github.cottonmc.cotton.gui.SyncedGuiDescription
import io.github.cottonmc.cotton.gui.ValidatedSlot
import io.github.cottonmc.cotton.gui.client.CottonInventoryScreen
import io.github.cottonmc.cotton.gui.widget.WGridPanel
import io.github.cottonmc.cotton.gui.widget.WItemSlot
import io.github.cottonmc.cotton.gui.widget.data.Insets
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.recipe.RecipeUnlocker
import net.minecraft.screen.ScreenHandlerContext
import net.minecraft.text.Text
import kotlin.math.min

class WChessWorkbenchResultSlot(
    inventory: Inventory, val input: Inventory, val player: PlayerEntity?, index: Int
) : WItemSlot(inventory, index, 1, 1, true) {

    override fun isInsertingAllowed(): Boolean = false

    override fun createSlotPeer(inventory: Inventory?, index: Int, x: Int, y: Int): ValidatedSlot {
        return object : ValidatedSlot(inventory, index, x, y) {
            private var amount = 0

            override fun canInsert(stack: ItemStack?): Boolean = false

            override fun takeStack(amount: Int): ItemStack? {
                if (hasStack()) {
                    this.amount += min(amount, this.stack.count)
                }
                return super.takeStack(amount)
            }

            override fun onCrafted(stack: ItemStack, amount: Int) {
                this.amount += amount
                this.onCrafted(stack)
            }

            override fun onTake(amount: Int) {
                this.amount += amount
            }

            override fun onCrafted(stack: ItemStack) {
                if (amount > 0) {
                    if (player != null)
                        stack.onCraft(player.world, player, amount)
                }
                if (this.inventory is RecipeUnlocker) {
                    (this.inventory as RecipeUnlocker).unlockLastRecipe(player)
                }
                amount = 0
            }

            override fun onTakeItem(player: PlayerEntity, stack: ItemStack) {
                this.onCrafted(stack)
                for (i in 0..1) {
                    input.removeStack(i, 1)
                }
            }
        }
    }
}

class ChessWorkbenchGuiDescription(
    syncId: Int, playerInventory: PlayerInventory?, private val context: ScreenHandlerContext = ScreenHandlerContext.EMPTY
) : SyncedGuiDescription(
    GregChessMod.CHESS_WORKBENCH_SCREEN_HANDLER_TYPE, syncId, playerInventory,
    null, getBlockPropertyDelegate(context, 0)
) {

    private val input = DefaultedListInventory(2, ::onInputChanged)
    private val output = DefaultedListInventory(1)


    init {
        val root = WGridPanel()
        setRootPanel(root)
        root.insets = Insets.ROOT_PANEL

        root.add(WChessWorkbenchResultSlot(output, input, playerInventory?.player, 0), 6, 2)
        root.add(WItemSlot(input, 0, 1, 1, false), 1, 2)
        root.add(WItemSlot(input, 1, 1, 1, false), 3, 2)

        root.add(this.createPlayerInventoryPanel(), 0, 4)

        root.validate(this)
    }

    private fun onInputChanged() {
        if (input.getStack(0).isItemEqual(ItemStack(Items.BEDROCK))
            && input.getStack(1).isItemEqual(ItemStack(Items.BEDROCK))) {
            output.setStack(0, ItemStack(Items.BEDROCK))
        } else {
            output.removeStack(0)
        }
    }

    override fun close(player: PlayerEntity?) {
        super.close(player)
        context.run { _, _ ->
            dropInventory(player, input)
        }
    }

    override fun canUse(entity: PlayerEntity?): Boolean = canUse(context, entity, GregChessMod.CHESS_WORKBENCH_BLOCK)

    override fun transferSlot(player: PlayerEntity, index: Int): ItemStack {
        var ret = ItemStack.EMPTY
        val slot = slots[index]
        if (slot.hasStack()) {
            val slotItem = slot.stack
            ret = slotItem.copy()
            if (index == 0) {
                context.run { world, _ ->
                    slotItem.item.onCraft(slotItem, world, player)
                }
                if (!this.insertItem(slotItem, 3, 39, true)) {
                    return ItemStack.EMPTY
                }
                slot.onQuickTransfer(slotItem, ret)
            } else if (index in 3 until 39) {
                if (!this.insertItem(slotItem, 1, 3, false)) {
                    if (index < 37) {
                        if (!this.insertItem(slotItem, 30, 39, false)) {
                            return ItemStack.EMPTY
                        }
                    } else if (!this.insertItem(slotItem, 3, 30, false)) {
                        return ItemStack.EMPTY
                    }
                }
            } else if (!this.insertItem(slotItem, 3, 39, false)) {
                return ItemStack.EMPTY
            }
            if (slotItem.isEmpty) {
                slot.stack = ItemStack.EMPTY
            } else {
                slot.markDirty()
            }
            if (slotItem.count == ret.count) {
                return ItemStack.EMPTY
            }
            slot.onTakeItem(player, slotItem)
            if (index == 0) {
                player.dropItem(slotItem, false)
            }
        }
        return ret
    }

}

class ChessWorkbenchScreen(gui: ChessWorkbenchGuiDescription?, player: PlayerEntity?, title: Text?) :
    CottonInventoryScreen<ChessWorkbenchGuiDescription?>(gui, player, title)
