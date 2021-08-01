package gregc.gregchess.bukkit.chess

import gregc.gregchess.GregChessModule
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.chess.component.BukkitRenderer
import gregc.gregchess.bukkit.chess.component.spectators
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.*
import gregc.gregchess.snakeToPascal
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.inventory.ItemStack

val Side.configName get() = name.snakeToPascal()

fun PieceType.getItem(side: Side): ItemStack {
    val item = ItemStack(itemMaterial[side])
    val meta = item.itemMeta!!
    meta.setDisplayName(config.getString("Chess.Side.${side.configName}.Piece")!!.format(localName).chatColor())
    item.itemMeta = meta
    return item
}

val PieceType.module get() = GregChessModule.pieceTypeModule(this).bukkit
val PieceType.configName get() = name.snakeToPascal()
val PieceType.section get() = module.config.getConfigurationSection("Chess.Piece.$configName")!!
val PieceType.localChar get() = section.getString("Char")!!.single()
val PieceType.localName get() = section.getString("Name")!!
fun PieceType.getSound(s: String) = Sound.valueOf(section.getString("Sound.$s")!!)
val PieceType.itemMaterial get() = BySides { Material.valueOf(section.getString("Item.${it.configName}")!!) }
val PieceType.structure get() = BySides { section.getStringList("Structure.${it.configName}").map { m -> Material.valueOf(m) } }

val Piece.item get() = type.getItem(side)

val MoveName.localName get() = joinToString("") { GregChessModule.modules.firstNotNullOfOrNull { m -> m.bukkit.moveNameTokenToString(it.type, it.value) } ?: it.pgn }

val Floor.material get() = Material.valueOf(config.getString("Chess.Floor.${name.snakeToPascal()}")!!)

fun BoardPiece.getInfo() = buildTextComponent {
    append("Type: ${side.name} ${type.name}\n")
    appendCopy("UUID: $uuid\n", uuid)
    append("Position: $pos\n")
    append(if (hasMoved) "Has moved\n" else "Has not moved\n")
    val game = square.game
    appendCopy("Game: ${game.uuid}\n", game.uuid)
    val moves = square.bakedMoves.orEmpty()
    append("All moves: ${moves.joinToString { it.baseName().localName }}")
    moves.groupBy { m -> game.variant.getLegality(m) }.forEach { (l, m) ->
        append("\n${l.prettyName}: ${m.joinToString { it.baseName().localName }}")
    }
}

fun ChessGame.getInfo() = buildTextComponent {
    appendCopy("UUID: $uuid\n", uuid)
    append("Players: ${players.toList().joinToString { "${it.name} as ${it.side.configName}" }}\n")
    append("Spectators: ${spectators.spectators.joinToString { it.name }}\n")
    append("Arena: ${arena.name}\n")
    append("Preset: ${settings.name}\n")
    append("Variant: ${variant.name}\n")
    append("Components: ${components.joinToString { it.javaClass.simpleName }}")
}

val EndReason<*>.module get() = GregChessModule.endReasonModule(this).bukkit
val GameResults<*>.name
    get() = endReason.module.config.getString("Chess.EndReason.${endReason.name.snakeToPascal()}")!!.format(*args.toTypedArray())

val GameResults<*>.message
    get() = score.let {
        when(it) {
            is GameScore.Draw -> config.getString("Message.GameFinished.ItWasADraw")!!
            is GameScore.Victory -> config.getString("Message.GameFinished." + it.winner.configName + "Won")!!
        }.format(name)
    }

val PropertyType<*>.module get() = GregChessModule.propertyTypeModule(this).bukkit
val PropertyType<*>.localName get() = module.config.getString("Scoreboard.${name.snakeToPascal()}")!!
fun <T> PropertyType<T>.stringify(v: T) = module.stringify(this, v)

fun <T> PlayerProperty<T>.asString(s: Side) = type.stringify(this(s))
fun <T> GameProperty<T>.asString() = type.stringify(this())

val ChessGame.renderer get() = requireComponent<BukkitRenderer>()