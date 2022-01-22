package gregc.gregchess.bukkit.command

import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.chess.player.*
import gregc.gregchess.bukkitutils.command.*
import gregc.gregchess.chess.FEN
import gregc.gregchess.chess.Pos
import gregc.gregchess.registry.FiniteRegistryView
import org.bukkit.entity.Player

internal fun playerArgument(name: String): PlayerArgument = PlayerArgument(name, PLAYER_NOT_FOUND)

internal fun offlinePlayerArgument(name: String): OfflinePlayerArgument = OfflinePlayerArgument(name, PLAYER_NOT_FOUND)

internal fun CommandBuilder.requirePermission(permission: String) = requirePermission(permission, NO_PERMISSION)

internal fun CommandBuilder.subcommand(name: String, builder: CommandBuilder.() -> Unit) {
    literal(name) {
        requirePermission("gregchess.chess.$name")
        builder()
    }
}

internal fun CommandBuilder.requirePlayer() {
    validate(NOT_PLAYER) { sender is Player }
}

internal fun CommandBuilder.requireHumanOpponent(): ExecutionContext<Player>.() -> BukkitChessSide {
    validate(OPPONENT_NOT_HUMAN) { (sender as? Player)?.chess?.opponent is BukkitChessSide }
    return { sender.chess!!.opponent as BukkitChessSide }
}

internal fun CommandBuilder.requireGame(): ExecutionContext<Player>.() -> BukkitChessSide {
    requirePlayer()
    validate(YOU_NOT_IN_GAME) { sender is Player && (sender as Player).currentGame != null }
    return { sender.chess!! }
}

internal fun CommandBuilder.requireNoGame() {
    validate(YOU_IN_GAME) { sender !is Player || (sender as Player).currentGame == null }
}

internal class PosArgument(name: String) : CommandArgumentType<Pos>(name) {
    override fun tryParse(strings: List<String>): Pair<Pos, List<String>>? = try {
        if (strings.isEmpty()) null else Pos.parseFromString(strings.first()) to strings.drop(1)
    } catch (e: IllegalArgumentException) {
        null
    }
}

internal class RegistryArgument<T>(name: String, val registryView: FiniteRegistryView<String, T>) : CommandArgumentType<T>(name) {
    override fun tryParse(strings: List<String>): Pair<T, List<String>>? = try {
        if (strings.isEmpty()) null else registryView[strings.first().toKey()] to strings.drop(1)
    } catch (e: IllegalArgumentException) {
        null
    }

    override fun autocomplete(ctx: ExecutionContext<*>, strings: List<String>): Set<String> =
        registryView.keys.map { it.toString() }.toSet()

}

internal class FENArgument(name: String) : CommandArgumentType<FEN>(name) {
    override fun tryParse(strings: List<String>): Pair<FEN, List<String>>? = try {
        FEN.parseFromString(strings.joinToString(" ")) to emptyList()
    } catch (e: FEN.FENFormatException) {
        null
    }
}

internal fun durationArgument(name: String) = DurationArgument(name, WRONG_DURATION_FORMAT)