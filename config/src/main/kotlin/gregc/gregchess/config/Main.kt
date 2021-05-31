package gregc.gregchess.config

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import java.time.Duration

const val PACKAGE_NAME = "gregc.gregchess"

val viewClass = ClassName("gregc.gregchess", "View")
val sideType = ClassName("gregc.gregchess.chess", "Side")

@DslMarker
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class ConfigDSL

@ConfigDSL
class BlockScope(val config: TypeSpec.Builder) {
    fun addBlock(name: String, block: BlockScope.() -> Unit) {
        val b = BlockScope(TypeSpec.classBuilder(name)
            .superclass(viewClass)
            .primaryConstructor(FunSpec.constructorBuilder().addParameter("path", String::class).build())
            .addSuperclassConstructorParameter("path"))
        b.block()
        config.addType(b.config.build())
        config.addProperty(PropertySpec
            .builder(name.replaceFirstChar { it.lowercaseChar() }, ClassName("", name))
            .getter(FunSpec.getterBuilder().addCode("return $name(childPath(\"$name\"))").build()).build())
    }
    fun addString(name: String, default: String? = null) {
        config.addProperty(PropertySpec
            .builder(name.replaceFirstChar { it.lowercaseChar() }, String::class)
            .getter(FunSpec.getterBuilder().addCode("return getString(\"$name\")").build()).build())
    }
    fun addStringList(name: String) {
        config.addProperty(PropertySpec
            .builder(name.replaceFirstChar { it.lowercaseChar() }, List::class.asClassName().parameterizedBy(String::class.asClassName()))
            .getter(FunSpec.getterBuilder().addCode("return getStringList(\"$name\")").build())
            .build())
    }
    fun addBool(name: String, default: Boolean, warnMissing: Boolean = false) {
        config.addProperty(PropertySpec
            .builder(name.replaceFirstChar { it.lowercaseChar() }, Boolean::class)
            .getter(FunSpec.getterBuilder().addCode("return getBool(\"$name\", $default, $warnMissing)").build())
            .build())
    }
    fun addChar(name: String, default: Char? = null) {
        config.addProperty(PropertySpec
            .builder(name.replaceFirstChar { it.lowercaseChar() }, Char::class)
            .getter(FunSpec.getterBuilder().addCode("return getChar(\"$name\")").build())
            .build())
    }
    fun addEnum(name: String, typ: TypeName, default: String, warnMissing: Boolean = true) {
        config.addProperty(PropertySpec
            .builder(name.replaceFirstChar { it.lowercaseChar() }, typ)
            .getter(FunSpec.getterBuilder().addCode("return getEnum<%T>(\"$name\", %T.$default, $warnMissing)", typ, typ).build())
            .build())
    }
    fun addEnumList(name: String, typ: TypeName, warnMissing: Boolean = true) {
        config.addProperty(PropertySpec
            .builder(name.replaceFirstChar { it.lowercaseChar() }, List::class.asClassName().parameterizedBy(typ))
            .getter(FunSpec.getterBuilder().addCode("return getEnumList<%T>(\"$name\", $warnMissing)", typ).build())
            .build())
    }
    fun addDuration(name: String, default: String, warnMissing: Boolean = false) {
        config.addProperty(PropertySpec
            .builder(name.replaceFirstChar { it.lowercaseChar() }, Duration::class)
            .getter(FunSpec.getterBuilder().addCode("return getDuration(\"$name\", $warnMissing)").build())
            .build())
    }
    fun addOptionalDuration(name: String) {
        config.addProperty(PropertySpec
            .builder(name.replaceFirstChar { it.lowercaseChar() }, Duration::class.asClassName().copy(nullable = true))
            .getter(FunSpec.getterBuilder().addCode("return getOptionalDuration(\"$name\")").build())
            .build())
    }
    fun inlineBlockList(name: String, block: BlockScope.() -> Unit){
        val b = BlockScope(TypeSpec.classBuilder(name)
            .superclass(viewClass)
            .primaryConstructor(FunSpec.constructorBuilder().addParameter("path", String::class).build())
            .addSuperclassConstructorParameter("path"))
        b.block()
        config.addType(b.config.build())
        config.addProperty(PropertySpec
            .builder(name.replaceFirstChar { it.lowercaseChar() }+"s",
                Map::class.asClassName().parameterizedBy(String::class.asClassName(), ClassName("", name)))
            .getter(FunSpec.getterBuilder().addCode("return children.mapValues{ $name(childPath(it.key)) }").build()).build())
    }
    fun inlineFiniteBlockList(name: String, vararg values: String, block: BlockScope.() -> Unit){
        val b = BlockScope(TypeSpec.classBuilder(name)
            .superclass(viewClass)
            .primaryConstructor(FunSpec.constructorBuilder().addParameter("path", String::class).build())
            .addSuperclassConstructorParameter("path"))
        b.block()
        config.addType(b.config.build())
        values.forEach { nm ->
            config.addProperty(PropertySpec
                .builder(nm.replaceFirstChar { it.lowercaseChar() }, ClassName("", name))
                .getter(FunSpec.getterBuilder().addCode("return $name(childPath(\"$nm\"))").build()).build())
        }
    }
    fun bySides(white: String, black: String) {
        val wt = config.propertySpecs.first {it.name == white.replaceFirstChar { it.lowercaseChar() }}.type
        val bt = config.propertySpecs.first {it.name == black.replaceFirstChar { it.lowercaseChar() }}.type
        require(wt == bt)
        config.addFunction(FunSpec.builder("get")
            .addModifiers(KModifier.OPERATOR)
            .returns(wt)
            .addParameter("side", sideType)
            .addCode("""
                return when (side) { 
                    %T.WHITE -> ${white.replaceFirstChar { it.lowercaseChar() }}
                    %T.BLACK -> ${black.replaceFirstChar { it.lowercaseChar() }}
                }
            """.trimIndent(), sideType, sideType)
            .build())
    }
    fun byOptSides(white: String, black: String, nulls: String) {
        val wt = config.propertySpecs.first {it.name == white.replaceFirstChar { it.lowercaseChar() }}.type
        val bt = config.propertySpecs.first {it.name == black.replaceFirstChar { it.lowercaseChar() }}.type
        val nt = config.propertySpecs.first {it.name == nulls.replaceFirstChar { it.lowercaseChar() }}.type
        require(wt == bt)
        require(wt == nt)
        config.addFunction(FunSpec.builder("get")
            .addModifiers(KModifier.OPERATOR)
            .returns(wt)
            .addParameter("side", ClassName("gregc.gregchess.chess", "Side").copy(nullable = true))
            .addCode("""
                return when (side) { 
                    %T.WHITE -> ${white.replaceFirstChar { it.lowercaseChar() }}
                    %T.BLACK -> ${black.replaceFirstChar { it.lowercaseChar() }}
                    null -> ${nulls.replaceFirstChar { it.lowercaseChar() }}
                }
            """.trimIndent(), sideType, sideType)
            .build())
    }
}

