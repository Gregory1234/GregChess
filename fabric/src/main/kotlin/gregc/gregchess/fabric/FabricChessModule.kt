package gregc.gregchess.fabric

import gregc.gregchess.*
import gregc.gregchess.fabric.piece.PieceBlock
import gregc.gregchess.fabric.renderer.ChessFloorRenderer
import gregc.gregchess.fabric.renderer.simpleFloorRenderer
import gregc.gregchess.piece.*
import gregc.gregchess.registry.*
import gregc.gregchess.variant.ChessVariant

object FabricRegistry {
    @JvmField
    val PIECE_BLOCK = ConnectedBiRegistry<Piece, PieceBlock>("piece_block", PieceRegistryView)
    @JvmField
    val FLOOR_RENDERER = ConnectedRegistry<ChessVariant, ChessFloorRenderer>("floor_renderer", Registry.VARIANT)
}

operator fun <T> ConnectedBiRegistry<Piece, T>.set(key: PieceType, v: ByColor<T>) {
    Color.forEach {
        set(key.of(it), v[it])
    }
}

abstract class FabricChessModule(name: String, namespace: String) : ChessModule(name, namespace) {
    companion object {
        internal val modules = mutableSetOf<ChessModule>()
        operator fun get(namespace: String) = modules.first { it.namespace == namespace }
    }

    final override fun postLoad() {
        FabricRegistry.FLOOR_RENDERER[this].completeWith { simpleFloorRenderer() }
    }

    final override fun finish() {
        modules += this
    }

    final override fun validate() {
        Registry.REGISTRIES.forEach { it[this].validate() }
    }
}

fun interface ChessInitializer {
    fun onInitializeChess()
}