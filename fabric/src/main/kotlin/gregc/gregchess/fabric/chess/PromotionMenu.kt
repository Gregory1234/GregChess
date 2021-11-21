package gregc.gregchess.fabric.chess

import gregc.gregchess.chess.piece.Piece
import gregc.gregchess.chess.piece.PieceRegistryView
import gregc.gregchess.fabric.GregChess
import gregc.gregchess.fabric.ident
import gregc.gregchess.registry.toKey
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
import net.minecraft.screen.ScreenHandlerContext
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class PromotionMenuGuiDescription(
    syncId: Int, playerInventory: PlayerInventory?, context: ScreenHandlerContext = ScreenHandlerContext.EMPTY
) : SyncedGuiDescription(
    GregChess.PROMOTION_MENU_HANDLER_TYPE, syncId, playerInventory,
    null, getBlockPropertyDelegate(context, 0)
) {

    private val root = WGridPanel()

    private var index = 0

    private fun addButton(p: Piece) {
        val button = WButton(ItemIcon(p.item), p.item.name)
        button.onClick = Runnable {
            ScreenNetworking.of(this, NetworkSide.CLIENT).send(ident("promoted")) {
                it.writeString(p.key.toString())
            }
        }
        root.add(button, 1, 2 * (++index) - 1, 6, 1)
    }

    private fun addButtons(p: Collection<Piece>) {
        p.forEach {
            addButton(it)
        }
        root.validate(this)
    }

    init {
        setRootPanel(root)
        root.setSize(150, 200)
        root.insets = Insets.ROOT_PANEL

        ScreenNetworking.of(this, NetworkSide.SERVER).receive(ident("promoted")) {
            context.run { world, pos ->
                val entity = world.getBlockEntity(pos)
                if (entity is ChessboardFloorBlockEntity) {
                    entity.chessControllerBlock?.providePromotion(PieceRegistryView[it.readString().toKey()])
                }
            }
            (playerInventory?.player as ServerPlayerEntity).closeHandledScreen()
        }

        ScreenNetworking.of(this, NetworkSide.CLIENT).receive(ident("setup")) {
            println("received")
            addButtons(it.readList { p -> PieceRegistryView[ p.readString().toKey() ] })
        }

        ScreenNetworking.of(this, NetworkSide.CLIENT).send(ident("setup_request")) { }
    }

}

class PromotionMenu(gui: PromotionMenuGuiDescription?, player: PlayerEntity?, title: Text?) :
    CottonInventoryScreen<PromotionMenuGuiDescription?>(gui, player, title)