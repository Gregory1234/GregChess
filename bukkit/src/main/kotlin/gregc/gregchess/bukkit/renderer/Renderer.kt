package gregc.gregchess.bukkit.renderer

import gregc.gregchess.Pos
import gregc.gregchess.bukkit.match.ComponentAlternative
import gregc.gregchess.match.*
import org.bukkit.Location

interface Renderer : Component {
    override val type: ComponentType<out Renderer>

    fun getPos(location: Location): Pos

    fun validate()
}

val ChessMatch.renderer get() = require(ComponentAlternative.RENDERER)