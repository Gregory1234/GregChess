package gregc.gregchess.bukkit.match

import gregc.gregchess.Color
import gregc.gregchess.Register
import gregc.gregchess.bukkit.GregChessPlugin
import gregc.gregchess.bukkit.player.*
import gregc.gregchess.bukkit.registerEvents
import gregc.gregchess.bukkit.renderer.ResetPlayerEvent
import gregc.gregchess.bukkit.renderer.renderer
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.results.*
import org.bukkit.Bukkit
import org.bukkit.block.BlockFace
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

object ChessMatchManager : Listener {
    @JvmField
    @Register(data = ["quick"])
    val PLUGIN_DISABLED = DrawEndReason(EndReason.Type.EMERGENCY)

    private val matches = mutableMapOf<UUID, ChessMatch>()
    private val playerCurrentMatches = mutableMapOf<UUID, UUID>()
    private val playerSpectatedMatches = mutableMapOf<UUID, UUID>()
    private val playerSides = mutableMapOf<UUID, Map<UUID, Color?>>()

    operator fun get(uuid: UUID): ChessMatch? = matches[uuid]

    internal fun currentMatchOf(uuid: UUID): ChessMatch? = playerCurrentMatches[uuid]?.let(::get)
    internal fun currentSpectatedMatchOf(uuid: UUID): ChessMatch? = playerSpectatedMatches[uuid]?.let(::get)

    internal fun currentSideOf(uuid: UUID): BukkitChessSideFacade? {
        val currentMatch = currentMatchOf(uuid) ?: return null
        return currentMatch[playerSides[uuid]!![currentMatch.uuid] ?: currentMatch.board.currentTurn] as? BukkitChessSideFacade
    }

    internal fun activeMatchesOf(uuid: UUID): Set<ChessMatch> = playerSides[uuid]?.keys?.mapNotNull(::get)?.toSet().orEmpty()

    internal fun setCurrentMatch(uuid: UUID, matchUUID: UUID?) {
        if (matchUUID == null) {
            playerCurrentMatches.remove(uuid)
        } else {
            playerCurrentMatches[uuid] = matchUUID
        }
    }

    internal fun setCurrentSpectatedMatch(uuid: UUID, matchUUID: UUID?) {
        if (matchUUID == null) {
            playerSpectatedMatches.remove(uuid)
        } else {
            playerSpectatedMatches[uuid] = matchUUID
        }
    }

    fun start() {
        registerEvents()
    }

    fun stop() {
        for (g in matches.values)
            g.stop(drawBy(PLUGIN_DISABLED))
    }

    @EventHandler
    fun onPlayerJoin(e: PlayerJoinEvent) {
        Bukkit.getScheduler().scheduleSyncDelayedTask(GregChessPlugin.plugin, { e.gregchessPlayer.sendRejoinReminder() }, 1)
    }

    @EventHandler
    fun onPlayerLeave(e: PlayerQuitEvent) = e.gregchessPlayer.leaveMatch()

    @EventHandler
    fun onPlayerDeath(e: EntityDeathEvent) {
        val ent = e.gregchessPlayer ?: return
        val match = ent.currentChessMatch ?: return
        match.callEvent(ResetPlayerEvent(ent))
    }

    @EventHandler
    fun onPlayerDamage(e: EntityDamageEvent) {
        val ent = e.gregchessPlayer ?: return
        ent.currentChessMatch ?: return
        e.isCancelled = true
    }

    @EventHandler
    fun onBlockClick(e: PlayerInteractEvent) {
        val player = e.gregchessPlayer.currentChessSide ?: return
        e.isCancelled = true
        if (player.hasTurn && e.blockFace != BlockFace.DOWN) {
            val block = e.clickedBlock ?: return
            val pos = player.match.renderer.getPos(block.location)
            if (e.action == Action.LEFT_CLICK_BLOCK && player.held == null) {
                player.pickUp(pos)
            } else if (e.action == Action.RIGHT_CLICK_BLOCK && player.held != null) {
                player.makeMove(pos)
            }
        }
    }

    @EventHandler
    fun onBlockBreak(e: BlockBreakEvent) {
        if (e.gregchessPlayer.isInChessMatch) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryDrag(e: InventoryDragEvent) {
        if (e.gregchessPlayer?.isInChessMatch == true) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        if (e.gregchessPlayer?.isInChessMatch == true) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onItemDrop(e: PlayerDropItemEvent) {
        if (e.gregchessPlayer.isInChessMatch)
            e.isCancelled = true
    }

    internal operator fun plusAssign(g: ChessMatch) {
        matches[g.uuid] = g
        val white = (g.sides.white as? BukkitChessSide)?.uuid
        val black = (g.sides.black as? BukkitChessSide)?.uuid

        if (white != null) {
            playerCurrentMatches[white] = g.uuid
        }
        if (black != null && black != white) {
            playerCurrentMatches[black] = g.uuid
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

    internal operator fun minusAssign(g: ChessMatch) {
        g.sideFacades.forEachReal(GregChessPlugin::clearRequests)
        matches.remove(g.uuid, g)
        val white = (g.sides.white as? BukkitChessSide)?.uuid
        val black = (g.sides.black as? BukkitChessSide)?.uuid

        if (white != null) {
            if (playerCurrentMatches[white] == g.uuid)
                playerCurrentMatches.remove(white)
            playerSides[white] = playerSides[white]!! - g.uuid
            if (playerSides[white]!!.isEmpty())
                playerSides.remove(white)
        }
        if (black != null && black != white) {
            if (playerCurrentMatches[black] == g.uuid)
                playerCurrentMatches.remove(black)
            playerSides[black] = playerSides[black]!! - g.uuid
            if (playerSides[black]!!.isEmpty())
                playerSides.remove(black)
        }
    }

}
