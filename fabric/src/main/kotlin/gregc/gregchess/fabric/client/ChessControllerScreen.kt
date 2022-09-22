package gregc.gregchess.fabric.client

import gregc.gregchess.CoreRegistry
import gregc.gregchess.fabric.*
import gregc.gregchess.fabric.block.ChessControllerBlockEntity
import gregc.gregchess.fabric.piece.item
import gregc.gregchess.fabric.player.FabricPlayer
import gregc.gregchess.piece.*
import gregc.gregchess.results.drawBy
import io.github.cottonmc.cotton.gui.SyncedGuiDescription
import io.github.cottonmc.cotton.gui.client.CottonInventoryScreen
import io.github.cottonmc.cotton.gui.networking.NetworkSide
import io.github.cottonmc.cotton.gui.networking.ScreenNetworking
import io.github.cottonmc.cotton.gui.widget.*
import io.github.cottonmc.cotton.gui.widget.data.Insets
import net.minecraft.client.resource.language.I18n
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.item.Items
import net.minecraft.screen.ScreenHandlerContext
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class ChessControllerGuiDescription(
    syncId: Int, playerInventory: PlayerInventory?, context: ScreenHandlerContext = ScreenHandlerContext.EMPTY
) : SyncedGuiDescription(
    GregChessMod.CHESS_CONTROLLER_SCREEN_HANDLER_TYPE, syncId, playerInventory,
    getBlockInventory(context, INVENTORY_SIZE), getBlockPropertyDelegate(context, 1)
) {

    companion object {
        private val pieces: List<Piece> = PieceRegistryView.values.toList()

        @JvmField
        val INVENTORY_SIZE = pieces.size

        fun slotOf(p: Piece) = pieces.indexOf(p)
    }

    init {
        val root = WGridPanel()
        setRootPanel(root)
        root.setSize(300, 300)
        root.insets = Insets.ROOT_PANEL

        val startMatchButton = WButton(Text.translatable("gui.gregchess.start_match"))
        startMatchButton.onClick = Runnable {
            ScreenNetworking.of(this, NetworkSide.CLIENT).send(ident("start_match")) {
                it.writeUuid(playerInventory?.player?.uuid)
            }
        }
        root.add(startMatchButton, 0, 5, 5, 1)

        val abortMatchButton = WButton(Text.translatable("gui.gregchess.abort_match"))
        abortMatchButton.onClick = Runnable {
            ScreenNetworking.of(this, NetworkSide.CLIENT).send(ident("abort_match")) {}
        }
        root.add(abortMatchButton, 0, 7, 5, 1)

        val detectBoardButton = WButton(Text.translatable("gui.gregchess.detect_board"))
        detectBoardButton.onClick = Runnable {
            ScreenNetworking.of(this, NetworkSide.CLIENT).send(ident("detect_board")) {}
        }
        root.add(detectBoardButton, 0, 1, 5, 1)

        val boardStatusLabel = WDynamicLabel {
            I18n.translate(if (propertyDelegate.get(0) == 0) "gui.gregchess.no_chessboard" else "gui.gregchess.has_chessboard")
        }
        root.add(boardStatusLabel, 0, 3, 5, 1)

        val whiteIcon = WItem(Items.WHITE_DYE.defaultStack)
        root.add(whiteIcon, 0, 9)
        val blackIcon = WItem(Items.BLACK_DYE.defaultStack)
        root.add(blackIcon, 0, 10)

        for ((i, piece) in CoreRegistry.PIECE_TYPE.values.withIndex()) {
            val icon = WItem(white(piece).item.defaultStack)
            root.add(icon, i+1, 8)
            val itemSlotWhite = WItemSlot.of(blockInventory, slotOf(white(piece)))
            itemSlotWhite.setFilter { it.item == white(piece).item }
            root.add(itemSlotWhite, i+1, 9)
            val itemSlotBlack = WItemSlot.of(blockInventory, slotOf(black(piece)))
            itemSlotBlack.setFilter { it.item == black(piece).item }
            root.add(itemSlotBlack, i+1, 10)
        }

        root.add(this.createPlayerInventoryPanel(), 0, 12)

        ScreenNetworking.of(this, NetworkSide.SERVER).receive(ident("detect_board")) {
            context.run { world, pos ->
                val entity = world.getBlockEntity(pos)
                if (entity is ChessControllerBlockEntity) {
                    entity.detectBoard()
                }
            }
        }

        ScreenNetworking.of(this, NetworkSide.SERVER).receive(ident("start_match")) {
            context.run { world, pos ->
                val entity = world.getBlockEntity(pos)
                if (entity is ChessControllerBlockEntity && entity.chessboardStartPos != null && entity.currentMatch == null) {
                    val player = world.getPlayerByUuid(it.readUuid()) as ServerPlayerEntity
                    entity.startMatch(FabricPlayer(player), FabricPlayer(player))
                }
            }
        }

        ScreenNetworking.of(this, NetworkSide.SERVER).receive(ident("abort_match")) {
            context.run { world, pos ->
                val entity = world.getBlockEntity(pos)
                if (entity is ChessControllerBlockEntity && entity.chessboardStartPos != null) {
                    entity.currentMatch?.stop(drawBy(GregChess.ABORTED))
                }
            }
        }

        root.validate(this)
    }

}

class ChessControllerScreen(gui: ChessControllerGuiDescription?, player: PlayerEntity?, title: Text?) :
    CottonInventoryScreen<ChessControllerGuiDescription?>(gui, player, title)

