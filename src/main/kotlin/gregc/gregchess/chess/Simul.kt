package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.*
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*

class Simul(private val arena: String, private val settings: ChessGame.Settings) {

    companion object {
        private val ChessPlayer.player
            get() = (this as BukkitChessPlayer).player
        private val ChessPlayer.currentGame
            get() = ChessManager[player]!!.game
    }

    private val data = mutableMapOf<UUID, PlayerData>()

    class SimulManager(val game: ChessGame, val simul: Simul): Component {
        private val location = MutableBySides(game[ChessSide.WHITE].player.location, game[ChessSide.BLACK].player.location)

        @GameEvent(GameBaseEvent.START, mod = TimeModifier.LATE)
        fun start() {
            location.white = game.renderer.spawnLocation
            location.black = game.renderer.spawnLocation
            game.scoreboard += object : PlayerProperty(ConfigManager.getString("Component.Simul.Current")) {
                override fun invoke(s: ChessSide): String {
                    val p = game[s]
                    val games = simul.gamesOf(p.player)
                    val current = games.indexOf(p.currentGame) + 1
                    return "$current/${games.size}"
                }
            }
        }
        fun getPlayer(g: ChessGame) =
            if (g[ChessSide.WHITE].player == g[ChessSide.BLACK].player) g.currentPlayer else g[game.currentPlayer.player]!!

        private fun chooseGame(): ChessGame? {
            val player = game.currentPlayer
            val games = simul.gamesOf(player.player).filter { it != game }
            if (games.isEmpty())
                return null

            val against = games.filter {getPlayer(it).hasTurn}
            return if (against.isNotEmpty()){
                against.minByOrNull { it.board.halfMoveCounter }!!
            } else {
                games.minByOrNull { it.board.halfMoveCounter }!!
            }
        }


        @GameEvent(GameBaseEvent.END_TURN, mod = TimeModifier.LATE)
        fun endTurn() {
            val player = game.currentPlayer
            location[player.side] = player.player.location
            val newGame = chooseGame()
            if(newGame != null) {
                ChessManager.moveToGame(player.player, newGame)
                player.sendTitle(ConfigManager.getString("Message.Teleporting"))
                val newPlayer = getPlayer(newGame)
                TimeManager.runTaskLater(ConfigManager.getDuration("Chess.SimulDelay")) {
                    location[player.side] = player.player.location
                    newGame.renderer.resetPlayer(player.player)
                    newGame.getComponent(SimulManager::class)!!.let {
                        player.player.teleport(it.location[newPlayer.side])
                        it.playingAgainstMessage()
                    }
                }
            }
        }

        private fun playingAgainstMessage() {
            game.currentPlayer.sendTitle(
                ConfigManager.getFormatString("Title.YouArePlayingAgainst", game.currentPlayer.opponent.name),
                ConfigManager.getString("Title.YouArePlayingAs."+game.currentTurn.standardName)
            )
        }

        @GameEvent(GameBaseEvent.REMOVE_PLAYER, mod = TimeModifier.LATE)
        fun movePlayer(player: Player) {
            if (ChessManager[player]?.game != game)
                return
            val newGame = chooseGame()
            if(newGame != null) {
                ChessManager.moveToGame(player, newGame)
                ChessManager[player]!!.sendTitle(ConfigManager.getString("Message.Teleporting"))
                val newPlayer = getPlayer(newGame)
                TimeManager.runTaskLater(ConfigManager.getDuration("Chess.SimulDelay")) {
                    newGame.renderer.resetPlayer(player)
                    newGame.getComponent(SimulManager::class)!!.let {
                        player.teleport(it.location[newPlayer.side])
                        it.playingAgainstMessage()
                    }
                }
            } else {
                player.playerData = simul.data[player.uniqueId]!!
            }
        }
    }

    private val games = mutableListOf<ChessGame>()

    private var currentOffset = settings.renderer.offset ?: Loc(0,0,0)

    fun gamesOf(p: Player) = games.filter {it.running && p in it}

    fun start() {
        cRequire(settings.clock == null, "Simul.Clock")
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
        newGame.addPlayers {
            human(Bukkit.getPlayer(white)!!, ChessSide.WHITE, true)
            human(Bukkit.getPlayer(black)!!, ChessSide.BLACK, true)
        }
        newGame.registerComponent(SimulManager(newGame, this))
        games.add(newGame)
        currentOffset += Loc(50,0,0)
    }
}