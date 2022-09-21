package gregc.gregchess.fabric.client

import gregc.gregchess.fabric.GregChessMod
import net.fabricmc.api.ClientModInitializer
import net.minecraft.client.gui.screen.ingame.HandledScreens

// TODO: add @Environment(EnvType.CLIENT) where possible

object GregChessModClient : ClientModInitializer {
    override fun onInitializeClient() {
        HandledScreens.register(GregChessMod.CHESS_CONTROLLER_SCREEN_HANDLER_TYPE) { gui, inventory, title ->
            ChessControllerScreen(gui, inventory.player, title)
        }
        HandledScreens.register(GregChessMod.PROMOTION_MENU_HANDLER_TYPE) { gui, inventory, title ->
            PromotionMenu(gui, inventory.player, title)
        }
        HandledScreens.register(GregChessMod.CHESS_WORKBENCH_SCREEN_HANDLER_TYPE) { gui, inventory, title ->
            ChessWorkbenchScreen(gui, inventory.player, title)
        }
    }
}