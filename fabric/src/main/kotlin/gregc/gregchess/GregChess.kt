package gregc.gregchess

import gregc.gregchess.chess.*
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder
import net.fabricmc.fabric.api.item.v1.FabricItemSettings
import net.minecraft.item.Item
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
                Registry.register(Registry.ITEM, Piece(t, s).id, Item(FabricItemSettings().group(PIECES)))
            }
        }
    }
}