package gregc.gregchess.bukkit.command

import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.chess.player.*
import gregc.gregchess.bukkitutils.command.*
import gregc.gregchess.chess.FEN
import gregc.gregchess.chess.Pos
import gregc.gregchess.registry.FiniteRegistryView
import org.bukkit.entity.Player

internal fun playerArgument(name: String) = playerArgument(name, PLAYER_NOT_FOUND)

internal fun offlinePlayerArgument(name: String) = offlinePlayerArgument(name, PLAYER_NOT_FOUND)

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

internal fun posArgument(name: String) = SimpleArgument(name) { try { Pos.parseFromString(it) } catch (e : IllegalArgumentException) { null } }

internal fun <T : Any> registryArgument(name: String, registryView: FiniteRegistryView<String, T>, condition: ((T) -> Boolean)? = null) =
    SimpleArgument(
        name,
        defaults = { registryView.keys.filter { condition?.invoke(registryView[it]) ?: true }.map { it.toString() }.toSet() }
    ) {
        try {
            registryView[it.toKey()].also { v -> require(condition?.invoke(v) ?: true) }
        } catch (e: IllegalArgumentException) {
            null
        }
    }

internal class FENArgument(name: String) : CommandArgumentType<FEN>(name) {
    override fun tryParse(args: StringArguments): FEN? = try {
        FEN.parseFromString(args.nextMany().joinToString(" "))
    } catch (e: FEN.FENFormatException) {
        null
    }
}

internal fun durationArgument(name: String) = durationArgument(name, WRONG_DURATION_FORMAT)