package gregc.gregchess.player

import gregc.gregchess.Color
import gregc.gregchess.byColor
import gregc.gregchess.component.*
import gregc.gregchess.event.ChessEventRegistry
import gregc.gregchess.match.ChessMatch
import kotlinx.serialization.Serializable

@Serializable
class ChessSideManager(val white: ChessSide, val black: ChessSide) : Component {
    constructor(white: ChessPlayer<*>, black: ChessPlayer<*>) : this(white.createChessSide(Color.WHITE), black.createChessSide(Color.BLACK))

    override val type get() = ComponentType.SIDES


    operator fun get(color: Color): ChessSide = if (color == Color.WHITE) white else black
    fun toByColor() = byColor(white, black)
    fun toList() = listOf(white, black)

    fun getFacade(match: ChessMatch) = match.componentsFacade.makeCachedFacade(::ChessSideManagerFacade, this)

    init {
        require(white.color == Color.WHITE)
        require(black.color == Color.BLACK)
    }

    override fun init(match: ChessMatch, events: ChessEventRegistry) {
        white.init(match, events.subRegistry(Color.WHITE))
        black.init(match, events.subRegistry(Color.BLACK))
    }
}

class ChessSideManagerFacade(match: ChessMatch, component: ChessSideManager) : ComponentFacade<ChessSideManager>(match, component) {
    private val sideFacades = byColor { component.toByColor()[it].createFacade(match) }
    val white get() = sideFacades.white
    val black get() = sideFacades.black
    val current: ChessSideFacade<*> get() = sideFacades[match.currentColor]
    val opponent: ChessSideFacade<*> get() = sideFacades[!match.currentColor]

    operator fun get(color: Color): ChessSideFacade<*> = sideFacades[color]

    fun toByColor() = byColor(white, black)
    fun toList() = listOf(white, black)
}