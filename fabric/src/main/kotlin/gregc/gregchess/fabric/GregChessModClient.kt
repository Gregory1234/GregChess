package gregc.gregchess.fabric

import gregc.gregchess.fabric.chess.ChessControllerBlockScreen
import gregc.gregchess.fabric.chess.PromotionMenu
import net.fabricmc.api.ClientModInitializer
import net.minecraft.client.gui.screen.ingame.HandledScreens


object GregChessModClient : ClientModInitializer {
    override fun onInitializeClient() {
        HandledScreens.register(GregChessMod.CHESS_CONTROLLER_SCREEN_HANDLER_TYPE) { gui, inventory, title ->
            ChessControllerBlockScreen(gui, inventory.player, title)
        }
        HandledScreens.register(GregChessMod.PROMOTION_MENU_HANDLER_TYPE) { gui, inventory, title ->
            PromotionMenu(gui, inventory.player, title)
        }
    }
}