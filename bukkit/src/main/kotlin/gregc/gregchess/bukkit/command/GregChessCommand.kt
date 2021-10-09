package gregc.gregchess.bukkit.command

import gregc.gregchess.EnumeratedRegistryView
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.chess.*
import gregc.gregchess.chess.*
import gregc.gregchess.toKey
import org.bukkit.entity.Player
import java.time.Duration


fun CommandBuilder.subcommand(name: String, builder: CommandBuilder.() -> Unit) {
    literal(name) {
        requirePermission("greg-chess.chess.$name")
        builder()
    }
}

fun CommandBuilder.requirePlayer() {
    partialExecute {
        cPlayer(sender)
    }
    filter {
        if (sender is Player) it else null
    }
}

fun CommandBuilder.requireHumanOpponent(): ExecutionContext<Player>.() -> BukkitPlayer {
    partialExecute<Player> {
        sender.chess!!.opponent.cCast<ChessPlayer, BukkitPlayer>(OPPONENT_NOT_HUMAN)
    }
    filter {
        if ((sender as? Player)?.chess?.opponent is BukkitPlayer) it else null
    }
    return { sender.chess!!.opponent as BukkitPlayer }
}

fun CommandBuilder.requireGame(): ExecutionContext<Player>.() -> BukkitPlayer {
    partialExecute {
        cPlayer(sender)
        sender.currentGame.cNotNull(YOU_NOT_IN_GAME)
    }
    filter {
        if (sender is Player && sender.currentGame != null) it else null
    }
    return { sender.chess!! }
}

fun CommandBuilder.requireNoGame() {
    partialExecute {
        cPlayer(sender)
        cRequire(sender.currentGame == null, YOU_IN_GAME)
    }
    filter {
        if (sender is Player && sender.currentGame == null) it else null
    }
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