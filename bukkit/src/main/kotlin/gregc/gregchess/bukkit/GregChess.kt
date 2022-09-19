package gregc.gregchess.bukkit

import gregc.gregchess.GregChessCore
import gregc.gregchess.board.Chessboard
import gregc.gregchess.board.FEN
import gregc.gregchess.bukkit.component.*
import gregc.gregchess.bukkit.match.ChessMatchManager
import gregc.gregchess.bukkit.match.SettingsManager
import gregc.gregchess.bukkit.player.BukkitChessSideType
import gregc.gregchess.bukkit.properties.BukkitGregChessAdapter
import gregc.gregchess.bukkit.properties.SimpleScoreboardLayout
import gregc.gregchess.bukkit.registry.BukkitRegistry
import gregc.gregchess.bukkit.renderer.BukkitRenderer
import gregc.gregchess.bukkit.renderer.simpleFloorRenderer
import gregc.gregchess.bukkit.stats.YamlChessStats
import gregc.gregchess.bukkitutils.toDuration
import gregc.gregchess.clock.ChessClock
import gregc.gregchess.clock.TimeControl
import gregc.gregchess.component.ComponentType
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
            when(val name = section.getString("Board")) {
                null -> Chessboard(variant, variantOptions)
                "normal" -> Chessboard(variant, variantOptions)
                else -> if (name.startsWith("fen ")) try {
                    val fen = FEN.parseFromString(name.drop(4))
                    Chessboard(variant, variantOptions, fen)
                } catch (e: FEN.FENFormatException) {
                    logger.warn("Chessboard configuration ${e.fen} is in a wrong format, defaulted to normal: ${e.cause?.message}!")
                    Chessboard(variant, variantOptions)
                } else {
                    logger.warn("Invalid chessboard configuration $name, defaulted to normal!")
                    Chessboard(variant, variantOptions)
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
        BukkitRegistry.SETTINGS_PARSER[ThreeChecks.CHECK_COUNTER] = { ThreeChecks.CheckCounter(section.getInt("CheckLimit", 3)) }
    }

    private fun registerOptionalComponents() {
        for (c in listOf(ComponentType.CLOCK)) {
            BukkitRegistry.OPTIONAL_COMPONENTS += c
        }
    }

    private fun registerRequiredComponents() {
        for (c in listOf(ComponentType.CHESSBOARD, BukkitComponentType.SPECTATOR_MANAGER, BukkitComponentType.SCOREBOARD_MANAGER)) {
            BukkitRegistry.REQUIRED_COMPONENTS += c
        }
        for (a in listOf(ComponentAlternative.RENDERER)) {
            BukkitRegistry.REQUIRED_COMPONENT_ALTERNATIVES += a
        }
    }

    private fun registerImpliedComponents() {
        BukkitRegistry.IMPLIED_COMPONENTS[BukkitComponentType.MATCH_CONTROLLER] = { MatchController }
        BukkitRegistry.IMPLIED_COMPONENTS[BukkitComponentType.ADAPTER] = { BukkitGregChessAdapter }
    }

    override fun load() {
        GregChessCore.registerAll(this)
        BukkitGregChessCore.autoRegister(this).apply {
            registerAll<ChessMatchManager>()
            registerAll<BukkitComponentType>()
            registerAll<BukkitChessSideType>()
            registerAll<ComponentAlternative<*>>()
        }
        registerOptionalComponents()
        registerRequiredComponents()
        registerImpliedComponents()
        registerSettings()
        BukkitRegistry.FLOOR_RENDERER[KingOfTheHill] = simpleFloorRenderer(KingOfTheHill.SPECIAL_SQUARES)
        BukkitRegistry.CHESS_STATS_PROVIDER["yaml"] = ::YamlChessStats
        BukkitRegistry.SCOREBOARD_LAYOUT_PROVIDER["simple"] = ::SimpleScoreboardLayout
    }
}