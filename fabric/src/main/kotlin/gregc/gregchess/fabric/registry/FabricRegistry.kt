package gregc.gregchess.fabric.registry

import gregc.gregchess.ByColor
import gregc.gregchess.Color
import gregc.gregchess.component.Component
import gregc.gregchess.fabric.FabricChessModule
import gregc.gregchess.fabric.GregChess
import gregc.gregchess.fabric.piece.PieceBlock
import gregc.gregchess.fabric.renderer.ChessFloorRenderer
import gregc.gregchess.piece.*
import gregc.gregchess.registry.*

object FabricRegistry {
    @JvmField
    val PIECE_BLOCK = ConnectedBiRegistry<_, PieceBlock>("piece_block", PieceRegistryView)
    @JvmField
    val FLOOR_RENDERER = ConnectedRegistry<_, ChessFloorRenderer>("floor_renderer", Registry.VARIANT)
    @JvmField
    val IMPLIED_COMPONENTS = PartialConnectedRegistry<_, () -> Component>("implied_components", Registry.COMPONENT_TYPE)
}

operator fun <T> ConnectedBiRegistry<Piece, T>.set(key: PieceType, v: ByColor<T>) {
    Color.forEach {
        set(key.of(it), v[it])
    }
}


fun String.toKey(): RegistryKey<String> {
    val sections = split(":")
    return when (sections.size) {
        1 -> RegistryKey(GregChess, this)
        2 -> RegistryKey(FabricChessModule[sections[0]], sections[1])
        else -> throw IllegalArgumentException("Bad registry key: $this")
    }
}