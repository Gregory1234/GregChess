package gregc.gregchess.bukkit.renderer

import gregc.gregchess.bukkit.NO_ARENAS
import gregc.gregchess.bukkit.component.BukkitComponentType
import gregc.gregchess.bukkitutils.CommandException
import gregc.gregchess.component.Component
import gregc.gregchess.event.ChessBaseEvent
import gregc.gregchess.event.EventListenerRegistry
import gregc.gregchess.match.ChessMatch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
class SimpleRenderer : Component {
    override val type get() = BukkitComponentType.SIMPLE_RENDERER

    @Transient
    lateinit var arena: SimpleArena
        private set

    override fun init(match: ChessMatch, events: EventListenerRegistry) {
        arena = SimpleArenaManager.reserveArenaOrNull(match) ?: throw CommandException(NO_ARENAS)
        events.register<ChessBaseEvent> {
            if (it == ChessBaseEvent.CLEAR || it == ChessBaseEvent.PANIC) arena.currentMatch = null
        }

    }

}