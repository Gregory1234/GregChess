package gregc.gregchess.bukkit.chess

import gregc.gregchess.*
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.chess.component.BukkitRenderer
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.spectators
import gregc.gregchess.chess.variant.*
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.inventory.ItemStack


object BukkitPieceTypes: Registry<PieceType>() {
    init {
        register("king".asIdent(), PieceType.KING)
        register("queen".asIdent(), PieceType.QUEEN)
        register("rook".asIdent(), PieceType.ROOK)
        register("bishop".asIdent(), PieceType.BISHOP)
        register("knight".asIdent(), PieceType.KNIGHT)
        register("pawn".asIdent(), PieceType.PAWN)
    }
}

object BukkitEndReasons: Registry<EndReason<*>>() {
    init {
        register("checkmate".asIdent(), EndReason.CHECKMATE)
        register("resignation".asIdent(), EndReason.RESIGNATION)
        register("walkover".asIdent(), EndReason.WALKOVER)
        register("stalemate".asIdent(), EndReason.STALEMATE)
        register("insufficient_material".asIdent(), EndReason.INSUFFICIENT_MATERIAL)
        register("fifty_moves".asIdent(), EndReason.FIFTY_MOVES)
        register("repetition".asIdent(), EndReason.REPETITION)
        register("draw_agreement".asIdent(), EndReason.DRAW_AGREEMENT)
        register("timeout".asIdent(), EndReason.TIMEOUT)
        register("draw_timeout".asIdent(), EndReason.DRAW_TIMEOUT)
        register("pieces_lost".asIdent(), EndReason.ALL_PIECES_LOST)
        register("error".asIdent(), EndReason.ERROR)

        register("stalemate_victory".asIdent(), Antichess.STALEMATE_VICTORY)
        register("atomic".asIdent(), AtomicChess.ATOMIC)
        register("king_of_the_hill".asIdent(), KingOfTheHill.KING_OF_THE_HILL)
        register("check_limit".asIdent(), ThreeChecks.CHECK_LIMIT)

        register("arena_removed".asIdent(), ArenaManager.ARENA_REMOVED)
        register("plugin_restart".asIdent(), ChessGameManager.PLUGIN_RESTART)
    }
}

val Side.standardName get() = name.snakeToPascal()

fun PieceType.getItem(side: Side, lang: String): ItemStack {
    val item = ItemStack(itemMaterial[side])
    val meta = item.itemMeta!!
    meta.setDisplayName(config.getLocalizedString("Chess.Side.${side.standardName}.Piece", localName).get(lang).chatColor())
    item.itemMeta = meta
    return item
}

val PieceType.id get() = BukkitPieceTypes.getId(this)!!
val PieceType.section get() = configOf(id.namespace).getConfigurationSection("Chess.Piece.${id.path.snakeToPascal()}")!!
val PieceType.localName get() = section.getLocalizedString("Name")
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

val EndReason<*>.id
    get() = BukkitEndReasons.getId(this)!!
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