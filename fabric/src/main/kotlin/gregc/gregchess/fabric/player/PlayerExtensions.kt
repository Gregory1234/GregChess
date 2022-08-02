package gregc.gregchess.fabric.player

import gregc.gregchess.Color
import gregc.gregchess.fabric.results.text
import gregc.gregchess.results.MatchResults
import gregc.gregchess.results.MatchScore
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

fun PlayerEntity.toChessSide(color: Color) = FabricChessSide(gameProfile, color)

fun ServerPlayerEntity.showMatchResults(color: Color, results: MatchResults) {
    val wonlostdraw = when(results.score) {
        MatchScore.Draw -> "draw_by"
        MatchScore.Victory(color) -> "you_won_by"
        else -> "you_lost_by"
    }
    sendMessage(Text.translatable("chess.gregchess.$wonlostdraw", results.text), false)
}
