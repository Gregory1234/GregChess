package gregc.gregchess

import gregc.gregchess.chess.*
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder
import net.fabricmc.fabric.api.item.v1.FabricItemSettings
import net.minecraft.block.AbstractBlock
import net.minecraft.block.Blocks
import net.minecraft.item.BlockItem
import net.minecraft.item.TallBlockItem
import net.minecraft.util.Rarity
import net.minecraft.util.registry.Registry
import java.util.logging.Logger


object GregChess : ModInitializer {
    val PIECES = FabricItemGroupBuilder.build(ident("pieces")) {
        Registry.ITEM[Piece(PieceType.PAWN, Side.WHITE).id].defaultStack
    }

    override fun onInitialize() {
        glog = GregLogger(Logger.getLogger(MOD_NAME))
        PieceType.values().forEach { t ->
            Side.values().forEach { s ->
                if (t == PieceType.PAWN) {
                    val block = Registry.register(Registry.BLOCK, Piece(t, s).id,
                        PawnBlock(AbstractBlock.Settings.copy(Blocks.OAK_PLANKS)))
                    Registry.register(Registry.ITEM, Piece(t, s).id, BlockItem(block, FabricItemSettings().group(PIECES)))
                } else {
                    val block = Registry.register(Registry.BLOCK, Piece(t, s).id,
                        PieceBlock(AbstractBlock.Settings.copy(Blocks.OAK_PLANKS)))
                    Registry.register(Registry.ITEM, Piece(t, s).id, TallBlockItem(block, FabricItemSettings().group(PIECES).rarity(when {
                        t.minor -> Rarity.UNCOMMON
                        t == PieceType.KING -> Rarity.EPIC
                        else -> Rarity.RARE
                    })))
                }
            }
        }
    }
}