fun config(block: BlockScope.() -> Unit) {
    val c = BlockScope(TypeSpec.objectBuilder("Config")
        .superclass(viewClass)
        .addSuperclassConstructorParameter("\"\""))
    c.block()
    FileSpec.builder(PACKAGE_NAME, "Config")
        .addType(c.config.build())
        .build()
        .writeTo(System.out)
}

fun main() {
    config {
        addStringList("ChessArenas")
        addBlock("Request") {
            addString("Accept", "&a[Accept]")
            addString("Cancel", "&c[Cancel]")
            addBool("SelfAccept", true)
            inlineBlockList("RequestType") {
                addBlock("Sent") {
                    addString("Request")
                    addString("Cancel")
                    addString("Accept")
                }
                addBlock("Received") {
                    addString("Request")
                    addString("Cancel")
                    addString("Accept")
                }
                addBlock("Error") {
                    addString("NotFound")
                    addString("CannotSend")
                }
                addString("Expired")
                addOptionalDuration("Duration")
            }
        }
        addBlock("Message") {
            addString("Teleported", "&eTeleported to $1.")
            addString("Teleporting", "&eTeleporting...")
            addString("CopyFEN", "Copy FEN")
            addString("CopyPGN", "Copy PGN")
            addString("LoadedFEN", "&eLoaded FEN!")
            addString("ChooseSettings", "Choose settings")
            addString("PawnPromotion", "Pawn promotion")
            addString("InCheck", "&cYou are in check!")
            addString("ConfigReloaded", "&ePlugin config reloaded!")
            addString("LevelSet", "&eDebug level set successfully!")
            addString("EngineCommandSent", "&eEngine command sent!")
            addString("TimeOpDone", "&eCompleted the chess clock operation!")
            addString("SkippedTurn", "&eSkipped a turn!")
            addString("BoardOpDone", "&eCompleted the chessboard operation!")
            addBlock("YouArePlayingAs") {
                addString("White", "&eYou are playing with the white pieces")
                addString("Black", "&eYou are playing with the black pieces")
                bySides("White", "Black")
            }
            addBlock("GameFinished") {
                addString("WhiteWon", "The game has finished! White won by $1.")
                addString("BlackWon", "The game has finished! Black won by $1.")
                addString("ItWasADraw", "The game has finished! It was a draw by $1.")
                byOptSides("WhiteWon", "BlackWon", "ItWasADraw")
            }
            addBlock("Error") {
                addString("NoPerms", "&cYou do not have the permission to do that!")
                addString("NotPlayer", "&cYou are not a player!")
                addString("WrongArgumentsNumber", "&cWrong number of arguments!")
                addString("WrongArgument", "&cWrong argument!")
                addString("NoPermission", "&cYou do not have permission to do this!")
                addString("PlayerNotFound", "&cPlayer doesn't exist!")
                addString("NoArenas", "&cThere are no free arenas!")
                addString("GameNotFound", "&cGame not found!")
                addString("EngineNotFound", "&cThere are no engines in this game!")
                addString("BoardNotFound", "&cYour game has no chessboard!")
                addString("ClockNotFound", "&cYour game has no clock!")
                addString("RendererNotFound", "&cYour game has no compatible renderer!")
                addString("NothingToTakeback", "&cThere are no moves to takeback!")
                addString("WrongDurationFormat", "&cWrong duration format!")
                addString("TeleportFailed", "&cTeleport failed!")
                addBlock("NotHuman") {
                    addString("Opponent", "&cYour opponent is not a human!")
                }
                addBlock("InGame") {
                    addString("You", "&cYou are already in a game!")
                    addString("Opponent", "&cYour opponent is in a game already!")
                }
                addBlock("NotInGame") {
                    addString("You", "&cYou are not in a game!")
                    addString("Player", "&cThe player isn't in a game!")
                }
                addBlock("Simul") {
                    addString("Clock", "&cClocks are not supported in simuls.")
                }
            }
        }
        addBlock("Title") {
            addString("YourTurn", "&eIt is your turn")
            addString("InCheck", "&cYou are in check!")
            addBlock("YouArePlayingAs") {
                addString("White", "&eYou are playing with the white pieces")
                addString("Black", "&eYou are playing with the black pieces")
                bySides("White", "Black")
            }
            addString("YouArePlayingAgainst", "&eYou are playing against &c$1")
            addBlock("Player") {
                addString("YouWon", "&aYou won")
                addString("YouLost", "&cYou lost")
                addString("YouDrew", "&eYou drew")
            }
            addBlock("Spectator") {
                addString("WhiteWon", "&eWhite won")
                addString("BlackWon", "&eBlack won")
                addString("ItWasADraw", "&eDraw")
                byOptSides("WhiteWon", "BlackWon", "ItWasADraw")
            }
        }
        addBlock("Chess") {
            addString("Capture", "x")
            addDuration("SimulDelay", "1s")
            addBlock("Side") {
                inlineFiniteBlockList("SideData", "White", "Black") {
                    addString("Name")
                    addChar("Char")
                    addString("Piece")
                }
                bySides("White", "Black")
            }
            val mat: TypeName = ClassName("org.bukkit", "Material")
            val sound: TypeName = ClassName("org.bukkit", "Sound")
            addBlock("Floor") {
                addEnum("Light", mat, "BIRCH_PLANKS")
                addEnum("Dark", mat, "SPRUCE_PLANKS")
                addEnum("Move", mat, "GREEN_CONCRETE")
                addEnum("Capture", mat, "RED_CONCRETE")
                addEnum("Special", mat, "BLUE_CONCRETE")
                addEnum("Nothing", mat, "YELLOW_CONCRETE")
                addEnum("Other", mat, "PURPLE_CONCRETE")
                addEnum("LastStart", mat, "BROWN_CONCRETE")
                addEnum("LastEnd", mat, "ORANGE_CONCRETE")
                // TODO: enum `get` gen
            }
            addBlock("Piece") {
                inlineFiniteBlockList("PieceData", "King", "Queen", "Rook", "Bishop", "Knight", "Pawn") {
                    addString("Name")
                    addChar("Char")
                    addBlock("Item") {
                        addEnum("White", mat, "AIR")
                        addEnum("Black", mat, "AIR")
                        bySides("White", "Black")
                    }
                    addBlock("Structure") {
                        addEnumList("White", mat)
                        addEnumList("Black", mat)
                        bySides("White", "Black")
                    }
                    addBlock("Sound") {
                        addEnum("PickUp", sound, "BLOCK_STONE_HIT")
                        addEnum("Move", sound, "BLOCK_STONE_HIT")
                        addEnum("Capture", sound, "BLOCK_STONE_HIT")
                    }
                }
            }
            addBlock("EndReason") {
                addString("Checkmate", "checkmate")
                addString("Resignation", "resignation")
                addString("Walkover", "walkover")
                addString("PluginRestart", "the plugin restarting")
                addString("ArenaRemoved", "the arena getting removed")
                addString("Stalemate", "stalemate")
                addString("InsufficientMaterial", "insufficient material")
                addString("FiftyMoves", "the 50-move rule")
                addString("Repetition", "repetition")
                addString("DrawAgreement", "agreement")
                addString("Timeout", "timeout")
                addString("DrawTimeout", "timeout vs insufficient material")
                addString("Error", "error")
                addString("ThreeChecks", "three checks")
                addString("KingOfTheHill", "king of the hill")
                addString("Atomic", "explosion")
                addString("PiecesLost", "all pieces lost")
            }
        }
        addBlock("Component") {
            addBlock("Scoreboard") {
                addString("Title", "GregChess game")
                addString("Preset", "Preset")
                addString("Player", "player")
                addString("PlayerPrefix", "&b")
                addString("GeneralFormat", "$1: ")
                addString("WhiteFormat", "White $1: ")
                addString("BlackFormat", "Black $1: ")
            }
            addBlock("Clock") {
                addString("TimeRemainingSimple", "Time remaining")
                addString("TimeRemaining", "time")
                addString("TimeFormat", "mm:ss.S")
            }
            addBlock("CheckCounter") {
                addString("CheckCounter", "check counter")
            }
            addBlock("Simul") {
                addString("Current", "current game")
            }
        }
    }
}