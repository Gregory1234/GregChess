package gregc.gregchess.event

import gregc.gregchess.component.ComponentType

interface ChessEventOwner {
    fun isOf(parent: ChessEventOwner): Boolean = this == parent
}

// TODO: simplify this
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