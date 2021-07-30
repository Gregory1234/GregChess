package gregc.gregchess.bukkit.chess

import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.Registry
import gregc.gregchess.bukkit.chess.component.*
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.variant.*
import gregc.gregchess.snakeToPascal
import org.bukkit.*
import org.bukkit.inventory.ItemStack
import java.time.Duration


object BukkitPieceTypes: Registry<PieceType, Unit>() {
    fun register(id: NamespacedKey, pieceType: PieceType) = register(id, pieceType, Unit)

    init {
        register("king".toKey(), PieceType.KING)
        register("queen".toKey(), PieceType.QUEEN)
        register("rook".toKey(), PieceType.ROOK)
        register("bishop".toKey(), PieceType.BISHOP)
        register("knight".toKey(), PieceType.KNIGHT)
        register("pawn".toKey(), PieceType.PAWN)
    }
}

object BukkitEndReasons: Registry<EndReason<*>, Unit>() {
    fun register(id: NamespacedKey, endReason: EndReason<*>) = register(id, endReason, Unit)

    init {
        register("checkmate".toKey(), EndReason.CHECKMATE)
        register("resignation".toKey(), EndReason.RESIGNATION)
        register("walkover".toKey(), EndReason.WALKOVER)
        register("stalemate".toKey(), EndReason.STALEMATE)
        register("insufficient_material".toKey(), EndReason.INSUFFICIENT_MATERIAL)
        register("fifty_moves".toKey(), EndReason.FIFTY_MOVES)
        register("repetition".toKey(), EndReason.REPETITION)
        register("draw_agreement".toKey(), EndReason.DRAW_AGREEMENT)
        register("timeout".toKey(), EndReason.TIMEOUT)
        register("draw_timeout".toKey(), EndReason.DRAW_TIMEOUT)
        register("pieces_lost".toKey(), EndReason.ALL_PIECES_LOST)
        register("error".toKey(), EndReason.ERROR)

        register("stalemate_victory".toKey(), Antichess.STALEMATE_VICTORY)
        register("atomic".toKey(), AtomicChess.ATOMIC)
        register("king_of_the_hill".toKey(), KingOfTheHill.KING_OF_THE_HILL)
        register("check_limit".toKey(), ThreeChecks.CHECK_LIMIT)

        register("arena_removed".toKey(), ArenaManager.ARENA_REMOVED)
        register("plugin_restart".toKey(), ChessGameManager.PLUGIN_RESTART)
    }
}

private val timeFormat: String get() = config.getString("TimeFormat")!!

private fun Duration.format() = format(timeFormat)!!

@Suppress("UNCHECKED_CAST")
object BukkitPropertyTypes: Registry<PropertyType<*>, (Any?) -> String>() {
    fun <T> register(id: NamespacedKey, type: PropertyType<T>, stringify: (T) -> String = Any?::toString) =
        super.register(id, type, stringify as (Any?) -> String)

    init {
        register("preset".toKey(), ScoreboardManager.PRESET)
        register("player".toKey(), ScoreboardManager.PLAYER)
        register("time_remaining".toKey(), ChessClock.TIME_REMAINING, Duration::format)
        register("time_remaining_simple".toKey(), ChessClock.TIME_REMAINING_SIMPLE, Duration::format)
        register("check_counter".toKey(), ThreeChecks.CHECK_COUNTER)
    }

    fun <T> getStringify(v: PropertyType<T>): (T) -> String = getData(v) as (T) -> String
}

object BukkitChessVariants: Registry<ChessVariant, Unit>() {
    fun register(id: NamespacedKey, variant: ChessVariant) = register(id, variant, Unit)

    init {
        register("normal".toKey(), ChessVariant.Normal)
        register("three_checks".toKey(), ThreeChecks)
        register("king_of_the_hill".toKey(), KingOfTheHill)
        register("atomic".toKey(), AtomicChess)
        register("antichess".toKey(), Antichess)
        register("horde".toKey(), HordeChess)
        register("capture_all".toKey(), CaptureAll)
    }
}

