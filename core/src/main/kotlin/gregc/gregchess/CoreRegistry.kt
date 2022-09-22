package gregc.gregchess

import gregc.gregchess.component.ComponentType
import gregc.gregchess.move.trait.MoveTraitType
import gregc.gregchess.piece.PieceType
import gregc.gregchess.piece.PlacedPieceType
import gregc.gregchess.player.ChessSideType
import gregc.gregchess.registry.registry.NameRegistry
import gregc.gregchess.results.EndReason
import gregc.gregchess.stats.ChessStat
import gregc.gregchess.variant.ChessVariant

object CoreRegistry {
    @JvmField
    val PIECE_TYPE = NameRegistry<PieceType>("piece_type")
    @JvmField
    val END_REASON = NameRegistry<EndReason<*>>("end_reason")
    @JvmField
    val VARIANT = NameRegistry<ChessVariant>("variant")
    @JvmField
    val FLAG = NameRegistry<ChessFlag>("flag")
    @JvmField
    val COMPONENT_TYPE = NameRegistry<ComponentType<*>>("component_type")
    @JvmField
    val MOVE_TRAIT_TYPE = NameRegistry<MoveTraitType<*>>("move_trait_type")
    @JvmField
    val SIDE_TYPE = NameRegistry<ChessSideType<*>>("side_type")
    @JvmField
    val PLACED_PIECE_TYPE = NameRegistry<PlacedPieceType<*>>("placed_piece_type")
    @JvmField
    val STAT = NameRegistry<ChessStat<*>>("stat")
}