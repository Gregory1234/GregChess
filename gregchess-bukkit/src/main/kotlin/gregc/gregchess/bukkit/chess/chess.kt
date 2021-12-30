package gregc.gregchess.bukkit.chess

import gregc.gregchess.ChessModule
import gregc.gregchess.bukkit.BukkitRegistry
import gregc.gregchess.bukkit.chess.component.*
import gregc.gregchess.bukkit.chess.player.BukkitChessSide
import gregc.gregchess.bukkit.config
import gregc.gregchess.bukkitutils.*
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
import org.bukkit.inventory.ItemStack

val ChessModule.plugin get() = get(BukkitRegistry.BUKKIT_PLUGIN).get()
val ChessModule.config get() = plugin.config

val Color.configName get() = name.snakeToPascal()

val PieceType.configName get() = name.snakeToPascal()
val PieceType.section get() = module.config.getConfigurationSection("Chess.Piece.$configName")!!
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

private fun getFloor(name: String): Material = Material.valueOf(config.getString("Chess.Floor.$name")!!)

fun simpleFloorRenderer(specialSquares: Collection<Pos> = emptyList()) = ChessFloorRenderer { p ->
    val heldPiece = (currentSide as? BukkitChessSide)?.held
    fun Move.getFloorMaterial(): Material {
        if (getTrait<CastlesTrait>() != null || getTrait<PromotionTrait>() != null)
            return getFloor("Special")
        getTrait<CaptureTrait>()?.let {
            if (board[it.capture]?.piece != null)
                return getFloor("Capture")
        }
        return getFloor("Move")
    }
    when(p) {
        heldPiece?.pos -> getFloor("Nothing")
        in heldPiece?.getLegalMoves(board).orEmpty().map { it.display } ->
            heldPiece?.getLegalMoves(board).orEmpty().first { it.display == p }.getFloorMaterial()
        board.lastMove?.origin -> getFloor("LastStart")
        board.lastMove?.display -> getFloor("LastEnd")
        in specialSquares -> getFloor("Other")
        else -> if ((p.file + p.rank) % 2 == 0) getFloor("Dark") else getFloor("Light")
    }
}

val ChessVariant.floorRenderer: ChessFloorRenderer
    get() = BukkitRegistry.VARIANT_FLOOR_RENDERER[this]

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
    get() = BukkitRegistry.VARIANT_LOCAL_MOVE_NAME_FORMATTER[this]

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
    text("Players: ${sides.toList().joinToString { "${it.name} as ${it.color.configName}" }}\n")
    text("Spectators: ${spectators.spectators.joinToString { it.name }}\n")
    text("Arena: ${arena.name}\n")
    text("Preset: ${settings.name}\n")
    text("Variant: ${variant.key}\n")
    text("Components: ${components.joinToString { it::class.componentKey.toString() }}")
}

val EndReason<*>.quick
    get() = this in module[BukkitRegistry.QUICK_END_REASONS]

val GameResults.name
    get() = endReason.module.config
        .getPathString("Chess.EndReason.${endReason.name.snakeToPascal()}", *args.toTypedArray())

val GameResults.message
    get() = score.let {
        when (it) {
            is GameScore.Draw -> config.getPathString("Message.GameFinished.ItWasADraw", name)
            is GameScore.Victory -> config.getPathString("Message.GameFinished." + it.winner.configName + "Won", name)
        }
    }

val PropertyType.localName get() = module.config.getPathString("Scoreboard.${name.snakeToPascal()}")