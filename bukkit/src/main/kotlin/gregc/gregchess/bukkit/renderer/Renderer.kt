package gregc.gregchess.bukkit.renderer

import gregc.gregchess.Pos
import gregc.gregchess.bukkit.component.ComponentAlternative
import gregc.gregchess.component.Component
import gregc.gregchess.component.ComponentType
import gregc.gregchess.match.ChessMatch
import org.bukkit.Location

interface Renderer : Component {
    override val type: ComponentType<out Renderer>

    fun getPos(location: Location): Pos

    fun validate()
}

val ChessMatch.renderer get() = components.require(ComponentAlternative.RENDERER)