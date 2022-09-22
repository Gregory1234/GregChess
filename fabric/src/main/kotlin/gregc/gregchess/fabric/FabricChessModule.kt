package gregc.gregchess.fabric

import gregc.gregchess.fabric.renderer.simpleFloorRenderer
import gregc.gregchess.registry.ChessModule
import gregc.gregchess.registry.registry.Registry

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