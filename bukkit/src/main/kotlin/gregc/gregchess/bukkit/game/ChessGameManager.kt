package gregc.gregchess.bukkit.game

import gregc.gregchess.Color
import gregc.gregchess.Register
import gregc.gregchess.bukkit.GregChessPlugin
import gregc.gregchess.bukkit.player.*
import gregc.gregchess.bukkit.registerEvents
import gregc.gregchess.bukkit.renderer.ResetPlayerEvent
import gregc.gregchess.bukkit.renderer.renderer
import gregc.gregchess.game.ChessGame
import gregc.gregchess.results.*
import org.bukkit.Bukkit
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.*
import java.util.*

object ChessGameManager : Listener {
    @JvmField
    @Register(data = ["quick"])
    val PLUGIN_DISABLED = DrawEndReason(EndReason.Type.EMERGENCY)

    private val games = mutableMapOf<UUID, ChessGame>()
    private val playerCurrentGames = mutableMapOf<UUID, UUID>()
    private val playerSpectatedGames = mutableMapOf<UUID, UUID>()
    private val playerSides = mutableMapOf<UUID, Map<UUID, Color?>>()

    operator fun get(uuid: UUID): ChessGame? = games[uuid]

    internal fun currentGameOf(uuid: UUID): ChessGame? = playerCurrentGames[uuid]?.let(::get)
    internal fun currentSpectatedGameOf(uuid: UUID): ChessGame? = playerSpectatedGames[uuid]?.let(::get)

    internal fun currentSideOf(uuid: UUID): BukkitChessSide? {
        val currentGame = currentGameOf(uuid) ?: return null
        return currentGame[playerSides[uuid]!![currentGame.uuid] ?: currentGame.currentTurn] as? BukkitChessSide
    }

    internal fun activeGamesOf(uuid: UUID): Set<ChessGame> = playerSides[uuid]?.keys?.mapNotNull(::get)?.toSet().orEmpty()

    internal fun setCurrentGame(uuid: UUID, gameUUID: UUID?) {
        if (gameUUID == null) {
            playerCurrentGames.remove(uuid)
        } else {
            playerCurrentGames[uuid] = gameUUID
        }
    }

    internal fun setCurrentSpectatedGame(uuid: UUID, gameUUID: UUID?) {
        if (gameUUID == null) {
            playerSpectatedGames.remove(uuid)
        } else {
            playerSpectatedGames[uuid] = gameUUID
        }
    }

    fun start() {
        registerEvents()
    }

    fun stop() {
        for (g in games.values)
            g.quickStop(drawBy(PLUGIN_DISABLED))
    }

    @EventHandler
    fun onPlayerJoin(e: PlayerJoinEvent) {
        Bukkit.getScheduler().scheduleSyncDelayedTask(GregChessPlugin.plugin, { e.player.sendRejoinReminder() }, 1)
    }

    @EventHandler
    fun onPlayerLeave(e: PlayerQuitEvent) = e.player.leaveGame()

    @EventHandler
    fun onPlayerDeath(e: EntityDeathEvent) {
        val ent = e.entity as? Player ?: return
        val game = ent.currentChessGame ?: return
        game.callEvent(ResetPlayerEvent(ent))
    }

    @EventHandler
    fun onPlayerDamage(e: EntityDamageEvent) {
        val ent = e.entity as? Player ?: return
        ent.currentChessGame ?: return
        e.isCancelled = true
    }

    @EventHandler
    fun onBlockClick(e: PlayerInteractEvent) {
        val player = e.player.currentChessSide ?: return
        e.isCancelled = true
        if (player.hasTurn && e.blockFace != BlockFace.DOWN) {
            val block = e.clickedBlock ?: return
            val pos = player.game.renderer.getPos(block.location)
            if (e.action == Action.LEFT_CLICK_BLOCK && player.held == null) {
                player.pickUp(pos)
            } else if (e.action == Action.RIGHT_CLICK_BLOCK && player.held != null) {
                player.makeMove(pos)
            }
        }
    }

    @EventHandler
    fun onBlockBreak(e: BlockBreakEvent) {
        if (e.player.isInChessGame) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryDrag(e: InventoryDragEvent) {
        if (e.whoClicked.let { it is Player && it.isInChessGame }) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        if (e.whoClicked.let { it is Player && it.isInChessGame }) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onItemDrop(e: PlayerDropItemEvent) {
        if (e.player.isInChessGame)
            e.isCancelled = true
    }

    internal operator fun plusAssign(g: ChessGame) {
        games[g.uuid] = g
        val white = g.playerData.white.value as? UUID
        val black = g.playerData.black.value as? UUID

        if (white != null) {
            playerCurrentGames[white] = g.uuid
        }
        if (black != null && black != white) {
            playerCurrentGames[black] = g.uuid
        }

        if (white != null && white == black) {
            playerSides[white] = playerSides[white].orEmpty() + Pair(g.uuid, null)
        } else if (white != null && black != null) {
            playerSides[white] = playerSides[white].orEmpty() + Pair(g.uuid, Color.WHITE)
            playerSides[black] = playerSides[black].orEmpty() + Pair(g.uuid, Color.BLACK)
        } else if (white != null) {
            playerSides[white] = playerSides[white].orEmpty() + Pair(g.uuid, Color.WHITE)
        } else if (black != null) {
            playerSides[black] = playerSides[black].orEmpty() + Pair(g.uuid, Color.BLACK)
        }
    }

    internal operator fun minusAssign(g: ChessGame) {
        g.sides.forEachUnique(GregChessPlugin::clearRequests)
        games.remove(g.uuid, g)
        val white = g.playerData.white.value as? UUID
        val black = g.playerData.black.value as? UUID

        if (white != null) {
            if (playerCurrentGames[white] == g.uuid)
                playerCurrentGames.remove(white)
            playerSides[white] = playerSides[white]!! - g.uuid
            if (playerSides[white]!!.isEmpty())
                playerSides.remove(white)
        }
        if (black != null && black != white) {
            if (playerCurrentGames[black] == g.uuid)
                playerCurrentGames.remove(black)
            playerSides[black] = playerSides[black]!! - g.uuid
            if (playerSides[black]!!.isEmpty())
                playerSides.remove(black)
        }
    }

}
