package gregc.gregchess.chess.component

import gregc.gregchess.chess.ChessGame
import kotlin.reflect.KClass

interface ComponentData<out T : Component> {
    fun getComponent(game: ChessGame): T
}
abstract class Component(protected val game: ChessGame) {
    abstract val data: ComponentData<*>
}

class ComponentNotFoundException(cl: KClass<out Component>) : Exception(cl.toString())
class ComponentDataNotFoundException(cl: KClass<out ComponentData<*>>) : Exception(cl.toString())