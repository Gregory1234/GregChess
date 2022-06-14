package gregc.gregchess.fabric.block

import gregc.gregchess.fabric.client.ChessWorkbenchGuiDescription
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.screen.ScreenHandlerContext
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

@Suppress("OVERRIDE_DEPRECATION")
class ChessWorkbenchBlock(settings: Settings?) : Block(settings) {

    override fun createScreenHandlerFactory(state: BlockState?, world: World?, pos: BlockPos?) =
        SimpleNamedScreenHandlerFactory({ syncId, playerInventory, _ ->
            ChessWorkbenchGuiDescription(syncId, playerInventory, ScreenHandlerContext.create(world, pos))
        }, Text.translatable("gui.gregchess.chess_workbench_menu"))


    override fun onUse(
        state: BlockState, world: World, pos: BlockPos?, player: PlayerEntity, hand: Hand?, hit: BlockHitResult?
    ): ActionResult = if (world.isClient) {
        ActionResult.SUCCESS
    } else {
        player.openHandledScreen(state.createScreenHandlerFactory(world, pos))
        ActionResult.CONSUME
    }


}