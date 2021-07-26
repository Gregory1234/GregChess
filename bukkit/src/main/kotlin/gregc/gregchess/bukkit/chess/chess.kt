package gregc.gregchess.bukkit.chess

import gregc.gregchess.*
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.chess.component.BukkitRenderer
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.spectators
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.inventory.ItemStack


val Side.standardName get() = name.snakeToPascal()

data class IdentifierAlreadyUsedException(val id: Identifier, val original: Any?, val duplicate: Any?) :
    Exception("$id - original: $original, duplicate: $duplicate")

data class AlreadyRegisteredException(val o: Any, val original: Identifier, val duplicate: Identifier) :
    Exception("$o - original: $original, duplicate: $duplicate")

object BukkitPieceTypes {
    private val pieceTypes = mutableMapOf<Identifier, PieceType>()


    fun register(id: Identifier, pieceType: PieceType) {
        if (id in pieceTypes)
            throw IdentifierAlreadyUsedException(id, pieceTypes[id], pieceType)
        if (pieceTypes.containsValue(pieceType))
            throw AlreadyRegisteredException(pieceType, getId(pieceType)!!, id)
        pieceTypes[id] = pieceType
    }

    init {
        register("king".asIdent(), PieceType.KING)
        register("queen".asIdent(), PieceType.QUEEN)
        register("rook".asIdent(), PieceType.ROOK)
        register("bishop".asIdent(), PieceType.BISHOP)
        register("knight".asIdent(), PieceType.KNIGHT)
        register("pawn".asIdent(), PieceType.PAWN)
    }

    fun getId(pieceType: PieceType) = pieceTypes.filterValues { it == pieceType }.keys.firstOrNull()
    operator fun get(id: Identifier) = pieceTypes[id]

    val values get() = pieceTypes.values
    val ids get() = pieceTypes.keys

}

fun PieceType.getItem(side: Side, lang: String): ItemStack {
    val item = ItemStack(itemMaterial[side])
    val meta = item.itemMeta!!
    meta.setDisplayName(config.getLocalizedString("Chess.Side.${side.standardName}.Piece", name).get(lang).chatColor())
    item.itemMeta = meta
    return item
}

val PieceType.id get() = BukkitPieceTypes.getId(this)!!
val PieceType.section get() = configOf(id.namespace).getConfigurationSection("Chess.Piece.${id.path.snakeToPascal()}")!!
val PieceType.name get() = section.getLocalizedString("Name")
fun PieceType.getSound(s: String) = Sound.valueOf(section.getString("Sound.$s")!!)
val PieceType.itemMaterial get() = BySides { Material.valueOf(section.getString("Item.${it.standardName}")!!) }
val PieceType.structure get() = BySides { section.getStringList("Structure.${it.standardName}").map { m -> Material.valueOf(m) } }

fun Piece.getItem(lang: String) = type.getItem(side, lang)

val Floor.material get() = Material.valueOf(config.getString("Chess.Floor.${standardName}")!!)

val Piece.id get() = type.id.let { Identifier(it.namespace, side.name.lowercase() + "_" + it.path) }

val BoardPiece.id get() = piece.id
fun BoardPiece.getInfo() = buildTextComponent {
    append("Id: $id\n")
    appendCopy("UUID: $uuid\n", uuid)
    append("Position: $pos\n")
    append(if (hasMoved) "Has moved\n" else "Has not moved\n")
    val game = square.game
    appendCopy("Game: ${game.uuid}\n", game.uuid)
    val moves = square.bakedMoves.orEmpty()
    append("All moves: ${moves.joinToString { it.baseStandardName() }}")
    moves.groupBy { m -> game.variant.getLegality(m) }.forEach { (l, m) ->
        append("\n${l.prettyName}: ${m.joinToString { it.baseStandardName() }}")
    }
}

fun ChessGame.getInfo() = buildTextComponent {
    appendCopy("UUID: $uuid\n", uuid)
    append("Players: ${players.toList().joinToString { "${it.name} as ${it.side.standardName}" }}\n")
    append("Spectators: ${spectators.spectators.joinToString { it.name }}\n")
    append("Arena: ${arena.name}\n")
    append("Preset: ${settings.name}\n")
    append("Variant: ${variant.id}\n")
    append("Components: ${components.joinToString { it.javaClass.simpleName }}")
}

val GameResults<*>.name
    get() = configOf(endReason.id.namespace).getLocalizedString("Chess.EndReason.${endReason.id.path.snakeToPascal()}", *args.toTypedArray())

val GameResults<*>.message
    get() = score.let {
        when(it) {
            is GameScore.Draw -> config.getLocalizedString("Message.GameFinished.ItWasADraw", name)
            is GameScore.Victory -> config.getLocalizedString("Message.GameFinished." + it.winner.standardName + "Won", name)
        }
    }

val ChessGame.renderer get() = requireComponent<BukkitRenderer>()