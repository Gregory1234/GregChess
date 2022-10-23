package gregc.gregchess.bukkit.renderer

import gregc.gregchess.bukkit.config
import gregc.gregchess.bukkit.configName
import gregc.gregchess.bukkit.piece.localName
import gregc.gregchess.bukkit.piece.section
import gregc.gregchess.bukkit.variant.Floor
import gregc.gregchess.bukkitutils.*
import gregc.gregchess.piece.Piece
import gregc.gregchess.piece.PieceType
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.inventory.ItemStack

interface SimpleRendererStyle : RendererStyle {
    fun pieceStructure(piece: Piece): List<Material>
    fun pieceSound(piece: PieceType, sound: String): Sound
    fun floorMaterial(floor: Floor): Material
}

object DefaultSimpleRendererStyle : SimpleRendererStyle {
    override fun pieceStructure(piece: Piece) =
        piece.type.section.getStringList("Structure.${piece.color.configName}").map { m -> Material.valueOf(m) }

    override fun pieceItem(piece: Piece): ItemStack =
        itemStack(Material.valueOf(piece.type.section.getString("Item.${piece.color.configName}")!!)) {
            meta { name = piece.localName }
        }

    override fun pieceSound(piece: PieceType, sound: String): Sound =
        Sound.valueOf(piece.section.getString("Sound.$sound")!!)

    override fun floorMaterial(floor: Floor): Material =
        Material.valueOf(config.getString("Chess.Floor.${floor.name}")!!)
}