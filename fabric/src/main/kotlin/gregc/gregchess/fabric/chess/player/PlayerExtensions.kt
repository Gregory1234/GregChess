package gregc.gregchess.fabric.chess.player

import gregc.gregchess.Color
import gregc.gregchess.fabric.chess.text
import gregc.gregchess.results.GameResults
import gregc.gregchess.results.GameScore
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.TranslatableText

val PlayerEntity.gregchess get() = FabricPlayerType.FABRIC.of(gameProfile)

fun ServerPlayerEntity.showGameResults(color: Color, results: GameResults) {
    val wonlostdraw = when(results.score) {
        GameScore.Draw -> "draw_by"
        GameScore.Victory(color) -> "you_won_by"
        else -> "you_lost_by"
    }
    sendMessage(TranslatableText("chess.gregchess.$wonlostdraw", results.text), false)
}
