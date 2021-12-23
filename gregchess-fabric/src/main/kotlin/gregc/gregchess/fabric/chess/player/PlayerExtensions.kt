package gregc.gregchess.fabric.chess.player

import gregc.gregchess.chess.Color
import gregc.gregchess.chess.GameResults
import gregc.gregchess.registry.name
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.LiteralText

val PlayerEntity.gregchess get() = FabricPlayer(uuid, name.asString())

fun ServerPlayerEntity.showGameResults(color: Color, results: GameResults) {
    sendMessage(LiteralText(results.endReason.name), false)
}
