package gregc.gregchess.config

import com.squareup.kotlinpoet.*
import gregc.gregchess.config.dsl.config
import java.io.File
import java.time.Duration

fun main(args: Array<String>) {
    val c = config {
        val string = type("String", String::class.asTypeName(), "path", "c::processString")
        val bool = type("Bool", Boolean::class.asTypeName(), "true", "String::toBooleanStrictOrNull", true)
        val duration = type("Duration", Duration::class.asTypeName(), "0.seconds", "::parseDuration")
        val char = type("Char", Char::class.asTypeName(), "' '", "{ if (it.length == 1) it[0] else null }", true)
        val int = type("Int", Int::class.asTypeName(), "0", "String::toIntOrNull", true)
        root("Config") {
            string.list("ChessArenas")
            block("Request") {
                string("Accept", "&a[Accept]")
                string("Cancel", "&c[Cancel]")
                bool("SelfAccept", "true")
                val requestType = inlineFiniteBlockList("RequestType") {
                    block("Sent") {
                        string("Request")
                        formatString("Cancel", String::class)
                        formatString("Accept", String::class)
                    }
                    block("Received") {
                        formatString("Request", String::class, String::class)
                        formatString("Cancel", String::class)
                        formatString("Accept", String::class)
                    }
                    block("Error") {
                        string("NotFound")
                        string("CannotSend")
                    }
                    formatString("Expired", String::class)
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
                    formatString("WhiteWon", String::class, default = "The game has finished! White won by $1.")
                    formatString("BlackWon", String::class, default = "The game has finished! Black won by $1.")
                    formatString("ItWasADraw", String::class, default = "The game has finished! It was a draw by $1.")
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
                        formatString("Piece", String::class)
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
                val mat: TypeName = ClassName("org.bukkit", "Material")
                val sound: TypeName = ClassName("org.bukkit", "Sound")
                block("Floor") {
                    enumString("Light", mat, "BIRCH_PLANKS")
                    enumString("Dark", mat, "SPRUCE_PLANKS")
                    enumString("Move", mat, "GREEN_CONCRETE")
                    enumString("Capture", mat, "RED_CONCRETE")
                    enumString("Special", mat, "BLUE_CONCRETE")
                    enumString("Nothing", mat, "YELLOW_CONCRETE")
                    enumString("Other", mat, "PURPLE_CONCRETE")
                    enumString("LastStart", mat, "BROWN_CONCRETE")
                    enumString("LastEnd", mat, "ORANGE_CONCRETE")
                    byEnum(ClassName("gregc.gregchess.chess", "Floor"),
                        "Light", "Dark", "Move", "Capture", "Special", "Nothing", "Other", "LastStart", "LastEnd")
                }
                block("Piece") {
                    val pieceData = inlineFiniteBlockList("PieceData") {
                        string("Name")
                        char("Char")
                        block("Item") {
                            enumString("White", mat, "AIR")
                            enumString("Black", mat, "AIR")
                            bySides("White", "Black")
                        }
                        block("Structure") {
                            enumStringList("White", mat)
                            enumStringList("Black", mat)
                            bySides("White", "Black")
                        }
                        block("Sound") {
                            enumString("PickUp", sound, "BLOCK_STONE_HIT")
                            enumString("Move", sound, "BLOCK_STONE_HIT")
                            enumString("Capture", sound, "BLOCK_STONE_HIT")
                            byEnum(ClassName("gregc.gregchess.chess", "PieceSound"), "PickUp", "Move", "Capture")
                        }
                    }
                    pieceData.addInstance("King") {
                        string("Name", "King")
                        char("Char", 'k')
                        block("Item") {
                            string("White", "WHITE_CONCRETE")
                            string("Black", "BLACK_CONCRETE")
                        }
                        block("Structure") {
                            string.list("White", listOf("WHITE_CONCRETE"))
                            string.list("Black", listOf("BLACK_CONCRETE"))
                        }
                        block("Sound") {
                            string("PickUp", "BLOCK_METAL_HIT")
                            string("Move", "BLOCK_METAL_STEP")
                            string("Capture", "ENTITY_ENDER_DRAGON_DEATH")
                        }
                    }
                    pieceData.addInstance("Queen") {
                        string("Name", "Queen")
                        char("Char", 'q')
                        block("Item") {
                            string("White", "DIAMOND_BLOCK")
                            string("Black", "NETHERITE_BLOCK")
                        }
                        block("Structure") {
                            string.list("White", listOf("DIAMOND_BLOCK"))
                            string.list("Black", listOf("NETHERITE_BLOCK"))
                        }
                        block("Sound") {
                            string("PickUp", "ENTITY_WITCH_CELEBRATE")
                            string("Move", "BLOCK_GLASS_STEP")
                            string("Capture", "ENTITY_WITCH_DEATH")
                        }
                    }
                    pieceData.addInstance("Rook") {
                        string("Name", "Rook")
                        char("Char", 'r')
                        block("Item") {
                            string("White", "IRON_BLOCK")
                            string("Black", "GOLD_BLOCK")
                        }
                        block("Structure") {
                            string.list("White", listOf("IRON_BLOCK"))
                            string.list("Black", listOf("GOLD_BLOCK"))
                        }
                        block("Sound") {
                            string("PickUp", "ENTITY_IRON_GOLEM_STEP")
                            string("Move", "ENTITY_IRON_GOLEM_STEP")
                            string("Capture", "ENTITY_IRON_GOLEM_DEATH")
                        }
                    }
                    pieceData.addInstance("Bishop") {
                        string("Name", "Bishop")
                        char("Char", 'b')
                        block("Item") {
                            string("White", "POLISHED_DIORITE")
                            string("Black", "POLISHED_BLACKSTONE")
                        }
                        block("Structure") {
                            string.list("White", listOf("POLISHED_DIORITE"))
                            string.list("Black", listOf("POLISHED_BLACKSTONE"))
                        }
                        block("Sound") {
                            string("PickUp", "ENTITY_SPIDER_AMBIENT")
                            string("Move", "ENTITY_SPIDER_STEP")
                            string("Capture", "ENTITY_SPIDER_DEATH")
                        }
                    }
                    pieceData.addInstance("Knight") {
                        string("Name", "Knight")
                        char("Char", 'n')
                        block("Item") {
                            string("White", "END_STONE")
                            string("Black", "BLACKSTONE")
                        }
                        block("Structure") {
                            string.list("White", listOf("END_STONE"))
                            string.list("Black", listOf("BLACKSTONE"))
                        }
                        block("Sound") {
                            string("PickUp", "ENTITY_HORSE_JUMP")
                            string("Move", "ENTITY_HORSE_STEP")
                            string("Capture", "ENTITY_HORSE_DEATH")
                        }
                    }
                    pieceData.addInstance("Pawn") {
                        string("Name", "Pawn")
                        char("Char", 'p')
                        block("Item") {
                            string("White", "WHITE_CARPET")
                            string("Black", "BLACK_CARPET")
                        }
                        block("Structure") {
                            string.list("White", listOf("WHITE_CARPET"))
                            string.list("Black", listOf("BLACK_CARPET"))
                        }
                        block("Sound") {
                            string("PickUp", "BLOCK_STONE_HIT")
                            string("Move", "BLOCK_STONE_STEP")
                            string("Capture", "BLOCK_STONE_BREAK")
                        }
                    }
                    byEnum(ClassName("gregc.gregchess.chess", "PieceType"),
                        "King", "Queen", "Rook", "Bishop", "Knight", "Pawn")
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
                        formatString("General", String::class, default = "$1: ")
                        formatString("White", String::class, default = "White $1: ")
                        formatString("Black", String::class, default = "Black $1: ")
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
                    bool("SimpleCastling", "false")
                    int("TileSize", "3")
                }
                blockList("Clock") {
                    enumString("Type", ClassName("gregc.gregchess.chess.component.ChessClock", "Type"), "INCREMENT", false)
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