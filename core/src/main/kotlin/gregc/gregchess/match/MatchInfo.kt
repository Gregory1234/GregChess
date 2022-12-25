package gregc.gregchess.match

interface MatchInfo {
    val pgnSite: String // TODO: replace with a method
    val pgnEventName: String
    val pgnRound: Int
    fun matchToString(): String
    fun matchCoroutineName(): String
}