val Side.configName get() = name.snakeToPascal()

fun PieceType.getItem(side: Side, lang: String): ItemStack {
    val item = ItemStack(itemMaterial[side])
    val meta = item.itemMeta!!
    meta.setDisplayName(config.getLocalizedString("Chess.Side.${side.configName}.Piece", localName).get(lang).chatColor())
    item.itemMeta = meta
    return item
}

val PieceType.id get() = BukkitPieceTypes.getId(this)!!
val PieceType.section get() = configOf(id.namespace).getConfigurationSection("Chess.Piece.${id.key.snakeToPascal()}")!!
val PieceType.localName get() = section.getLocalizedString("Name")
fun PieceType.getSound(s: String) = Sound.valueOf(section.getString("Sound.$s")!!)
val PieceType.itemMaterial get() = BySides { Material.valueOf(section.getString("Item.${it.configName}")!!) }
val PieceType.structure get() = BySides { section.getStringList("Structure.${it.configName}").map { m -> Material.valueOf(m) } }

fun Piece.getItem(lang: String) = type.getItem(side, lang)

val Floor.material get() = Material.valueOf(config.getString("Chess.Floor.${name.snakeToPascal()}")!!)

val Piece.id get() = type.id.let { NamespacedKey.fromString(it.namespace + ":" + side.name.lowercase() + "_" + it.key) }

val BoardPiece.id get() = piece.id
fun BoardPiece.getInfo() = buildTextComponent {
    append("Id: $id\n")
    appendCopy("UUID: $uuid\n", uuid)
    append("Position: $pos\n")
    append(if (hasMoved) "Has moved\n" else "Has not moved\n")
    val game = square.game
    appendCopy("Game: ${game.uuid}\n", game.uuid)
    val moves = square.bakedMoves.orEmpty()
    append("All moves: ${moves.joinToString { it.baseName() }}")
    moves.groupBy { m -> game.variant.getLegality(m) }.forEach { (l, m) ->
        append("\n${l.prettyName}: ${m.joinToString { it.baseName() }}")
    }
}

fun ChessGame.getInfo() = buildTextComponent {
    appendCopy("UUID: $uuid\n", uuid)
    append("Players: ${players.toList().joinToString { "${it.name} as ${it.side.configName}" }}\n")
    append("Spectators: ${spectators.spectators.joinToString { it.name }}\n")
    append("Arena: ${arena.name}\n")
    append("Preset: ${settings.name}\n")
    append("Variant: ${variant.id}\n")
    append("Components: ${components.joinToString { it.javaClass.simpleName }}")
}

val EndReason<*>.id
    get() = BukkitEndReasons.getId(this)!!
val GameResults<*>.name
    get() = configOf(endReason.id.namespace).getLocalizedString("Chess.EndReason.${endReason.id.key.snakeToPascal()}", *args.toTypedArray())

val GameResults<*>.message
    get() = score.let {
        when(it) {
            is GameScore.Draw -> config.getLocalizedString("Message.GameFinished.ItWasADraw", name)
            is GameScore.Victory -> config.getLocalizedString("Message.GameFinished." + it.winner.configName + "Won", name)
        }
    }

val PropertyType<*>.id
    get() = BukkitPropertyTypes.getId(this)!!
val PropertyType<*>.localName
    get() = configOf(id.namespace).getLocalizedString("Scoreboard.${id.key.snakeToPascal()}").get(DEFAULT_LANG)
fun <T> PropertyType<T>.stringify(v: T) = BukkitPropertyTypes.getStringify(this)(v)

fun <T> PlayerProperty<T>.asString(s: Side) = type.stringify(this(s))
fun <T> GameProperty<T>.asString() = type.stringify(this())

val ChessVariant.id
    get() = BukkitChessVariants.getId(this)

val ChessGame.renderer get() = requireComponent<BukkitRenderer>()