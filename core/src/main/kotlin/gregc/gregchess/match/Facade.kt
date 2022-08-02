package gregc.gregchess.match

interface AnyFacade : ChessEventCaller {
    val match: ChessMatch
    override fun callEvent(event: ChessEvent) = match.callEvent(event)
}

abstract class ComponentFacade<T : Component>(final override val match: ChessMatch, val component: T) : AnyFacade {
    @Suppress("UNCHECKED_CAST")
    val type: ComponentType<T> get() = component.type as ComponentType<T>

    final override fun callEvent(event: ChessEvent) = super.callEvent(event)
}