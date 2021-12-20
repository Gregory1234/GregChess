package gregc.gregchess.bukkit.chess

import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.chess.component.arena
import gregc.gregchess.bukkit.chess.component.spectators
import gregc.gregchess.bukkitutils.*
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.componentKey
import gregc.gregchess.chess.move.MoveNameFormatter
import gregc.gregchess.chess.move.MoveNameTokenType
import gregc.gregchess.chess.piece.*
import gregc.gregchess.chess.variant.ChessVariant
import gregc.gregchess.registry.module
import gregc.gregchess.registry.name
import gregc.gregchess.snakeToPascal
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.inventory.ItemStack

val Color.configName get() = name.snakeToPascal()

val PieceType.configName get() = name.snakeToPascal()
val PieceType.section get() = module.bukkit.config.getConfigurationSection("Chess.Piece.$configName")!!
val PieceType.localChar get() = section.getString("Char")!!.single()
val PieceType.localName get() = section.getPathString("Name")
fun PieceType.getSound(s: String) = Sound.valueOf(section.getString("Sound.$s")!!)

val Piece.structure
    get() = type.section.getStringList("Structure.${color.configName}").map { m -> Material.valueOf(m) }

val Piece.item: ItemStack
    get() = itemStack(Material.valueOf(type.section.getString("Item.${color.configName}")!!)) {
        meta {
            name = config.getPathString("Chess.Color.${color.configName}.Piece", this@item.type.localName)
        }
    }

val defaultLocalMoveNameFormatter: MoveNameFormatter
    get() = MoveNameFormatter { name ->
        buildString {
            name.getOrNull(MoveNameTokenType.PIECE_TYPE)?.let { append(it.localChar.uppercase()) }
            name.getOrNull(MoveNameTokenType.UNIQUENESS_COORDINATE)?.let { append(it) }
            name.getOrNull(MoveNameTokenType.CAPTURE)?.let { append(config.getPathString("Chess.Capture")) }
            name.getOrNull(MoveNameTokenType.TARGET)?.let { append(it) }
            name.getOrNull(MoveNameTokenType.CASTLE)?.let { append(it.castles) }
            name.getOrNull(MoveNameTokenType.PROMOTION)?.let { append(it.localChar.uppercase()) }
            name.getOrNull(MoveNameTokenType.CHECK)?.let { append("+") }
            name.getOrNull(MoveNameTokenType.CHECKMATE)?.let { append("#") }
            name.getOrNull(MoveNameTokenType.EN_PASSANT)?.let { append(" e.p.") }
        }
    }

val ChessVariant.localNameFormatter: MoveNameFormatter
    get() = BukkitRegistry.VARIANT_LOCAL_MOVE_NAME_FORMATTER[module, this]

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