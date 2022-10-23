package gregc.gregchess.bukkit.renderer

import gregc.gregchess.Pos
import gregc.gregchess.SelfType
import gregc.gregchess.component.Component
import gregc.gregchess.component.ComponentType
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.piece.Piece
import org.bukkit.Location
import org.bukkit.inventory.ItemStack

interface Renderer : Component {
    override val type: ComponentType<out @SelfType Renderer>
    val style: RendererStyle
    val arena: Arena
}

val ChessMatch.renderer get() = environment.impliedComponents.filterIsInstance<Renderer>().first()

interface Arena {
    fun getPos(location: Location): Pos?
}

interface RendererStyle {
    fun pieceItem(piece: Piece): ItemStack
}