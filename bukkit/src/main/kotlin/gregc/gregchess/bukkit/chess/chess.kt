package gregc.gregchess.bukkit.chess

import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.chess.component.arena
import gregc.gregchess.bukkit.chess.component.spectators
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.componentKey
import gregc.gregchess.chess.move.*
import gregc.gregchess.chess.piece.*
import gregc.gregchess.chess.variant.ChessVariant
import gregc.gregchess.registry.module
import gregc.gregchess.registry.name
import gregc.gregchess.snakeToPascal
import org.bukkit.Material
import org.bukkit.Sound

val Color.configName get() = name.snakeToPascal()

fun PieceType.getItem(color: Color) = itemStack(itemMaterial[color]) {
    meta {
        name = config.getPathString("Chess.Color.${color.configName}.Piece", localName)
    }
}

val PieceType.configName get() = name.snakeToPascal()
val PieceType.section get() = module.bukkit.config.getConfigurationSection("Chess.Piece.$configName")!!
val PieceType.localChar get() = section.getString("Char")!!.single()
val PieceType.localName get() = section.getPathString("Name")
fun PieceType.getSound(s: String) = Sound.valueOf(section.getString("Sound.$s")!!)
val PieceType.itemMaterial get() = byColor { Material.valueOf(section.getString("Item.${it.configName}")!!) }
val PieceType.structure
    get() = byColor { section.getStringList("Structure.${it.configName}").map { m -> Material.valueOf(m) } }

val Piece.item get() = type.getItem(color)

fun defaultFormatMoveNameLocal(name: MoveName): String = buildString {
    name.getOrNull(MoveNameTokenType.PIECE_TYPE)?.let { append(it.localChar.uppercase()) }
    name.getOrNull(MoveNameTokenType.UNIQUENESS_COORDINATE)?.let { append(it) }
    name.getOrNull(MoveNameTokenType.CAPTURE)?.let { append(GregChess.plugin.config.getPathString("Chess.Capture")) }
    name.getOrNull(MoveNameTokenType.TARGET)?.let { append(it) }
    name.getOrNull(MoveNameTokenType.PROMOTION)?.let { append(it.localChar.uppercase()) }
    name.getOrNull(MoveNameTokenType.CHECK)?.let { append("+") }
    name.getOrNull(MoveNameTokenType.CHECKMATE)?.let { append("#") }
    name.getOrNull(MoveNameTokenType.EN_PASSANT)?.let { append(" e.p.") }
}

val ChessVariant.localNameFormatter: MoveNameFormatter
    get() = BukkitRegistryTypes.VARIANT_LOCAL_MOVE_NAME_FORMATTER[module, this]

val Floor.material get() = Material.valueOf(config.getString("Chess.Floor.${name.snakeToPascal()}")!!)

fun BoardPiece.getInfo(game: ChessGame) = textComponent {
    text("Type: $color $type\n")
    text("Position: $pos\n")
    text(if (hasMoved) "Has moved\n" else "Has not moved\n")
    text("Game: ${game.uuid}\n") {
        onClickCopy(game.uuid)
    }
    val moves = getLegalMoves(game.board)
    text("All moves: ${moves.joinToString { it.name.format(game.variant.localNameFormatter) }}")
    moves.groupBy { m -> game.variant.getLegality(m, game) }.forEach { (l, m) ->
        text("\n${l.prettyName}: ${m.joinToString { it.name.format(game.variant.localNameFormatter) }}")
    }
}

fun ChessGame.getInfo() = textComponent {
    text("UUID: $uuid\n") {
        onClickCopy(uuid)
    }
    text("Players: ${players.toList().joinToString { "${it.name} as ${it.color.configName}" }}\n")
    text("Spectators: ${spectators.spectators.joinToString { it.name }}\n")
    text("Arena: ${arena.name}\n")
    text("Preset: ${settings.name}\n")
    text("Variant: ${variant.key}\n")
    text("Components: ${components.joinToString { it::class.componentKey.toString() }}")
}

val EndReason<*>.quick
    get() = this in module.bukkit.quickEndReasons

val GameResults.name
    get() = endReason.module.bukkit.config
        .getPathString("Chess.EndReason.${endReason.name.snakeToPascal()}", *args.toTypedArray())

val GameResults.message
    get() = score.let {
        when (it) {
            is GameScore.Draw -> config.getPathString("Message.GameFinished.ItWasADraw", name)
            is GameScore.Victory -> config.getPathString("Message.GameFinished." + it.winner.configName + "Won", name)
        }
    }

val PropertyType.localName get() = module.bukkit.config.getPathString("Scoreboard.${name.snakeToPascal()}")