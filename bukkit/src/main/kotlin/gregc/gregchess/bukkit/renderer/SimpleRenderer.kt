package gregc.gregchess.bukkit.renderer

import gregc.gregchess.bukkit.NO_ARENAS
import gregc.gregchess.bukkit.component.BukkitComponentType
import gregc.gregchess.bukkit.match.MatchInfoEvent
import gregc.gregchess.bukkit.player.*
import gregc.gregchess.bukkit.registerEvents
import gregc.gregchess.bukkitutils.CommandException
import gregc.gregchess.event.ChessBaseEvent
import gregc.gregchess.event.EventListenerRegistry
import gregc.gregchess.match.ChessMatch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.bukkit.block.BlockFace
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent

@Serializable
class SimpleRenderer : Renderer {
    override val type get() = BukkitComponentType.SIMPLE_RENDERER

    @Transient
    override lateinit var arena: SimpleArena
        private set

    override val style: SimpleRendererStyle get() = DefaultSimpleRendererStyle

    override fun init(match: ChessMatch, events: EventListenerRegistry) {
        arena = SimpleArenaManager.reserveArenaOrNull(match) ?: throw CommandException(NO_ARENAS)
        events.register<ChessBaseEvent> {
            if (it == ChessBaseEvent.CLEAR || it == ChessBaseEvent.PANIC) arena.currentMatch = null
        }
        events.registerR<MatchInfoEvent> {
            text("Arena: ${arena.name}\n")
        }
        events.registerR<SpectatorEvent> {
            when (dir) {
                PlayerDirection.JOIN -> player.entity?.let(arena::resetSpectator)
                PlayerDirection.LEAVE -> player.entity?.let(arena::leave)
            }
        }
        events.registerR<PlayerEvent> {
            when (dir) {
                PlayerDirection.JOIN -> player.entity?.let(arena::reset)
                PlayerDirection.LEAVE -> player.entity?.let(arena::leave)
            }
        }
        events.registerR<ResetPlayerEvent> {
            player.entity?.let {
                arena.reset(it)
                it.inventory.setItem(0, player.currentSide?.held?.piece?.let(style::pieceItem))
            }
        }
        events.registerR<PiecePlayerActionEvent> {
            player.entity?.inventory?.setItem(0, player.currentSide?.held?.piece?.let(style::pieceItem))
        }
    }
}

val ChessMatch.simpleRenderer get() = renderer as? SimpleRenderer

object SimpleRendererListener : Listener {
    fun start() {
        registerEvents()
    }

    @EventHandler
    fun onPlayerDeath(e: EntityDeathEvent) {
        val ent = e.gregchessPlayer ?: return
        val match = ent.currentMatch ?: return
        match.simpleRenderer ?: return
        match.callEvent(ResetPlayerEvent(ent))
    }

    @EventHandler
    fun onPlayerDamage(e: EntityDamageEvent) {
        val ent = e.gregchessPlayer ?: return
        val match = ent.currentMatch ?: return
        match.simpleRenderer ?: return
        match.callEvent(ResetPlayerEvent(ent))
        e.isCancelled = true
    }

    @EventHandler
    fun onBlockClick(e: PlayerInteractEvent) {
        val player = e.gregchessPlayer.currentSide ?: return
        val renderer = player.match.simpleRenderer ?: return
        e.isCancelled = true
        if (player.hasTurn && e.blockFace != BlockFace.DOWN) {
            val block = e.clickedBlock ?: return
            val pos = renderer.arena.getPos(block.location) ?: return
            if (e.action == Action.LEFT_CLICK_BLOCK && player.held == null) {
                player.pickUp(pos)
            } else if (e.action == Action.RIGHT_CLICK_BLOCK && player.held != null) {
                player.makeMove(pos)
            }
        }
    }

    @EventHandler
    fun onBlockBreak(e: BlockBreakEvent) {
        e.gregchessPlayer.currentMatch?.simpleRenderer ?: return
        e.isCancelled = true
    }

    @EventHandler
    fun onInventoryDrag(e: InventoryDragEvent) {
        e.gregchessPlayer?.currentMatch?.simpleRenderer ?: return
        e.isCancelled = true
    }

    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        e.gregchessPlayer?.currentMatch?.simpleRenderer ?: return
        e.isCancelled = true
    }

    @EventHandler
    fun onItemDrop(e: PlayerDropItemEvent) {
        e.gregchessPlayer.currentMatch?.simpleRenderer ?: return
        e.isCancelled = true
    }
}