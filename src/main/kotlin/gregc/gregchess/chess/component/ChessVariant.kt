package gregc.gregchess.chess.component

import gregc.gregchess.glog

abstract class ChessVariant(val name: String) {
    companion object {
        private val normal = Normal()

        private val variants = mutableMapOf<String, ChessVariant>()

        init {
            this += Normal()
        }

        operator fun get(name: String?) = when (name) {
            null -> normal
            else -> variants[name] ?: run {
                glog.warn("Variant $name not found, defaulted to Normal")
                normal
            }
        }

        operator fun plusAssign(variant: ChessVariant) {
            variants[variant.name] = variant
        }
    }

    class Normal : ChessVariant("Normal")

}