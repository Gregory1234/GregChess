package gregc.gregchess.chess

import gregc.gregchess.Loc
import org.bukkit.Material

class ChessBoard(private val game: ChessGame) {
    private val world = game.arena.world
    private val pieces =
            (listOf("wKe1", "bKe8", "wQd1", "bQd8", "wRa1", "wRh1", "bRa8", "bRh8", "wBc1", "wBf1", "bBc8", "bBf8", "wNb1", "wNg1", "bNb8", "bNg8")
                    .map { ChessPiece.parseFromString(game, it) } +
                    (0..7).flatMap {
                        listOf(
                                ChessPiece(ChessPiece.Type.PAWN, ChessSide.WHITE, ChessPosition(it, 1), game),
                                ChessPiece(ChessPiece.Type.PAWN, ChessSide.BLACK, ChessPosition(it, 6), game))
                    }).toMutableList()

    fun clearMarkings() {
        for (i in 0 until 8 * 3) {
            for (j in 0 until 8 * 3) {
                val b = (i.div(3) + j.div(3)) % 2 == 1
                world.getBlockAt(i + 8, 101, j + 8).type = if (b) Material.SPRUCE_PLANKS else Material.BIRCH_PLANKS
            }
        }
    }

    fun clear() {
        for (i in 0 until 8 * 5) {
            for (j in 0 until 8 * 5) {
                world.getBlockAt(i, 100, j).type = Material.DARK_OAK_PLANKS
                if (i in 8 - 1..8 * 4 && j in 8 - 1..8 * 4) {
                    world.getBlockAt(i, 101, j).type = Material.DARK_OAK_PLANKS
                }
            }
        }
        for (i in 0 until 8 * 3) {
            for (j in 0 until 8 * 3) {
                val b = (i.div(3) + j.div(3)) % 2 == 1
                world.getBlockAt(i + 8, 101, j + 8).type = if (b) Material.SPRUCE_PLANKS else Material.BIRCH_PLANKS
                world.getBlockAt(i + 8, 102, j + 8).type = Material.AIR
            }
        }
        pieces.forEach { it.render() }
    }

    fun empty(pos: ChessPosition) = pos.isValid() && this[pos] == null
    fun sided(pos: ChessPosition, side: ChessSide) = pos.isValid() && this[pos]?.side == side
    fun sideOf(pos: ChessPosition) = this[pos]?.side

    fun remove(piece: ChessPiece) = pieces.remove(piece)
    private fun find(predicate: (ChessPiece) -> Boolean): ChessPiece? = pieces.find(predicate)
    operator fun plusAssign(piece: ChessPiece) {
        pieces += piece
    }

    operator fun get(pos: ChessPosition): ChessPiece? = find { it.pos == pos }
    operator fun get(c: Int, r: Int) = get(ChessPosition(c, r))
    operator fun get(l: Loc) = get(ChessPosition.fromLoc(l))
    operator fun get(side: ChessSide) = pieces.filter { it.side == side }

    fun allCheckMoves(pos: ChessPosition, by: ChessSide) = pieces.filter { it.side == by }.flatMap { it.getMoves() }.filter { it.target == pos }

    fun getAttackedPositions(by: ChessSide, without : List<ChessPosition> = emptyList()) =
            pieces
                    .filter { it.side == by }
                    .flatMap { it.moveScheme.genMoves(game, it.pos) }
                    .filter { it.scheme.canCapture }
                    .flatMap { if (it.target in without) it.cont + it.target else listOf(it.target) }

    fun allPinnedPieces(pos: ChessPosition, by: ChessSide) = pieces.filter { it.side == by }.flatMap { it.getMoves() }.filter { pos in it.cont }.map { this[it.target]!! to it }.toMap()

    fun getPositionHash(): Long {
        val board : Array<Array<ChessPiece?>> = Array(8){Array(8){null} }
        var possum = 0
        pieces.forEach { board[it.pos.file][it.pos.rank] = it; possum += it.pos.file*256 + it.pos.rank}
        val content = StringBuilder()
        board.forEach {l -> l.forEach { content.append("${it?.type}${it?.side}")}}
        return content.toString().hashCode().toLong()*65536+ possum
    }
}