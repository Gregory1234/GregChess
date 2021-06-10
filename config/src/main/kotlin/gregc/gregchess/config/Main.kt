package gregc.gregchess.config

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import gregc.gregchess.config.dsl.Represents
import gregc.gregchess.config.dsl.config
import org.bukkit.Material
import org.bukkit.Sound
import java.io.File
import java.time.Duration

@Represents("gregc.gregchess.chess.component.ChessClock", "Type")
@Suppress("unused")
enum class ChessClockType {
    FIXED, INCREMENT, BRONSTEIN, SIMPLE
}

@Represents("gregc.gregchess.chess", "PieceType")
@Suppress("unused")
enum class PieceType {
    KING, QUEEN, ROOK, BISHOP, KNIGHT, PAWN
}

@Represents("gregc.gregchess.chess", "Floor")
@Suppress("unused")
enum class Floor {
    LIGHT, DARK, MOVE, CAPTURE, SPECIAL, NOTHING, OTHER, LAST_START, LAST_END
}

@Represents("gregc.gregchess.chess", "PieceSound")
@Suppress("unused")
enum class PieceSound {
    PICK_UP, MOVE, CAPTURE
}

fun main(args: Array<String>) {
    val c = config {
        val string = type<String>("String") {
            toCode = { CodeBlock.of("\"$it\"") }
            toYaml = { it }
            defaultCode = CodeBlock.of("path")
            parser = CodeBlock.of("c::processString")
        }
        val bool = defaultType<Boolean>("Bool") {
            toCode = { CodeBlock.of("$it") }
            toYaml = { "$it" }
            default = true
            parser = CodeBlock.of("String::toBooleanStrictOrNull")
        }
        val duration = type<String>("Duration") {
            baseClass = Duration::class
            toCode = { CodeBlock.of(it) }
            toYaml = { it }
            defaultCode = CodeBlock.of("0.seconds")
            parser = CodeBlock.of("::parseDuration")
        }
        val char = defaultType<Char>("Char") {
            toCode = { CodeBlock.of("'$it'") }
            toYaml = { "'$it'" }
            default = ' '
            parser = CodeBlock.of("{ if (it.length == 1) it[0] else null }")
        }
        val int = defaultType<Int>("Int") {
            toCode = { CodeBlock.of("$it") }
            toYaml = { "$it" }
            default = 0
            parser = CodeBlock.of("String::toIntOrNull")
        }
        val material = enumType("Material", Material.AIR)
        val sound = enumType("Sound", Sound.BLOCK_STONE_STEP)
        val clockType = enumType("ChessClockType", ChessClockType.INCREMENT)
        root("Config") {
            string.list("ChessArenas")
            block("Request") {
                string("Accept", "&a[Accept]")
                string("Cancel", "&c[Cancel]")
                bool("SelfAccept", true)
                val requestType = inlineFiniteBlockList("RequestType") {
                    block("Sent") {
                        string("Request")
                        formatString1<String>("Cancel")
                        formatString1<String>("Accept")
                    }
                    block("Received") {
                        formatString2<String, String>("Request")
                        formatString1<String>("Cancel")
                        formatString1<String>("Accept")
                    }
                    block("Error") {
                        string("NotFound")
                        string("CannotSend")
                    }
                    formatString1<String>("Expired")
                    duration.optional("Duration")
                }
                requestType.addInstance("Duel") {
                    block("Sent") {
                        string("Request", "&eDuel request sent.")
                        string("Cancel", "&eYou cancelled the duel with $1.")
                        string("Accept", "&aYou accepted the duel request.")
                    }
                    block("Received") {
                        string("Request", "&e$1 challenges you to a chess game: $2.")
                        string("Cancel", "&eDuel with $1 has been cancelled.")
                        string("Accept", "&aYour duel request has been accepted.")
                    }
                    block("Error") {
                        string("NotFound", "&cDuel request not found.")
                    }
                    string("Expired", "&eDuel request with $1 has expired.")
                    duration("Duration", "5m")
                }
                requestType.addInstance("Draw") {
                    block("Sent") {
                        string("Request", "&eDraw offer sent.")
                        string("Cancel", "&eYou cancelled the draw offer.")
                        string("Accept", "&aYou accepted the draw offer.")
                    }
                    block("Received") {
                        string("Request", "&eYour opponent wants a draw.")
                        string("Cancel", "&eThe draw offer has been cancelled.")
                        string("Accept", "&aThe draw offer has been accepted.")
                    }
                    block("Error") {
                        string("CannotSend", "&cIt is your opponent's turn!")
                    }
                }
                requestType.addInstance("Takeback") {
                    block("Sent") {
                        string("Request", "&eTakeback request sent.")
                        string("Cancel", "&eYou cancelled the takeback request.")
                        string("Accept", "&aYou accepted the takeback request.")
                    }
                    block("Received") {
                        string("Request", "&eYour opponent wants a takeback.")
                        string("Cancel", "&eThe takeback request has been cancelled.")
                        string("Accept", "&aThe takeback request has been accepted.")
                    }
                    block("Error") {
                        string("CannotSend", "&cIt is your turn!")
                    }
                }
            }
            block("Message") {
                string("Teleported", "&eTeleported to $1.")
                string("Teleporting", "&eTeleporting...")
                string("CopyFEN", "Copy FEN")
                string("CopyPGN", "Copy PGN")
                string("LoadedFEN", "&eLoaded FEN!")
                string("ChooseSettings", "Choose settings")
                string("PawnPromotion", "Pawn promotion")
                string("InCheck", "&cYou are in check!")
                string("ConfigReloaded", "&ePlugin config reloaded!")
                string("LevelSet", "&eDebug level set successfully!")
                string("EngineCommandSent", "&eEngine command sent!")
                string("TimeOpDone", "&eCompleted the chess clock operation!")
                string("SkippedTurn", "&eSkipped a turn!")
                string("BoardOpDone", "&eCompleted the chessboard operation!")
                block("YouArePlayingAs") {
                    string("White", "&eYou are playing with the white pieces")
                    string("Black", "&eYou are playing with the black pieces")
                    bySides("White", "Black")
                }
                block("GameFinished") {
                    formatString1<String>("WhiteWon", "The game has finished! White won by $1.")
                    formatString1<String>("BlackWon", "The game has finished! Black won by $1.")
                    formatString1<String>("ItWasADraw", "The game has finished! It was a draw by $1.")
                    byOptSides("WhiteWon", "BlackWon", "ItWasADraw")
                }
            }
            block("Title") {
                string("YourTurn", "&eIt is your turn")
                string("InCheck", "&cYou are in check!")
                block("YouArePlayingAs") {
                    string("White", "&eYou are playing with the white pieces")
                    string("Black", "&eYou are playing with the black pieces")
                    bySides("White", "Black")
                }
                string("YouArePlayingAgainst", "&eYou are playing against &c$1")
                block("Player") {
                    string("YouWon", "&aYou won")
                    string("YouLost", "&cYou lost")
                    string("YouDrew", "&eYou drew")
                }
                block("Spectator") {
                    string("WhiteWon", "&eWhite won")
                    string("BlackWon", "&eBlack won")
                    string("ItWasADraw", "&eDraw")
                    byOptSides("WhiteWon", "BlackWon", "ItWasADraw")
                }
            }
            block("Chess") {
                string("Capture", "x")
                duration("SimulDelay", "1s")
                block("Side") {
                    val sideData = inlineFiniteBlockList("SideData") {
                        string("Name")
                        char("Char")
                        formatString1<String>("Piece")
                    }
                    sideData.addInstance("White") {
                        string("Name", "White")
                        char("Char", 'w')
                        string("Piece", "&bWhite $1")
                    }
                    sideData.addInstance("Black") {
                        string("Name", "Black")
                        char("Char", 'b')
                        string("Piece", "&bBlack $1")
                    }
                    bySides("White", "Black")
                }
                block("Floor") {
                    material("Light", Material.BIRCH_PLANKS)
                    material("Dark", Material.SPRUCE_PLANKS)
                    material("Move", Material.GREEN_CONCRETE)
                    material("Capture", Material.RED_CONCRETE)
                    material("Special", Material.BLUE_CONCRETE)
                    material("Nothing", Material.YELLOW_CONCRETE)
                    material("Other", Material.PURPLE_CONCRETE)
                    material("LastStart", Material.BROWN_CONCRETE)
                    material("LastEnd", Material.ORANGE_CONCRETE)
                    byEnum<Floor>()
                }
                block("Piece") {
                    val pieceData = inlineFiniteBlockList("PieceData") {
                        string("Name")
                        char("Char")
                        block("Item") {
                            material("White")
                            material("Black")
                            bySides("White", "Black")
                        }
                        block("Structure") {
                            material.list("White")
                            material.list("Black")
                            bySides("White", "Black")
                        }
                        block("Sound") {
                            sound("PickUp")
                            sound("Move")
                            sound("Capture")
                            byEnum<PieceSound>()
                        }
                    }
                    pieceData.addInstance("King") {
                        string("Name", "King")
                        char("Char", 'k')
                        block("Item") {
                            material("White", Material.WHITE_CONCRETE)
                            material("Black", Material.BLACK_CONCRETE)
                        }
                        block("Structure") {
                            material.list("White", listOf(Material.WHITE_CONCRETE))
                            material.list("Black", listOf(Material.BLACK_CONCRETE))
                        }
                        block("Sound") {
                            sound("PickUp", Sound.BLOCK_METAL_HIT)
                            sound("Move", Sound.BLOCK_METAL_STEP)
                            sound("Capture", Sound.ENTITY_ENDER_DRAGON_DEATH)
                        }
                    }
                    pieceData.addInstance("Queen") {
                        string("Name", "Queen")
                        char("Char", 'q')
                        block("Item") {
                            material("White", Material.DIAMOND_BLOCK)
                            material("Black", Material.NETHERITE_BLOCK)
                        }
                        block("Structure") {
                            material.list("White", listOf(Material.DIAMOND_BLOCK))
                            material.list("Black", listOf(Material.NETHERITE_BLOCK))
                        }
                        block("Sound") {
                            sound("PickUp", Sound.ENTITY_WITCH_CELEBRATE)
                            sound("Move", Sound.BLOCK_GLASS_STEP)
                            sound("Capture", Sound.ENTITY_WITCH_DEATH)
                        }
                    }
                    pieceData.addInstance("Rook") {
                        string("Name", "Rook")
                        char("Char", 'r')
                        block("Item") {
                            material("White", Material.IRON_BLOCK)
                            material("Black", Material.GOLD_BLOCK)
                        }
                        block("Structure") {
                            material.list("White", listOf(Material.IRON_BLOCK))
                            material.list("Black", listOf(Material.GOLD_BLOCK))
                        }
                        block("Sound") {
                            sound("PickUp", Sound.ENTITY_IRON_GOLEM_STEP)
                            sound("Move", Sound.ENTITY_IRON_GOLEM_STEP)
                            sound("Capture", Sound.ENTITY_IRON_GOLEM_DEATH)
                        }
                    }
                    pieceData.addInstance("Bishop") {
                        string("Name", "Bishop")
                        char("Char", 'b')
                        block("Item") {
                            material("White", Material.POLISHED_DIORITE)
                            material("Black", Material.POLISHED_BLACKSTONE)
                        }
                        block("Structure") {
                            material.list("White", listOf(Material.POLISHED_DIORITE))
                            material.list("Black", listOf(Material.POLISHED_BLACKSTONE))
                        }
                        block("Sound") {
                            sound("PickUp", Sound.ENTITY_SPIDER_AMBIENT)
                            sound("Move", Sound.ENTITY_SPIDER_STEP)
                            sound("Capture", Sound.ENTITY_SPIDER_DEATH)
                        }
                    }
                    pieceData.addInstance("Knight") {
                        string("Name", "Knight")
                        char("Char", 'n')
                        block("Item") {
                            material("White", Material.END_STONE)
                            material("Black", Material.BLACKSTONE)
                        }
                        block("Structure") {
                            material.list("White", listOf(Material.END_STONE))
                            material.list("Black", listOf(Material.BLACKSTONE))
                        }
                        block("Sound") {
                            sound("PickUp", Sound.ENTITY_HORSE_JUMP)
                            sound("Move", Sound.ENTITY_HORSE_STEP)
                            sound("Capture", Sound.ENTITY_HORSE_DEATH)
                        }
                    }
                    pieceData.addInstance("Pawn") {
                        string("Name", "Pawn")
                        char("Char", 'p')
                        block("Item") {
                            material("White", Material.WHITE_CARPET)
                            material("Black", Material.BLACK_CARPET)
                        }
                        block("Structure") {
                            material.list("White", listOf(Material.WHITE_CARPET))
                            material.list("Black", listOf(Material.BLACK_CARPET))
                        }
                        block("Sound") {
                            sound("PickUp", Sound.BLOCK_STONE_HIT)
                            sound("Move", Sound.BLOCK_STONE_STEP)
                            sound("Capture", Sound.BLOCK_STONE_BREAK)
                        }
                    }
                    byEnum<PieceType>()
                }
                block("EndReason") {
                    string("Checkmate", "checkmate")
                    string("Resignation", "resignation")
                    string("Walkover", "walkover")
                    string("PluginRestart", "the plugin restarting")
                    string("ArenaRemoved", "the arena getting removed")
                    string("Stalemate", "stalemate")
                    string("InsufficientMaterial", "insufficient material")
                    string("FiftyMoves", "the 50-move rule")
                    string("Repetition", "repetition")
                    string("DrawAgreement", "agreement")
                    string("Timeout", "timeout")
                    string("DrawTimeout", "timeout vs insufficient material")
                    string("Error", "error")
                    string("ThreeChecks", "three checks")
                    string("KingOfTheHill", "king of the hill")
                    string("Atomic", "explosion")
                    string("PiecesLost", "all pieces lost")
                }
            }
            block("Component") {
                block("Scoreboard") {
                    string("Title", "GregChess game")
                    string("Preset", "Preset")
                    string("Player", "player")
                    string("PlayerPrefix", "&b")
                    block("Format") {
                        formatString1<String>("General", "$1: ")
                        formatString1<String>("White", "White $1: ")
                        formatString1<String>("Black", "Black $1: ")
                        byOptSides("White", "Black", "General")
                    }
                }
                block("Clock") {
                    string("TimeRemainingSimple", "Time remaining")
                    string("TimeRemaining", "time")
                    special("TimeFormat", ClassName("gregc.gregchess", "ConfigTimeFormat"),"mm:ss.S")
                }
                block("CheckCounter") {
                    string("CheckCounter", "check counter")
                }
                block("Simul") {
                    string("Current", "current game")
                }
            }
            block("Settings") {
                blockList("Presets") {
                    string.optional("Variant")
                    string.optional("Board")
                    string.optional("Clock")
                    bool("SimpleCastling", false)
                    int("TileSize", 3)
                }
                blockList("Clock") {
                    clockType("Type", warnMissing = false)
                    duration("Initial", null)
                    duration("Increment", "0s", true)
                }
            }
        }
        root("ErrorMsg", "Message.Error") {
            string("NoPerms", "&cYou do not have the permission to do that!")
            string("NotPlayer", "&cYou are not a player!")
            string("WrongArgumentsNumber", "&cWrong number of arguments!")
            string("WrongArgument", "&cWrong argument!")
            string("NoPermission", "&cYou do not have permission to do this!")
            string("PlayerNotFound", "&cPlayer doesn't exist!")
            string("NoArenas", "&cThere are no free arenas!")
            string("GameNotFound", "&cGame not found!")
            string("EngineNotFound", "&cThere are no engines in this game!")
            string("BoardNotFound", "&cYour game has no chessboard!")
            string("ClockNotFound", "&cYour game has no clock!")
            string("RendererNotFound", "&cYour game has no compatible renderer!")
            string("PieceNotFound", "&cPiece was not found!")
            string("NothingToTakeback", "&cThere are no moves to takeback!")
            string("WrongDurationFormat", "&cWrong duration format!")
            string("TeleportFailed", "&cTeleport failed!")
            block("NotHuman") {
                string("Opponent", "&cYour opponent is not a human!")
            }
            block("InGame") {
                string("You", "&cYou are already in a game!")
                string("Opponent", "&cYour opponent is in a game already!")
            }
            block("NotInGame") {
                string("You", "&cYou are not in a game!")
                string("Player", "&cThe player isn't in a game!")
            }
            block("Simul") {
                string("Clock", "&cClocks are not supported in simuls.")
            }
        }
    }
    c.buildKotlin().writeTo(File(args[0]))
    File(args[0] + "/config.yml").writeText(c.buildYaml().build())
}