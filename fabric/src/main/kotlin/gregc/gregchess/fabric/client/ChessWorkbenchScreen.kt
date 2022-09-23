package gregc.gregchess.fabric.client

import gregc.gregchess.CoreRegistry
import gregc.gregchess.fabric.*
import gregc.gregchess.fabric.piece.item
import gregc.gregchess.fabricutils.DefaultedListInventory
import gregc.gregchess.piece.*
import io.github.cottonmc.cotton.gui.SyncedGuiDescription
import io.github.cottonmc.cotton.gui.ValidatedSlot
import io.github.cottonmc.cotton.gui.client.BackgroundPainter
import io.github.cottonmc.cotton.gui.client.CottonInventoryScreen
import io.github.cottonmc.cotton.gui.networking.NetworkSide
import io.github.cottonmc.cotton.gui.networking.ScreenNetworking
import io.github.cottonmc.cotton.gui.widget.*
import io.github.cottonmc.cotton.gui.widget.data.Insets
import io.github.cottonmc.cotton.gui.widget.icon.ItemIcon
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.recipe.RecipeUnlocker
import net.minecraft.screen.ScreenHandlerContext
import net.minecraft.tag.TagKey
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry
import java.util.function.Predicate
import kotlin.math.min

class WChessWorkbenchResultSlot(
    inventory: Inventory, val input: Inventory, val player: PlayerEntity?, index: Int, val justCrafted: (Boolean) -> Unit
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
                justCrafted(true)
                for (i in 0..1) {
                    input.removeStack(i, 1)
                }
                justCrafted(false)
            }
        }
    }
}

class WGridListPanel : WGridPanel() {
    fun clear() {
        children.clear()
    }
}

class ChessWorkbenchGuiDescription(
    syncId: Int, playerInventory: PlayerInventory?, private val context: ScreenHandlerContext = ScreenHandlerContext.EMPTY
) : SyncedGuiDescription(
    GregChessMod.CHESS_WORKBENCH_SCREEN_HANDLER_TYPE, syncId, playerInventory,
    null, getBlockPropertyDelegate(context, 0)
) {
    private val bases = TagKey.of(Registry.ITEM_KEY, ident("piece_base"))
    private val whiteDyes = TagKey.of(Registry.ITEM_KEY, Identifier("c", "dye_white")) // TODO: switch to gregchess tags
    private val blackDyes = TagKey.of(Registry.ITEM_KEY, Identifier("c", "dye_black"))

    private val input = DefaultedListInventory(2, ::onInputChanged)
    private val output = DefaultedListInventory(1)

    private val recipeList = mutableListOf<Piece>()
    private var selected: Piece? = null

    private val recipeListPanel: WGridListPanel

    private var justCrafted = false


    init {
        // TODO: add scrolling in case of too many items
        // TODO: style the buttons better
        val root = WGridPanel(9)
        setRootPanel(root)
        root.insets = Insets.ROOT_PANEL

        root.add(WChessWorkbenchResultSlot(output, input, playerInventory?.player, 0) { justCrafted = it }, 15, 3)
        root.add(WItemSlot(input, 0, 1, 1, false).apply { filter = Predicate { it.isIn(bases) } }, 1, 1)
        root.add(WItemSlot(input, 1, 1, 1, false).apply { filter = Predicate { it.isIn(whiteDyes) || it.isIn(blackDyes) } }, 1, 5)

        recipeListPanel = WGridListPanel()

        recipeListPanel.backgroundPainter = BackgroundPainter.createColorful(0xff51493a.toInt())

        root.add(recipeListPanel, 4, 1, 9, 6)

        root.add(this.createPlayerInventoryPanel(), 0, 8)

        ScreenNetworking.of(this, NetworkSide.SERVER).receive(ident("select")) {
            val s = it.readString()
            for (p in recipeList) {
                if (p.id.toString() == s) {
                    selected = p
                    output.setStack(0, p.item.defaultStack)
                    break
                }
            }
        }
        ScreenNetworking.of(this, NetworkSide.SERVER).receive(ident("unselect")) {
            println("unselect")
            output.clear()
            selected = null
        }

        root.validate(this)
    }

    private fun readdRecipes() {
        recipeListPanel.clear()
        for ((y, row) in recipeList.chunked(4).withIndex()) {
            for ((x, p) in row.withIndex()) {
                val button = WButton(ItemIcon(p.item))
                button.onClick = Runnable {
                    output.setStack(0, p.item.defaultStack)
                    selected = p
                    ScreenNetworking.of(this, NetworkSide.CLIENT).send(ident("select")) {
                        it.writeString(p.id.toString())
                    }
                }
                recipeListPanel.add(button, x, y, 1, 1)
            }
        }
        if (selected !in recipeList) {
            selected = null
            if (!output.isEmpty && !justCrafted) {
                output.clear()
                ScreenNetworking.of(this, NetworkSide.CLIENT).send(ident("unselect")) {}
            }
        } else if (selected != null) {
            output.setStack(0, selected!!.item.defaultStack)
            ScreenNetworking.of(this, NetworkSide.CLIENT).send(ident("select")) {
                it.writeString(selected!!.id.toString())
            }
        }
        recipeListPanel.validate(this)
    }

    private fun onInputChanged() {

        recipeList.clear()

        if (input.getStack(0).isIn(bases) && input.getStack(1).isIn(whiteDyes)) {
            recipeList.addAll(CoreRegistry.PIECE_TYPE.values.map(::white))
        }
        if (input.getStack(0).isIn(bases) && input.getStack(1).isIn(blackDyes)) {
            recipeList.addAll(CoreRegistry.PIECE_TYPE.values.map(::black))
        }

        readdRecipes()
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
