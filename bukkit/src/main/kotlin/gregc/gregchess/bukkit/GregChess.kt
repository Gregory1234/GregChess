package gregc.gregchess.bukkit

import gregc.gregchess.GregChessCore
import gregc.gregchess.board.Chessboard
import gregc.gregchess.board.FEN
import gregc.gregchess.bukkit.game.*
import gregc.gregchess.bukkit.player.BukkitPlayerType
import gregc.gregchess.bukkit.registry.BukkitRegistry
import gregc.gregchess.bukkit.renderer.*
import gregc.gregchess.bukkit.stats.YamlChessStats
import gregc.gregchess.bukkitutils.toDuration
import gregc.gregchess.clock.ChessClock
import gregc.gregchess.clock.TimeControl
import gregc.gregchess.game.ComponentType
import gregc.gregchess.variant.KingOfTheHill
import gregc.gregchess.variant.ThreeChecks
import kotlin.time.Duration

internal object GregChess : BukkitChessModule(GregChessPlugin.plugin) {

    private val clockSettings: Map<String, ChessClock>
        get() = config.getConfigurationSection("Settings.Clock")?.getKeys(false).orEmpty().associateWith {
            val section = config.getConfigurationSection("Settings.Clock.$it")!!
            val t = TimeControl.Type.valueOf(section.getString("Type", TimeControl.Type.INCREMENT.toString())!!)
            val initial = section.getString("Initial")!!.toDuration()
            val increment = if (t.usesIncrement) section.getString("Increment")!!.toDuration() else Duration.ZERO
            ChessClock(TimeControl(t, initial, increment))
        }

    private fun registerSettings() {
        BukkitRegistry.SETTINGS_PARSER[ComponentType.CHESSBOARD] = {
            val simpleCastling = section.getBoolean("SimpleCastling", false)
            when(val name = section.getString("Board")) {
                null -> Chessboard(variant, simpleCastling = simpleCastling)
                "normal" -> Chessboard(variant, simpleCastling = simpleCastling)
                "chess960" -> Chessboard(variant, chess960 = true, simpleCastling = simpleCastling)
                else -> if (name.startsWith("fen ")) try {
                    Chessboard(variant, FEN.parseFromString(name.drop(4)), simpleCastling = simpleCastling)
                } catch (e: FEN.FENFormatException) {
                    logger.warn("Chessboard configuration ${e.fen} is in a wrong format, defaulted to normal: ${e.cause?.message}!")
                    Chessboard(variant, simpleCastling = simpleCastling)
                } else {
                    logger.warn("Invalid chessboard configuration $name, defaulted to normal!")
                    Chessboard(variant, simpleCastling = simpleCastling)
                }
            }
        }
        BukkitRegistry.SETTINGS_PARSER[ComponentType.CLOCK] =  {
            SettingsManager.chooseOrParse(clockSettings, section.getString("Clock")) {
                TimeControl.parseOrNull(it)?.let { t -> ChessClock(t) } ?: run {
                    logger.warn("Bad time control \"$it\", defaulted to none")
                    null
                }
            }
        }
        BukkitRegistry.SETTINGS_PARSER[BukkitComponentType.RENDERER] = { BukkitRenderer() }
        BukkitRegistry.SETTINGS_PARSER[BukkitComponentType.GAME_CONTROLLER] = { GameController(presetName) }
        BukkitRegistry.SETTINGS_PARSER[ThreeChecks.CHECK_COUNTER] = { ThreeChecks.CheckCounter(section.getInt("CheckLimit", 3)) }
    }

    private fun hookComponents() {
        for (c in listOf(ComponentType.CHESSBOARD, ComponentType.CLOCK, BukkitComponentType.GAME_CONTROLLER,
            BukkitComponentType.SPECTATOR_MANAGER, BukkitComponentType.SCOREBOARD_MANAGER, BukkitComponentType.RENDERER,
            BukkitComponentType.EVENT_RELAY, BukkitComponentType.ADAPTER)) {
            BukkitRegistry.HOOKED_COMPONENTS += c
        }
    }

    override fun load() {
        GregChessCore.registerAll(this)
        BukkitGregChessCore.autoRegister(this).apply {
            registerAll<Arena>()
            registerAll<ChessGameManager>()
            registerAll<BukkitComponentType>()
            registerAll<BukkitPlayerType>()
            registerAll<ArenaManagers>()
        }
        hookComponents()
        registerSettings()
        BukkitRegistry.FLOOR_RENDERER[KingOfTheHill] = simpleFloorRenderer(KingOfTheHill.SPECIAL_SQUARES)
        BukkitRegistry.CHESS_STATS_PROVIDER["yaml"] = ::YamlChessStats
    }
}