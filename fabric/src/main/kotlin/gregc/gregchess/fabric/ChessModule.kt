package gregc.gregchess.fabric

import gregc.gregchess.*
import gregc.gregchess.chess.*
import gregc.gregchess.fabric.chess.*
import net.fabricmc.fabric.api.item.v1.FabricItemSettings
import net.minecraft.block.AbstractBlock
import net.minecraft.block.Blocks
import net.minecraft.item.BlockItem
import net.minecraft.util.Rarity
import net.minecraft.util.registry.Registry


interface FabricChessModule: ChessModule {
    val pieceBlocks: Map<Piece, PieceBlock>
    val pieceItems: Map<Piece, BlockItem>
}

val MainChessModule.fabric
    get() = if (this is FabricChessModule) this
    else extensions.filterIsInstance<FabricChessModule>().first()

object FabricGregChessModule: FabricChessModule, ChessModuleExtension {
    override val base = GregChessModule

    private val pieceBlocks_ = mutableMapOf<Piece, PieceBlock>()
    private val pieceItems_ = mutableMapOf<Piece, BlockItem>()

    private fun PieceType.short() {
        Side.forEach {
            val block = PawnBlock(of(it), AbstractBlock.Settings.copy(Blocks.OAK_PLANKS))
            pieceBlocks_[of(it)] = block
            val item = PawnItem(block, FabricItemSettings().group(GregChess.CHESS_GROUP))
            pieceItems_[of(it)] = item
            Registry.register(Registry.BLOCK, of(it).id, block)
            Registry.register(Registry.ITEM, of(it).id, item)
        }
    }
    private fun PieceType.tall(rarity: Rarity) {
        Side.forEach {
            val block = TallPieceBlock(of(it), AbstractBlock.Settings.copy(Blocks.OAK_PLANKS))
            pieceBlocks_[of(it)] = block
            val item = TallPieceItem(block, FabricItemSettings().group(GregChess.CHESS_GROUP).rarity(rarity))
            pieceItems_[of(it)] = item
            Registry.register(Registry.BLOCK, of(it).id, block)
            Registry.register(Registry.ITEM, of(it).id, item)
        }
    }

    override val pieceBlocks get() = pieceBlocks_.toMap()
    override val pieceItems get() = pieceItems_.toMap()

    private fun registerItems() {
        PieceType.PAWN.short()
        PieceType.KNIGHT.tall(Rarity.UNCOMMON)
        PieceType.BISHOP.tall(Rarity.UNCOMMON)
        PieceType.ROOK.tall(Rarity.RARE)
        PieceType.QUEEN.tall(Rarity.RARE)
        PieceType.KING.tall(Rarity.EPIC)
    }

    override fun load() {
        registerItems()
    }

}