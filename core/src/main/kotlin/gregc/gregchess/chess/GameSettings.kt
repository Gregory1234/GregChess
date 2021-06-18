package gregc.gregchess.chess

import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.variant.ChessVariant

data class GameSettings(
    val name: String,
    val simpleCastling: Boolean,
    val variant: ChessVariant,
    val components: Collection<Component.Settings<*>>
) {
    inline fun <reified T : Component.Settings<*>> getComponent(): T? = components.filterIsInstance<T>().firstOrNull()
}