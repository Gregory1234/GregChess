package gregc.gregchess.fabric.chess

import gregc.gregchess.chess.GameSettings
import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.chess.variant.ChessVariant
import gregc.gregchess.fabric.chess.component.PlayerManager
import net.minecraft.util.math.BlockPos
import net.minecraft.world.WorldAccess

fun gameSettings(controller: BlockPos, world: WorldAccess): GameSettings {
    val components = buildList {
        this += Chessboard.Settings[null]
        this += PlayerManager.Settings
    }
    return GameSettings("", false, ChessVariant.Normal, components)
}