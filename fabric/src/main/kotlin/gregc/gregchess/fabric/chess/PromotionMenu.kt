package gregc.gregchess.fabric.chess

import gregc.gregchess.chess.piece.Piece
import gregc.gregchess.chess.piece.PieceRegistryView
import gregc.gregchess.fabric.*
import io.github.cottonmc.cotton.gui.SyncedGuiDescription
import io.github.cottonmc.cotton.gui.client.CottonInventoryScreen
import io.github.cottonmc.cotton.gui.networking.NetworkSide
import io.github.cottonmc.cotton.gui.networking.ScreenNetworking
import io.github.cottonmc.cotton.gui.widget.WButton
import io.github.cottonmc.cotton.gui.widget.WGridPanel
import io.github.cottonmc.cotton.gui.widget.data.Insets
import io.github.cottonmc.cotton.gui.widget.icon.ItemIcon
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.screen.*
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.text.TranslatableText
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

class PromotionMenuGuiDescription(
    syncId: Int, playerInventory: PlayerInventory?, context: ScreenHandlerContext = ScreenHandlerContext.EMPTY,
    private val promotionContinuation: Continuation<Piece?>? = null
) : SyncedGuiDescription(
    GregChessMod.PROMOTION_MENU_HANDLER_TYPE, syncId, playerInventory,
    null, getBlockPropertyDelegate(context, 0)
) {

    private val root = WGridPanel()

    private var index = 0

    private fun addButton(p: Piece, available: Boolean) {
        val button = WButton(ItemIcon(p.item), p.item.name)
        button.onClick = Runnable {
            ScreenNetworking.of(this, NetworkSide.CLIENT).send(ident("promoted")) {
                it.writeString(p.key.toString())
            }
        }
        button.isEnabled = available
        root.add(button, 1, 2 * (++index) - 1, 6, 1)
    }

    private fun addButtons(p: Collection<Piece>, av: Collection<Piece>) {
        p.forEach {
            addButton(it, it in av)
        }
        root.validate(this)
    }

    init {
        setRootPanel(root)
        root.setSize(150, 200)
        root.insets = Insets.ROOT_PANEL

        ScreenNetworking.of(this, NetworkSide.SERVER).receive(ident("promoted")) {
            promotionContinuation?.resume(PieceRegistryView[it.readString().toKey()])
            (playerInventory?.player as ServerPlayerEntity).closeHandledScreen()
        }

        ScreenNetworking.of(this, NetworkSide.SERVER).receive(ident("promoted_default")) {
            promotionContinuation?.resume(null)
        }

        ScreenNetworking.of(this, NetworkSide.CLIENT).receive(ident("setup")) {
            val promotions: List<Piece> = it.readList { p -> PieceRegistryView[ p.readString().toKey() ] }
            val availablePromotions: List<Piece> = it.readList { p -> PieceRegistryView[ p.readString().toKey() ] }
            addButtons(promotions, availablePromotions)
        }

        ScreenNetworking.of(this, NetworkSide.CLIENT).send(ident("setup_request")) { }
    }

    internal fun onClose() =
        ScreenNetworking.of(this, NetworkSide.CLIENT).send(ident("promoted_default")) { }

}

class PromotionMenu(gui: PromotionMenuGuiDescription?, player: PlayerEntity?, title: Text?) :
    CottonInventoryScreen<PromotionMenuGuiDescription?>(gui, player, title) {

    override fun close() {
        (this.description as? PromotionMenuGuiDescription)?.onClose()
        super.close()
    }
}

class PromotionMenuFactory(
    private val promotions: List<Piece>,
    private val availablePromotions: List<Piece>,
    private val world: World, private val pos: BlockPos,
    private val promotionContinuation: Continuation<Piece?>
) : NamedScreenHandlerFactory {
    override fun getDisplayName(): Text = TranslatableText("gui.gregchess.promotion_menu")

    override fun createMenu(syncId: Int, inv: PlayerInventory?, player: PlayerEntity?): ScreenHandler {
        return PromotionMenuGuiDescription(syncId, inv, ScreenHandlerContext.create(world, pos), promotionContinuation).apply {
            ScreenNetworking.of(this, NetworkSide.SERVER).receive(ident("setup_request")) {
                ScreenNetworking.of(this, NetworkSide.SERVER).send(ident("setup")) {
                    it.writeCollection(promotions) { buf, p ->
                        buf.writeString(p.key.toString())
                    }
                    it.writeCollection(availablePromotions) { buf, p ->
                        buf.writeString(p.key.toString())
                    }
                }
            }
        }
    }
}