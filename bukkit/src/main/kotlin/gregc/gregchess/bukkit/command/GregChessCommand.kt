package gregc.gregchess.bukkit.command

import gregc.gregchess.Pos
import gregc.gregchess.board.FEN
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.player.*
import gregc.gregchess.bukkit.registry.toKey
import gregc.gregchess.bukkitutils.command.*
import gregc.gregchess.registry.FiniteRegistryView
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

internal fun playerArgument(name: String) = playerArgument(name, PLAYER_NOT_FOUND)

internal fun offlinePlayerArgument(name: String) = offlinePlayerArgument(name, PLAYER_NOT_FOUND)

internal fun CommandBuilder<*>.requirePermission(permission: String) = requirePermission(permission, NO_PERMISSION)

internal fun <S : CommandSender> CommandBuilder<S>.subcommand(name: String, builder: CommandBuilder<S>.() -> Unit) {
    literal(name) {
        requirePermission("gregchess.chess.$name")
        builder()
    }
}

internal fun CommandBuilder<CommandSender>.playerSubcommand(name: String, builder: CommandBuilder<Player>.() -> Unit) {
    literal(Player::class, name) {
        requirePermission("gregchess.chess.$name")
        builder()
    }
}

internal fun CommandBuilder<Player>.requireHumanOpponent(): ExecutionContext<Player>.() -> BukkitChessSide {
    validate(OPPONENT_NOT_HUMAN) { sender.currentChessSide?.opponent is BukkitChessSide }
    return { sender.currentChessSide!!.opponent as BukkitChessSide }
}

internal fun CommandBuilder<Player>.requireGame(): ExecutionContext<Player>.() -> BukkitChessSide {
    validate(YOU_NOT_IN_GAME) { sender.currentChessGame != null }
    return { sender.currentChessSide!! }
}

internal fun CommandBuilder<*>.requireNoGame() {
    validate(YOU_IN_GAME) { sender !is Player || (sender as Player).currentChessGame == null }
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