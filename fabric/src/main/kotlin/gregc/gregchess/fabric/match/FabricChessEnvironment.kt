package gregc.gregchess.fabric.match

import gregc.gregchess.component.Component
import gregc.gregchess.component.ComponentIdentifier
import gregc.gregchess.fabric.FabricRegistry
import gregc.gregchess.fabric.component.FabricComponentType
import gregc.gregchess.fabric.coroutines.FabricDispatcher
import gregc.gregchess.match.ChessEnvironment
import gregc.gregchess.match.ChessMatch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.*
import java.time.Clock
import java.util.*

@Serializable
class FabricChessEnvironment(@Contextual val uuid: UUID = UUID.randomUUID()) : ChessEnvironment {
    override val pgnSite: String get() = "GregChess Fabric mod"
    override val pgnEventName: String get() = "Casual game"
    override val pgnRound: Int get() = 1
    override val coroutineDispatcher: CoroutineDispatcher get() = FabricDispatcher()
    override val clock: Clock get() = Clock.systemDefaultZone()
    override fun matchCoroutineName(): String = uuid.toString()
    override fun matchToString(): String = "uuid=$uuid"
    @Transient
    override val impliedComponents: Set<Component> = FabricRegistry.IMPLIED_COMPONENTS.values.map { it() }.toSet()
    override val requiredComponents: Set<ComponentIdentifier<*>> get() = setOf(FabricComponentType.RENDERER)
}

val ChessMatch.fabricEnvironment get() = environment as FabricChessEnvironment
val ChessMatch.uuid get() = fabricEnvironment.uuid