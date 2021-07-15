package gregc.gregchess.chess

import gregc.gregchess.GregChess
import io.github.cottonmc.cotton.gui.SyncedGuiDescription
import io.github.cottonmc.cotton.gui.client.CottonInventoryScreen
import io.github.cottonmc.cotton.gui.widget.WGridPanel
import io.github.cottonmc.cotton.gui.widget.data.Insets
import net.minecraft.block.*
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.screen.*
import net.minecraft.text.Text
import net.minecraft.text.TranslatableText
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World


class ChessControllerBlockEntity(pos: BlockPos?, state: BlockState?) :
    BlockEntity(GregChess.CHESS_CONTROLLER_ENTITY_TYPE, pos, state), NamedScreenHandlerFactory {
    override fun getDisplayName(): Text = TranslatableText(cachedState.block.translationKey)

    override fun createMenu(syncId: Int, inventory: PlayerInventory?, player: PlayerEntity?): ScreenHandler =
        ChessControllerGuiDescription(syncId, inventory, ScreenHandlerContext.create(world, pos))

}

class ChessControllerGuiDescription(syncId: Int, playerInventory: PlayerInventory?, context: ScreenHandlerContext?) :
    SyncedGuiDescription(
        GregChess.CHESS_CONTROLLER_SCREEN_HANDLER_TYPE,
        syncId,
        playerInventory,
        null,
        getBlockPropertyDelegate(context)
    ) {

    init {
        val root = WGridPanel()
        setRootPanel(root)
        root.setSize(300, 200)
        root.insets = Insets.ROOT_PANEL
    }

}

class ChessControllerBlockScreen(gui: ChessControllerGuiDescription?, player: PlayerEntity?, title: Text?) :
    CottonInventoryScreen<ChessControllerGuiDescription?>(gui, player, title)


class ChessControllerBlock(settings: Settings?) : BlockWithEntity(settings) {
    override fun createBlockEntity(pos: BlockPos?, state: BlockState?): BlockEntity =
        ChessControllerBlockEntity(pos, state)

    override fun getRenderType(state: BlockState?): BlockRenderType = BlockRenderType.MODEL

    override fun onUse(state: BlockState, world: World?, pos: BlockPos?, player: PlayerEntity, hand: Hand?, hit: BlockHitResult?): ActionResult {
        player.openHandledScreen(state.createScreenHandlerFactory(world, pos))
        return ActionResult.SUCCESS
    }
}