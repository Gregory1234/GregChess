package gregc.gregchess.fabric.chess

import gregc.gregchess.chess.*
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.LiteralText

fun ServerPlayerEntity.showGameResults(side: Side, results: GameResults<*>) {
    sendMessage(LiteralText(results.endReason.name), false)
}

class FabricPlayer(val player: ServerPlayerEntity, side: Side, game: ChessGame) :
    ChessPlayer(name = player.entityName, side, game) {
    override fun init() {
        player.sendMessage(LiteralText(side.name),false)
    }
}

fun ChessGame.AddPlayersScope.fabric(player: ServerPlayerEntity, side: Side) {
    addPlayer(FabricPlayer(player, side, game))
}

inline fun BySides<ChessPlayer>.forEachReal(block: (ServerPlayerEntity) -> Unit) {
    toList().filterIsInstance<FabricPlayer>().map { it.player }.distinctBy { it.uuid }.forEach(block)
}

inline fun BySides<ChessPlayer>.forEachUnique(block: (FabricPlayer) -> Unit) {
    val players = toList().filterIsInstance<FabricPlayer>()
    if (players.size == 2 && players.all {it.player == players[0].player})
        players.filter { it.hasTurn }.forEach(block)
    else
        players.forEach(block)
}