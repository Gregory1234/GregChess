package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.*
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.*

class Simul(val arena: String, val settings: ChessGame.Settings) {

    private val data = mutableMapOf<UUID, PlayerData>()

    class SimulManager(val game: ChessGame, val simul: Simul): Component {
        private lateinit var whiteLocation: Location
        private lateinit var blackLocation: Location

        @GameEvent(GameBaseEvent.START, TimeModifier.LATE)
        fun start() {
            whiteLocation = game.renderer.spawnLocation
            blackLocation = game.renderer.spawnLocation
            game.scoreboard += object : PlayerProperty(ConfigManager.getString("Component.Simul.Current")) {
                override fun invoke(s: ChessSide): String {
                    val p = (game[s] as BukkitChessPlayer).player
                    val games = simul.gamesOf(p)
                    val current = games.indexOf(ChessManager[p]!!.game) + 1
                    return "$current/${games.size}"
                }
            }
        }
        fun getPlayer(g: ChessGame) =
            if ((g[ChessSide.WHITE] as BukkitChessPlayer).player == (g[ChessSide.BLACK] as BukkitChessPlayer).player) g[game.currentTurn] as BukkitChessPlayer else g[(game[game.currentTurn] as BukkitChessPlayer).player] as BukkitChessPlayer

        private fun chooseGame(): ChessGame? {
            val player = game[game.currentTurn] as BukkitChessPlayer
            val games = simul.gamesOf(player.player).filter { it != game }
            if (games.isEmpty())
                return null

            val against = games.filter {getPlayer(it).hasTurn}
            if (against.isNotEmpty()){
                val clocked = against.filter {it.clock != null}.sortedBy { it.clock?.getTimeRemaining(getPlayer(it).side) }
                return clocked.firstOrNull() ?: against.minByOrNull { it.board.halfMoveCounter }!!
            }
            else {
                return games.minByOrNull { it.board.halfMoveCounter }!!
            }
        }


        @GameEvent(GameBaseEvent.END_TURN, TimeModifier.LATE)
        fun endTurn() {
            val player = game[game.currentTurn] as BukkitChessPlayer
            if (game.currentTurn == ChessSide.WHITE)
                whiteLocation = player.player.location
            else
                blackLocation = player.player.location
            val newGame = chooseGame()
            if(newGame != null) {
                ChessManager.moveToGame(player.player, newGame)
                player.sendTitle(ConfigManager.getString("Message.Teleporting"))
                TimeManager.runTaskLater(ConfigManager.getDuration("Chess.SimulDelay")) {
                    newGame.renderer.resetPlayer(player.player)
                    player.player.teleport(
                        newGame.getComponent(SimulManager::class)!!
                            .let {
                                it.startTurn()
                                if (getPlayer(newGame).side == ChessSide.WHITE) it.whiteLocation else it.blackLocation
                            })
                }
            }
        }
        @GameEvent(GameBaseEvent.START_TURN, TimeModifier.LATE)
        fun startTurn() {
            val player = game[game.currentTurn] as BukkitChessPlayer
            if (ChessManager[player.player]?.game != game)
                return
            player.sendTitle(ConfigManager.getFormatString("Title.YouArePlayingAgainst", player.opponent.name), ConfigManager.getString("Title.YouArePlayingAs."+player.side.standardName))
        }
        @GameEvent(GameBaseEvent.REMOVE_PLAYER, TimeModifier.LATE)
        fun movePlayer(player: Player) {
            if (ChessManager[player]?.game != game)
                return
            val newGame = chooseGame()
            if(newGame != null) {
                ChessManager.moveToGame(player, newGame)
                newGame.renderer.resetPlayer(player)
                player.teleport(newGame.getComponent(SimulManager::class)!!.let {if (getPlayer(newGame).side == ChessSide.WHITE) it.whiteLocation else it.blackLocation})
            } else {
                player.playerData = simul.data[player.uniqueId]!!
            }
        }
    }

    private val games = mutableListOf<ChessGame>()

    private var currentOffset = settings.renderer.offset ?: Loc(0,0,0)

    fun gamesOf(p: Player) = games.filter {it.running && p in it}

    fun start() {
        val pls = mutableListOf<Player>()
        games.forEach{ g ->
            g.forEachPlayer { p ->
                if (p.uniqueId !in pls.map { it.uniqueId })
                    pls += p
            }
        }
        pls.forEach {
            data[it.uniqueId] = it.playerData
        }
        games.forEach{ it.start() }
        pls.forEach {
            val newGame = gamesOf(it).first()
            ChessManager.moveToGame(it, newGame)
            it.teleport(newGame.renderer.spawnLocation)
        }
    }

    fun addGame(white: String, black: String) {
        val newGame = ChessGame(settings.copy(renderer = settings.renderer.copy(arenaWorld = arena, offset = currentOffset)))
        newGame.registerComponent(SimulManager(newGame, this))
        newGame.addPlayers {
            human(Bukkit.getPlayer(white)!!, ChessSide.WHITE, true)
            human(Bukkit.getPlayer(black)!!, ChessSide.BLACK, true)
        }
        games.add(newGame)
        currentOffset += Loc(50,0,0)
    }
}