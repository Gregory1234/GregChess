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

    private val clockSettings: Map<String, ChessClock>
        get() = config.getConfigurationSection("Settings.Clock")?.getKeys(false).orEmpty().associateWith {
            val section = config.getConfigurationSection("Settings.Clock.$it")!!
            val t = TimeControl.Type.valueOf(section.getString("Type", TimeControl.Type.INCREMENT.toString())!!)
            val initial = section.getString("Initial")!!.toDuration()
            val increment = if (t.usesIncrement) section.getString("Increment")!!.toDuration() else Duration.ZERO
            ChessClock(TimeControl(t, initial, increment))
        }

    private fun registerSettings() = with(GregChess) {
        ComponentType.CHESSBOARD.registerSettings {
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
        ComponentType.CLOCK.registerSettings {
            SettingsManager.chooseOrParse(clockSettings, section.getString("Clock")) {
                TimeControl.parseOrNull(it)?.let { t -> ChessClock(t) } ?: run {
                    logger.warn("Bad time control \"$it\", defaulted to none")
                    null
                }
            }
        }
        BukkitComponentType.RENDERER.registerSettings { BukkitRenderer() }
        ThreeChecks.CHECK_COUNTER.registerSettings { ThreeChecks.CheckCounter(section.getInt("CheckLimit", 3).toUInt()) }
    }

    private fun hookComponents() {
        for (c in listOf(ComponentType.CHESSBOARD, ComponentType.CLOCK, BukkitComponentType.GAME_CONTROLLER,
            BukkitComponentType.SPECTATOR_MANAGER, BukkitComponentType.SCOREBOARD_MANAGER, BukkitComponentType.RENDERER,
            BukkitComponentType.EVENT_RELAY, BukkitComponentType.ADAPTER)) {
            c.registerHooked()
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
            registerAll<BukkitComponentType>()
        }
        hookComponents()
        registerSettings()
        completeSimpleSettings()
        completeLocalFormatters()
        registerSimpleFloorRenderer(KingOfTheHill, (Pair(3, 3)..Pair(4, 4)).map { (x,y) -> Pos(x,y) })
        completeFloorRenderers()
        registerPlayerTypes()
        registerBukkitPlugin(GregChessPlugin.plugin)
    }
}