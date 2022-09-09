package gregc.gregchess.event

import gregc.gregchess.Color
import gregc.gregchess.component.ComponentType
import gregc.gregchess.player.ChessSideType

interface ChessEventOwner {
    fun isOf(parent: ChessEventOwner): Boolean = this == parent
}

class ChessEventSubOwner(val owner: ChessEventOwner, val subOwner: Any) : ChessEventOwner {
    override fun toString(): String = "$owner -> $subOwner"
    override fun equals(other: Any?): Boolean = other is ChessEventSubOwner && other.owner == owner && other.subOwner == subOwner
    override fun hashCode(): Int {
        var result = owner.hashCode()
        result = 31 * result + subOwner.hashCode()
        return result
    }
    override fun isOf(parent: ChessEventOwner): Boolean = super.isOf(parent) || owner.isOf(parent)
}
class ChessEventComponentOwner(val type: ComponentType<*>) : ChessEventOwner {
    override fun toString(): String = type.toString()
    override fun equals(other: Any?): Boolean = other is ChessEventComponentOwner && other.type == type
    override fun hashCode(): Int = type.hashCode()
}
class ChessEventSideOwner(val type: ChessSideType<*>, val color: Color) : ChessEventOwner {
    override fun toString(): String = "$type ($color)"
    override fun equals(other: Any?): Boolean = other is ChessEventSideOwner && other.type == type && other.color == color
    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + color.hashCode()
        return result
    }
}