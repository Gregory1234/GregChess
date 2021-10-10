package gregc.gregchess.bukkit.command

import gregc.gregchess.EnumeratedRegistryView
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.chess.*
import gregc.gregchess.chess.FEN
import gregc.gregchess.chess.Pos
import gregc.gregchess.toKey
import org.bukkit.entity.Player
import java.time.Duration


fun CommandBuilder.subcommand(name: String, builder: CommandBuilder.() -> Unit) {
    literal(name) {
        requirePermission("gregchess.chess.$name")
        builder()
    }
}

fun CommandBuilder.requirePlayer() {
    validate(NOT_PLAYER) { sender is Player }
}

fun CommandBuilder.requireHumanOpponent(): ExecutionContext<Player>.() -> BukkitPlayer {
    validate(OPPONENT_NOT_HUMAN) { (sender as? Player)?.chess?.opponent is BukkitPlayer }
    return { sender.chess!!.opponent as BukkitPlayer }
}

fun CommandBuilder.requireGame(): ExecutionContext<Player>.() -> BukkitPlayer {
    requirePlayer()
    validate(YOU_NOT_IN_GAME) { sender is Player && sender.currentGame != null }
    return { sender.chess!! }
}

fun CommandBuilder.requireNoGame() {
    validate(YOU_IN_GAME) { sender !is Player || sender.currentGame == null }
}

class PosArgument(name: String) : CommandArgumentType<Pos>(name) {
    override fun tryParse(strings: List<String>): Pair<Pos, List<String>>? = try {
        if (strings.isEmpty()) null else Pos.parseFromString(strings.first()) to strings.drop(1)
    } catch (e: IllegalArgumentException) {
        null
    }
}

class RegistryArgument<T>(name: String, val registryView: EnumeratedRegistryView<String, T>) : CommandArgumentType<T>(name) {
    override fun tryParse(strings: List<String>): Pair<T, List<String>>? = try {
        if (strings.isEmpty()) null else registryView[strings.first().toKey()] to strings.drop(1)
    } catch (e: IllegalArgumentException) {
        null
    }

    override fun autocomplete(ctx: ExecutionContext<*>, strings: List<String>): Set<String> =
        registryView.keys.map { it.toString() }.toSet()

}

class FENArgument(name: String) : CommandArgumentType<FEN>(name) {
    override fun tryParse(strings: List<String>): Pair<FEN, List<String>>? = try {
        FEN.parseFromString(strings.joinToString(" ")) to emptyList()
    } catch (e: FEN.FENFormatException) {
        null
    }
}

class DurationArgument(name: String) : CommandArgumentType<Duration>(name, WRONG_DURATION_FORMAT) {
    override fun tryParse(strings: List<String>): Pair<Duration, List<String>>? =
        strings.firstOrNull()?.toDurationOrNull()?.to(strings.drop(1))
}