package gregc.gregchess.chess.component

import gregc.gregchess.chess.Floor
import gregc.gregchess.chess.Pos

interface Renderer : Component {
    interface Settings : Component.Settings<Renderer>

    fun fillFloor(pos: Pos, floor: Floor)
    fun renderBoardBase()
    fun removeBoard()
}