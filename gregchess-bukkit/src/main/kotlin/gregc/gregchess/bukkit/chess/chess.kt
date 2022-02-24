package gregc.gregchess.bukkit.chess

import gregc.gregchess.ChessModule
import gregc.gregchess.bukkit.BukkitRegistry
import gregc.gregchess.bukkit.chess.component.*
import gregc.gregchess.bukkit.chess.player.BukkitChessSide
import gregc.gregchess.bukkit.config
import gregc.gregchess.bukkitutils.*
import gregc.gregchess.chess.*
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

val Piece.localChar
    get() = type.localChar
val Piece.localName
    get() = config.getPathString("Chess.Color.${color.configName}.Piece", type.localName)
val Piece.structure
    get() = type.section.getStringList("Structure.${color.configName}").map { m -> Material.valueOf(m) }
val Piece.item: ItemStack
    get() = itemStack(Material.valueOf(type.section.getString("Item.${color.configName}")!!)) {
        meta { name = localName }
    }

val CheckType.localChar
    get() = config.getPathString("Chess.${name.lowercase().replaceFirstChar(Char::uppercase) }").single()

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
        board.lastNormalMove?.origin -> getFloor("LastStart")
        board.lastNormalMove?.display -> getFloor("LastEnd")
        in specialSquares -> getFloor("Other")
        else -> if ((p.file + p.rank) % 2 == 0) getFloor("Dark") else getFloor("Light")
    }
}

val ChessVariant.floorRenderer: ChessFloorRenderer
    get() = BukkitRegistry.FLOOR_RENDERER[this]

val defaultLocalMoveFormatter: MoveFormatter
    get() = MoveFormatter { move ->
        buildString { // TODO: remove repetition
            operator fun Any?.unaryPlus() { if (this != null) append(this) }

            val main = move.pieceTracker.getOriginalOrNull("main")

            if (main?.piece?.type != PieceType.PAWN && move.getTrait<CastlesTrait>() == null) {
                +main?.piece?.localChar?.uppercase()
                +move.getTrait<TargetTrait>()?.uniquenessCoordinate
            }
            if (move.getTrait<CaptureTrait>()?.captureSuccess == true) {
                if (main?.piece?.type == PieceType.PAWN)
                    +(main as? BoardPiece)?.pos?.fileStr
                +config.getPathString("Chess.Capture")
            }
            +move.getTrait<TargetTrait>()?.target
            +move.getTrait<CastlesTrait>()?.side?.castles
            +move.getTrait<PromotionTrait>()?.promotion?.localChar?.uppercase()
            +move.getTrait<CheckTrait>()?.checkType?.localChar
            if (move.getTrait<RequireFlagTrait>()?.flags?.any { ChessFlag.EN_PASSANT in it.value } == true)
                +config.getPathString("Chess.EnPassant")
        }
    }

val ChessVariant.localMoveFormatter: MoveFormatter
    get() = BukkitRegistry.LOCAL_MOVE_FORMATTER[this]

fun BoardPiece.getInfo(game: ChessGame) = textComponent {
    text("Type: $color $type\n")
    text("Position: $pos\n")
    text(if (hasMoved) "Has moved\n" else "Has not moved\n")
    text("Game: ${game.uuid}\n") {
        onClickCopy(game.uuid)
    }
    val moves = getMoves(game.board)
    text("All moves: ${moves.joinToString { game.variant.localMoveFormatter.format(it) }}")
    moves.groupBy { m -> game.variant.getLegality(m, game.board) }.forEach { (l, m) ->
        text("\n${l.prettyName}: ${m.joinToString { game.variant.localMoveFormatter.format(it) }}")
    }
}

fun ChessGame.getInfo() = textComponent {
    text("UUID: $uuid\n") {
        onClickCopy(uuid)
    }
    text("Players: ${sides.toList().joinToString { "${it.name} as ${it.color.configName}" }}\n")
    text("Spectators: ${spectators.spectators.joinToString { it.name }}\n")
    text("Arena: ${arena.name}\n")
    text("Preset: ${gameController.presetName}\n")
    text("Variant: ${variant.key}\n")
    text("Components: ${components.joinToString { it.type.key.toString() }}")
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