package gregc.gregchess.match

import gregc.gregchess.component.Component
import gregc.gregchess.component.ComponentType
import kotlinx.coroutines.CoroutineDispatcher
import java.time.Clock

// TODO: choose a new better name?
interface ChessEnvironment {
    val pgnSite: String // TODO: replace with a method
    val pgnEventName: String
    val pgnRound: Int

    val coroutineDispatcher: CoroutineDispatcher
    val clock: Clock
    val requiredComponents: Set<ComponentType<*>> get() = emptySet()
    val impliedComponents: Set<Component> get() = emptySet()
    fun matchToString(): String
    fun matchCoroutineName(): String
}