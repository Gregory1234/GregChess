package gregc.gregchess.match

import gregc.gregchess.component.Component
import gregc.gregchess.component.ComponentType
import kotlinx.coroutines.CoroutineDispatcher
import java.time.Clock

interface ChessEnvironment {
    val coroutineDispatcher: CoroutineDispatcher
    val clock: Clock
    val requiredComponents: Set<ComponentType<*>> get() = emptySet()
    val impliedComponents: Set<Component> get() = emptySet()
}