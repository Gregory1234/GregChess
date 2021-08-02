package gregc.gregchess.fabric

import gregc.gregchess.fabric.chess.ChessControllerBlockScreen
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.screenhandler.v1.ScreenRegistry


object GregChessClient : ClientModInitializer {
    override fun onInitializeClient() {
        ScreenRegistry.register(GregChess.CHESS_CONTROLLER_SCREEN_HANDLER_TYPE) { gui, inventory, title ->
            ChessControllerBlockScreen(gui, inventory.player, title)
        }
    }
}