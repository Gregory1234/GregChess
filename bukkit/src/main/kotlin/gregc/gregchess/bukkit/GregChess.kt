package gregc.gregchess.bukkit

import gregc.gregchess.CoreAutoRegister
import gregc.gregchess.CoreRegistry
import gregc.gregchess.board.Chessboard
import gregc.gregchess.board.FEN
import gregc.gregchess.bukkit.component.BukkitComponentType
import gregc.gregchess.bukkit.component.MatchController
import gregc.gregchess.bukkit.match.ChessMatchManager
import gregc.gregchess.bukkit.player.BukkitChessSideType
import gregc.gregchess.bukkit.properties.BukkitGregChessAdapter
import gregc.gregchess.bukkit.renderer.*
import gregc.gregchess.bukkit.stats.YamlChessStats
import gregc.gregchess.bukkit.variant.simpleFloorRenderer
import gregc.gregchess.bukkitutils.toDuration
import gregc.gregchess.clock.ChessClock
import gregc.gregchess.clock.TimeControl
import gregc.gregchess.component.ComponentType
import gregc.gregchess.match.ChessTimeManager
import gregc.gregchess.variant.KingOfTheHill
import gregc.gregchess.variant.ThreeChecks
import kotlin.time.Duration

internal object GregChess : BukkitChessModule(GregChessPlugin.plugin) {

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
        BukkitRegistry.SETTINGS_PARSER[ComponentType.CLOCK] = {
            section.getString("Clock")?.let { clock ->
                TimeControl.parseOrNull(clock)?.let { t -> ChessClock(t) }
            } ?: section.getConfigurationSection("Clock")?.let { s ->
                val t = TimeControl.Type.valueOf(s.getString("Type", TimeControl.Type.INCREMENT.toString())!!)
                val initial = s.getString("Initial")!!.toDuration()
                val increment = if (t.usesIncrement) s.getString("Increment")!!.toDuration() else Duration.ZERO
                ChessClock(TimeControl(t, initial, increment))
            }
        }
        BukkitRegistry.SETTINGS_PARSER[ThreeChecks.CHECK_COUNTER] = { ThreeChecks.CheckCounter(section.getInt("CheckLimit", 3)) }
        BukkitRegistry.SETTINGS_PARSER[ComponentType.TIME] = { ChessTimeManager() }
    }

    private fun registerOptionalComponents() {
        for (c in listOf(ComponentType.CLOCK)) {
            BukkitRegistry.OPTIONAL_COMPONENTS += c
        }
    }

    private fun registerRequiredComponents() {
        for (c in listOf(ComponentType.CHESSBOARD, ComponentType.TIME, BukkitComponentType.SPECTATOR_MANAGER)) {
            BukkitRegistry.REQUIRED_COMPONENTS += c
        }
    }

    private fun registerImpliedComponents() {
        BukkitRegistry.IMPLIED_COMPONENTS["match_controller"] = { MatchController }
        BukkitRegistry.IMPLIED_COMPONENTS["adapter"] = { BukkitGregChessAdapter }
        BukkitRegistry.IMPLIED_COMPONENTS["renderer"] = {
            val ct = config.getFromRegistry(CoreRegistry.COMPONENT_TYPE, "Renderer.Type")!!
            ct.cl.constructors.first { it.parameters.isEmpty() }.call() as Renderer
        }
    }

    override fun load() {
        CoreAutoRegister.registerAll(this)
        BukkitAutoRegister(this).apply {
            registerAll<ChessMatchManager>()
            registerAll<BukkitComponentType>()
            registerAll<BukkitChessSideType>()
            registerAll<SimpleArena>()
        }
        registerOptionalComponents()
        registerRequiredComponents()
        registerImpliedComponents()
        registerSettings()
        BukkitRegistry.FLOOR_RENDERER[KingOfTheHill] = simpleFloorRenderer(KingOfTheHill.SPECIAL_SQUARES)
        BukkitRegistry.CHESS_STATS_PROVIDER["yaml"] = ::YamlChessStats
        BukkitRegistry.RENDERER_STYLE["default"] = DefaultSimpleRendererStyle
    }
}