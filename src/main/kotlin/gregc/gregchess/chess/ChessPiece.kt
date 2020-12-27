package gregc.gregchess.chess

import gregc.gregchess.chatColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.inventory.ItemStack
import java.lang.IllegalArgumentException

class ChessPiece(val type: Type, val side: ChessSide, initPos: ChessPosition, private val game: ChessGame) {

    enum class Type(private val prettyName: String, private val matWhite: Material, private val matBlack: Material, val pickUpSound: Sound, val moveSound: Sound, val captureSound: Sound, val moveScheme: ChessMoveScheme, val minor: Boolean) {
        KING("King", Material.WHITE_CONCRETE, Material.BLACK_CONCRETE, Sound.BLOCK_METAL_HIT, Sound.BLOCK_METAL_STEP, Sound.ENTITY_ENDER_DRAGON_DEATH, ChessMoveScheme.King, false),
        QUEEN( "Queen", Material.DIAMOND_BLOCK, Material.NETHERITE_BLOCK, Sound.ENTITY_WITCH_CELEBRATE, Sound.BLOCK_GLASS_STEP, Sound.ENTITY_WITCH_DEATH, ChessMoveScheme.Queen, false),
        ROOK("Rook", Material.IRON_BLOCK, Material.GOLD_BLOCK, Sound.ENTITY_IRON_GOLEM_STEP, Sound.ENTITY_IRON_GOLEM_STEP, Sound.ENTITY_IRON_GOLEM_DEATH, ChessMoveScheme.Rook, false),
        BISHOP("Bishop", Material.POLISHED_DIORITE, Material.POLISHED_BLACKSTONE, Sound.ENTITY_SPIDER_AMBIENT, Sound.ENTITY_SPIDER_STEP, Sound.ENTITY_SPIDER_DEATH, ChessMoveScheme.Bishop, true),
        KNIGHT("Knight", Material.END_STONE, Material.BLACKSTONE, Sound.ENTITY_HORSE_JUMP, Sound.ENTITY_HORSE_STEP, Sound.ENTITY_HORSE_DEATH, ChessMoveScheme.Knight, true),
        PAWN("Pawn", Material.WHITE_CARPET, Material.BLACK_CARPET, Sound.BLOCK_STONE_HIT, Sound.BLOCK_STONE_STEP, Sound.BLOCK_STONE_BREAK, ChessMoveScheme.Pawn, false);

        fun getMaterial(side: ChessSide) = when (side) {
            ChessSide.WHITE -> matWhite
            ChessSide.BLACK -> matBlack
        }

        companion object {
            fun parseFromChar(c: Char) = when (c.toLowerCase()) {
                'k' -> KING
                'q' -> QUEEN
                'r' -> ROOK
                'b' -> BISHOP
                'n' -> KNIGHT
                'p' -> PAWN
                else -> throw IllegalArgumentException(c.toString())
            }
        }

        fun getItem(side: ChessSide): ItemStack {
            val item = ItemStack(getMaterial(side))
            val meta = item.itemMeta!!
            meta.setDisplayName(chatColor("&b${side.prettyName} $prettyName"))
            item.itemMeta = meta
            return item
        }


    }

    override fun toString() = "ChessPiece(type = $type, side = $side, pos = $pos, hasMoved = $hasMoved)"
    var hasMoved = false
    private val material = type.getMaterial(side)
    val moveScheme = type.moveScheme
    private val block
        get() = pos.getBlock(game.arena.world)

    var pos: ChessPosition = initPos
        set(newPos) {
            playSound(type.moveSound)
            hide()
            field = newPos
            render()
            hasMoved = true
        }

    fun pickUp(){
        playSound(type.pickUpSound)
        hide()
    }

    fun capture() {
        playSound(type.captureSound)
        game.board.remove(this)
        hide()
    }

    fun placeBackDown() {
        playSound(type.moveSound)
        render()
    }

    fun render() {
        block.type = material
    }

    private fun hide() {
        block.type = Material.AIR
    }

    val promotions = if (type == Type.PAWN) listOf(Type.QUEEN, Type.ROOK, Type.BISHOP, Type.KNIGHT) else emptyList()

    fun promote(piece: ChessPiece) {
        if (piece.type !in promotions)
            throw IllegalArgumentException(piece.type.toString())
        capture()
        game.board += piece
        piece.render()
    }

    fun getMoves() = moveScheme.genMoves(game, pos).filter { it.finish != ChessMoveScheme.Move.Finish.DEFEND }

    private fun playSound(s: Sound, volume: Float = 3.0f, pitch: Float = 1.0f) = game.arena.world.playSound(pos.toLoc().toLocation(game.arena.world), s, volume, pitch)

    companion object {

        fun parseFromString(chessGame: ChessGame, s: String): ChessPiece = ChessPiece(
                Type.parseFromChar(s[1]),
                ChessSide.parseFromChar(s[0]),
                ChessPosition.parseFromString(s.substring(2..3)), chessGame)
    }
}