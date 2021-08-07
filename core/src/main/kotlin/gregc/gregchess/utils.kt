package gregc.gregchess

import java.time.Duration

fun randomString(size: Int) =
    String(CharArray(size) { (('a'..'z') + ('A'..'Z') + ('0'..'9')).random() })

fun rotationsOf(x: Int, y: Int): List<Pair<Int, Int>> =
    listOf(x to y, x to -y, -x to y, -x to -y, y to x, -y to x, y to -x, -y to -x).distinct()

fun between(i: Int, j: Int): IntRange = if (i > j) (j + 1 until i) else (i + 1 until j)

fun Int.towards(other: Int, amount: Int) =
    if (this > other) this - amount else if (this < other) this + amount else this

operator fun <E> List<E>.component6(): E = this[5]

fun isValidUUID(s: String) =
    Regex("""^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}${'$'}""")
        .matches(s)

operator fun Pair<Int, Int>.rangeTo(other: Pair<Int, Int>) = (first..other.first).flatMap { i ->
    (second..other.second).map { j -> Pair(i, j) }
}

operator fun Pair<Int, Int>.times(m: Int) = Pair(m * first, m * second)

fun String.upperFirst() = replaceFirstChar { it.uppercase() }

val Int.seconds: Duration
    get() = Duration.ofSeconds(toLong())
val Long.minutes: Duration
    get() = Duration.ofMinutes(this)

fun String.snakeToPascal(): String {
    val snakeRegex = "_[a-zA-Z]".toRegex()
    return snakeRegex.replace(lowercase()) { it.value.replace("_", "").uppercase() }.upperFirst()
}

fun String.isValidName(): Boolean = all { it == '_' || it in ('A'..'Z')}
fun String.isValidId(): Boolean = all { it == '_' || it in ('a'..'z')}

data class Loc(val x: Int, val y: Int, val z: Int) {
    operator fun plus(offset: Loc) = Loc(x + offset.x, y + offset.y, z + offset.z)
}