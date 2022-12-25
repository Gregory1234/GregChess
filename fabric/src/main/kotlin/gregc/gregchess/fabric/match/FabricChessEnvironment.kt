package gregc.gregchess.fabric.match

import gregc.gregchess.component.Component
import gregc.gregchess.component.ComponentType
import gregc.gregchess.fabric.FabricRegistry
import gregc.gregchess.fabric.component.FabricComponentType
import gregc.gregchess.fabricutils.coroutines.FabricDispatcher
import gregc.gregchess.match.ChessEnvironment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.time.Clock

@Serializable
object FabricChessEnvironment : ChessEnvironment {
    override val coroutineDispatcher: CoroutineDispatcher get() = FabricDispatcher()
    override val clock: Clock get() = Clock.systemDefaultZone()
    @Transient
    override val impliedComponents: Set<Component> = FabricRegistry.IMPLIED_COMPONENTS.values.map { it() }.toSet()
    override val requiredComponents: Set<ComponentType<*>> get() = setOf(FabricComponentType.RENDERER)
}