package gregc.gregchess.bukkit

import gregc.gregchess.*
import gregc.gregchess.bukkit.chess.*
import gregc.gregchess.bukkit.chess.component.*
import gregc.gregchess.bukkit.chess.player.BukkitPlayer
import gregc.gregchess.bukkit.chess.player.Stockfish
import gregc.gregchess.bukkitutils.toDuration
import gregc.gregchess.chess.FEN
import gregc.gregchess.chess.Pos
import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.variant.KingOfTheHill
import gregc.gregchess.chess.variant.ThreeChecks
import gregc.gregchess.registry.AutoRegister
import kotlin.time.Duration

internal object GregChessBukkit : ChessExtension {

    private val clockSettings: Map<String, ChessClockData>
        get() = config.getConfigurationSection("Settings.Clock")?.getKeys(false).orEmpty().associateWith {
            val section = config.getConfigurationSection("Settings.Clock.$it")!!
            val t = TimeControl.Type.valueOf(section.getString("Type", TimeControl.Type.INCREMENT.toString())!!)
            val initial = section.getString("Initial")!!.toDuration()
            val increment = if (t.usesIncrement) section.getString("Increment")!!.toDuration() else Duration.ZERO
            ChessClockData(TimeControl(t, initial, increment))
        }

    private fun registerSettings() = with(GregChess) {
        registerSettings {
            when(val name = section.getString("Board")) {
                null -> ChessboardState(variant)
                "normal" -> ChessboardState(variant)
                "chess960" -> ChessboardState(variant, chess960 = true)
                else -> if (name.startsWith("fen ")) try {
                    ChessboardState(variant, FEN.parseFromString(name.drop(4)))
                } catch (e: FEN.FENFormatException) {
                    logger.warn("Chessboard configuration ${e.fen} is in a wrong format, defaulted to normal: ${e.cause?.message}!")
                    ChessboardState(variant)
                } else {
                    logger.warn("Invalid chessboard configuration $name, defaulted to normal!")
                    ChessboardState(variant)
                }
            }
        }
        registerSettings {
            SettingsManager.chooseOrParse(clockSettings, section.getString("Clock")) {
                TimeControl.parseOrNull(it)?.let { t -> ChessClockData(t) } ?: run {
                    logger.warn("Bad time control \"$it\", defaulted to none")
                    null
                }
            }
        }
        registerSettings { BukkitRendererSettings() }
        registerSettings { ThreeChecks.CheckCounterData(section.getInt("CheckLimit", 3).toUInt()) }
    }

    private fun registerComponents() = with(GregChess) {
        registerSimpleComponent<BukkitEventRelay>("bukkit_event_relay")
        registerSimpleComponent<BukkitGregChessAdapter>("bukkit_adapter")
        registerComponent<BukkitRenderer, BukkitRendererSettings>("bukkit_renderer")
        registerSimpleComponent<GameController>("game_controller")
        registerSimpleComponent<ScoreboardManager>("scoreboard_manager")
        registerSimpleComponent<SpectatorManager>("spectator_manager")
        for (c in listOf(Chessboard::class, ChessClock::class, GameController::class,
            SpectatorManager::class, ScoreboardManager::class, BukkitRenderer::class,
            BukkitEventRelay::class, BukkitGregChessAdapter::class)) {
            registerHookedComponent(c)
        }
    }

    private fun registerPlayerTypes() = with(GregChess) {
        registerPlayerClass<BukkitPlayer>("bukkit")
        registerPlayerClass<Stockfish>("stockfish")
    }

    override fun load(): Unit = with(GregChess) {
        AutoRegister(this, AutoRegister.bukkitTypes).apply {
            registerAll<Arena>()
            registerAll<ChessGameManager>()
            registerAll<ScoreboardManager>()
            registerAll<BukkitGregChessAdapter>()
        }
        registerComponents()
        registerSettings()
        completeSimpleSettings()
        completeLocalFormatters()
        registerSimpleFloorRenderer(KingOfTheHill, (Pair(3, 3)..Pair(4, 4)).map { (x,y) -> Pos(x,y) })
        completeFloorRenderers()
        registerPlayerTypes()
        registerBukkitPlugin(GregChessPlugin.plugin)
    }
}