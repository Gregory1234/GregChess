package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.registry.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder


typealias Dir = Pair<Int, Int>

@Serializable(with = Pos.Serializer::class)
data class Pos(val file: Int, val rank: Int) {
    object Serializer : KSerializer<Pos> {
        override val descriptor: SerialDescriptor get() = PrimitiveSerialDescriptor("Pos", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: Pos) {
            encoder.encodeString(value.toString())
        }

        override fun deserialize(decoder: Decoder): Pos = parseFromString(decoder.decodeString())
    }

    override fun toString() = "$fileStr$rankStr"
    operator fun plus(diff: Dir) = plus(diff.first, diff.second)
    fun plus(df: Int, dr: Int) = Pos(file + df, rank + dr)
    val fileStr get() = "${'a' + file}"
    val rankStr get() = (rank + 1).toString()

    fun neighbours(): List<Pos> = (Pair(-1, -1)..Pair(1, 1)).map(::plus).filter { it.isValid() } - this

    fun isValid() = file in (0..7) && rank in (0..7)

    companion object {
        fun parseFromString(s: String): Pos {
            require(s.length == 2 && s[0] in 'a'..'h' && s[1] in '1'..'8') { "Bad chessboard coordinate: $s" }
            return Pos(s[0].lowercaseChar() - 'a', s[1] - '1')
        }
    }
}

class PosSteps(val start: Pos, private val jump: Dir, override val size: Int) : Set<Pos> {
    class PosIterator(val start: Pos, private val jump: Dir, private var remaining: Int) : Iterator<Pos> {
        private var value = start

        override fun hasNext() = remaining > 0

        override fun next(): Pos {
            if (remaining <= 0)
                throw NoSuchElementException()
            val ret = value
            value += jump
            remaining--
            return ret
        }
    }

    companion object {
        private fun calcSize(start: Pos, jump: Dir): Int {
            val ret = mutableListOf<Int>()

            if (jump.first > 0)
                ret += (8 - start.file).floorDiv(jump.first)
            else if (jump.first < 0)
                ret += (start.file + 1).floorDiv(-jump.first)

            if (jump.second > 0)
                ret += (8 - start.rank).floorDiv(jump.second)
            else if (jump.second < 0)
                ret += (start.rank + 1).floorDiv(-jump.second)

            return ret.minOrNull() ?: 1
        }
    }

    constructor(start: Pos, jump: Dir) : this(start, jump, calcSize(start, jump))

    init {
        require(size <= calcSize(start, jump)) { "PosSteps size too large" }
    }

    override fun contains(element: Pos): Boolean {
        if (jump.first == 0) {
            if (element.file != start.file)
                return false
            if (jump.second == 0)
                return element == start
            val m = (element.rank - start.rank).mod(jump.second)
            if (m != 0)
                return false
            val d = (element.rank - start.rank).floorDiv(jump.second)
            return d in (0 until size)
        } else {
            val m = (element.file - start.file).mod(jump.first)
            if (m != 0)
                return false
            val d = (element.file - start.file).floorDiv(jump.first)
            if (d !in (0 until size))
                return false
            return (element.rank - start.rank) == d * jump.second
        }
    }

    override fun containsAll(elements: Collection<Pos>) = elements.all { it in this }

    override fun isEmpty() = size <= 0

    override fun iterator() = PosIterator(start, jump, size)
}

@Serializable(with = UniquenessCoordinate.Serializer::class)
data class UniquenessCoordinate(val file: Int? = null, val rank: Int? = null) {
    constructor(pos: Pos) : this(pos.file, pos.rank)

    object Serializer : KSerializer<UniquenessCoordinate> {
        override val descriptor: SerialDescriptor
            get() = PrimitiveSerialDescriptor("UniquenessCoordinate", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: UniquenessCoordinate) {
            encoder.encodeString(value.toString())
        }

        override fun deserialize(decoder: Decoder): UniquenessCoordinate {
            val str = decoder.decodeString()
            return if (str.length == 2)
                UniquenessCoordinate(Pos.parseFromString(str))
            else when (str.single()) {
                in '1'..'8' -> UniquenessCoordinate(rank = str.single() - '1')
                in 'a'..'h' -> UniquenessCoordinate(file = str.single() - 'a')
                else -> error("Bad chessboard coordinate: $str")
            }
        }
    }

    val fileStr get() = file?.let { "${'a' + it}" }
    val rankStr get() = rank?.let { (it + 1).toString() }
    override fun toString(): String = fileStr.orEmpty() + rankStr.orEmpty()
}

@Serializable(with = ChessFlag.Serializer::class)
class ChessFlag(@JvmField val isActive: (UInt) -> Boolean) : NameRegistered {
    object Serializer : NameRegisteredSerializer<ChessFlag>("ChessFlag", RegistryType.FLAG)

    companion object {
        @JvmField
        val EN_PASSANT = GregChessModule.register("en_passant", ChessFlag { it == 1u })
    }

    override val key get() = RegistryType.FLAG[this]

    override fun toString(): String = "$key@${hashCode().toString(16)}"
}

typealias ChessFlagReference = Map.Entry<ChessFlag, List<UInt>>
val ChessFlagReference.flagType get() = key
val ChessFlagReference.flagAges get() = value
val ChessFlagReference.flagAge get() = value.lastOrNull()
val ChessFlagReference.flagActive get() = flagAge?.let(flagType.isActive) ?: false
