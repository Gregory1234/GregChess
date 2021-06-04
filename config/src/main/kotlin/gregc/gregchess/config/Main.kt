package gregc.gregchess.config

import com.squareup.kotlinpoet.*
import java.io.File

fun main(args: Array<String>) {
    config(File(args[0])) {
        addStringList("ChessArenas")
        addBlock("Request") {
            addString("Accept", "&a[Accept]")
            addString("Cancel", "&c[Cancel]")
            addDefaultBool("SelfAccept", true)
            inlineFiniteBlockList("RequestType", "Duel", "Draw", "Takeback") {
                addBlock("Sent") {
                    addString("Request")
                    addFormatString("Cancel", null, String::class.asClassName())
                    addFormatString("Accept", null, String::class.asClassName())
                }
                addBlock("Received") {
                    addFormatString("Request", null, String::class.asClassName(), String::class.asClassName())
                    addFormatString("Cancel", null, String::class.asClassName())
                    addFormatString("Accept", null, String::class.asClassName())
                }
                addBlock("Error") {
                    addString("NotFound")
                    addString("CannotSend")
                }
                addFormatString("Expired", null, String::class.asClassName())
                addOptionalDuration("Duration")
            }
            instanceBlock("Duel") {
                addBlock("Sent") {
                    addString("Request", "&eDuel request sent.")
                    addString("Cancel", "&eYou cancelled the duel with $1.")
                    addString("Accept", "&aYou accepted the duel request.")
                }
                addBlock("Received") {
                    addString("Request", "&e$1 challenges you to a chess game: $2.")
                    addString("Cancel", "&eDuel with $1 has been cancelled.")
                    addString("Accept", "&aYour duel request has been accepted.")
                }
                addBlock("Error") {
                    addString("NotFound", "&cDuel request not found.")
                }
                addString("Expired", "&eDuel request with $1 has expired.")
                addDuration("Duration", "5m")
            }
            instanceBlock("Draw") {
                addBlock("Sent") {
                    addString("Request", "&eDraw offer sent.")
                    addString("Cancel", "&eYou cancelled the draw offer.")
                    addString("Accept", "&aYou accepted the draw offer.")
                }
                addBlock("Received") {
                    addString("Request", "&eYour opponent wants a draw.")
                    addString("Cancel", "&eThe draw offer has been cancelled.")
                    addString("Accept", "&aThe draw offer has been accepted.")
                }
                addBlock("Error") {
                    addString("CannotSend", "&cIt is your opponent's turn!")
                }
            }
            instanceBlock("Takeback") {
                addBlock("Sent") {
                    addString("Request", "&eTakeback request sent.")
                    addString("Cancel", "&eYou cancelled the takeback request.")
                    addString("Accept", "&aYou accepted the takeback request.")
                }
                addBlock("Received") {
                    addString("Request", "&eYour opponent wants a takeback.")
                    addString("Cancel", "&eThe takeback request has been cancelled.")
                    addString("Accept", "&aThe takeback request has been accepted.")
                }
                addBlock("Error") {
                    addString("CannotSend", "&cIt is your turn!")
                }
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
                addFormatString("WhiteWon", "The game has finished! White won by $1.", String::class.asClassName())
                addFormatString("BlackWon", "The game has finished! Black won by $1.", String::class.asClassName())
                addFormatString("ItWasADraw", "The game has finished! It was a draw by $1.", String::class.asClassName())
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
                addString("PieceNotFound", "&cPiece was not found!")
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
                    addFormatString("Piece", null, String::class.asClassName())
                }
                bySides("White", "Black")
                instanceBlock("White") {
                    addString("Name", "White")
                    addChar("Char", 'w')
                    addString("Piece", "&bWhite $1")
                }
                instanceBlock("Black") {
                    addString("Name", "Black")
                    addChar("Char", 'b')
                    addString("Piece", "&bBlack $1")
                }
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
                byEnum(ClassName("gregc.gregchess.chess", "Floor"),
                    "Light", "Dark", "Move", "Capture", "Special", "Nothing", "Other", "LastStart", "LastEnd")
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
                        byEnum(ClassName("gregc.gregchess.chess", "PieceSound"), "PickUp", "Move", "Capture")
                    }
                }
                byEnum(ClassName("gregc.gregchess.chess", "PieceType"),
                    "King", "Queen", "Rook", "Bishop", "Knight", "Pawn")
                instanceBlock("King") {
                    addString("Name", "King")
                    addChar("Char", 'k')
                    addBlock("Item") {
                        addEnum("White", mat, "WHITE_CONCRETE")
                        addEnum("Black", mat, "BLACK_CONCRETE")
                    }
                    addBlock("Structure") {
                        addEnumList("White", mat, listOf("WHITE_CONCRETE"))
                        addEnumList("Black", mat, listOf("BLACK_CONCRETE"))
                    }
                    addBlock("Sound") {
                        addEnum("PickUp", sound, "BLOCK_METAL_HIT")
                        addEnum("Move", sound, "BLOCK_METAL_STEP")
                        addEnum("Capture", sound, "ENTITY_ENDER_DRAGON_DEATH")
                    }
                }
                instanceBlock("Queen") {
                    addString("Name", "Queen")
                    addChar("Char", 'q')
                    addBlock("Item") {
                        addEnum("White", mat, "DIAMOND_BLOCK")
                        addEnum("Black", mat, "NETHERITE_BLOCK")
                    }
                    addBlock("Structure") {
                        addEnumList("White", mat, listOf("DIAMOND_BLOCK"))
                        addEnumList("Black", mat, listOf("NETHERITE_BLOCK"))
                    }
                    addBlock("Sound") {
                        addEnum("PickUp", sound, "ENTITY_WITCH_CELEBRATE")
                        addEnum("Move", sound, "BLOCK_GLASS_STEP")
                        addEnum("Capture", sound, "ENTITY_WITCH_DEATH")
                    }
                }
                instanceBlock("Rook") {
                    addString("Name", "Rook")
                    addChar("Char", 'r')
                    addBlock("Item") {
                        addEnum("White", mat, "IRON_BLOCK")
                        addEnum("Black", mat, "GOLD_BLOCK")
                    }
                    addBlock("Structure") {
                        addEnumList("White", mat, listOf("IRON_BLOCK"))
                        addEnumList("Black", mat, listOf("GOLD_BLOCK"))
                    }
                    addBlock("Sound") {
                        addEnum("PickUp", sound, "ENTITY_IRON_GOLEM_STEP")
                        addEnum("Move", sound, "ENTITY_IRON_GOLEM_STEP")
                        addEnum("Capture", sound, "ENTITY_IRON_GOLEM_DEATH")
                    }
                }
                instanceBlock("Bishop") {
                    addString("Name", "Bishop")
                    addChar("Char", 'b')
                    addBlock("Item") {
                        addEnum("White", mat, "POLISHED_DIORITE")
                        addEnum("Black", mat, "POLISHED_BLACKSTONE")
                    }
                    addBlock("Structure") {
                        addEnumList("White", mat, listOf("POLISHED_DIORITE"))
                        addEnumList("Black", mat, listOf("POLISHED_BLACKSTONE"))
                    }
                    addBlock("Sound") {
                        addEnum("PickUp", sound, "ENTITY_SPIDER_AMBIENT")
                        addEnum("Move", sound, "ENTITY_SPIDER_STEP")
                        addEnum("Capture", sound, "ENTITY_SPIDER_DEATH")
                    }
                }
                instanceBlock("Knight") {
                    addString("Name", "Knight")
                    addChar("Char", 'n')
                    addBlock("Item") {
                        addEnum("White", mat, "END_STONE")
                        addEnum("Black", mat, "BLACKSTONE")
                    }
                    addBlock("Structure") {
                        addEnumList("White", mat, listOf("END_STONE"))
                        addEnumList("Black", mat, listOf("BLACKSTONE"))
                    }
                    addBlock("Sound") {
                        addEnum("PickUp", sound, "ENTITY_HORSE_JUMP")
                        addEnum("Move", sound, "ENTITY_HORSE_STEP")
                        addEnum("Capture", sound, "ENTITY_HORSE_DEATH")
                    }
                }
                instanceBlock("Pawn") {
                    addString("Name", "Pawn")
                    addChar("Char", 'p')
                    addBlock("Item") {
                        addEnum("White", mat, "WHITE_CARPET")
                        addEnum("Black", mat, "BLACK_CARPET")
                    }
                    addBlock("Structure") {
                        addEnumList("White", mat, listOf("WHITE_CARPET"))
                        addEnumList("Black", mat, listOf("BLACK_CARPET"))
                    }
                    addBlock("Sound") {
                        addEnum("PickUp", sound, "BLOCK_STONE_HIT")
                        addEnum("Move", sound, "BLOCK_STONE_STEP")
                        addEnum("Capture", sound, "BLOCK_STONE_BREAK")
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
                addBlock("Format") {
                    addFormatString("General", "$1: ", String::class.asClassName())
                    addFormatString("White", "White $1: ", String::class.asClassName())
                    addFormatString("Black", "Black $1: ", String::class.asClassName())
                    byOptSides("White", "Black", "General")
                }
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
        addBlock("Settings") {
            addBlockList("Presets") {
                addOptionalString("Variant")
                addOptionalString("Board")
                addOptionalString("Clock")
                addDefaultBool("SimpleCastling", false)
                addDefaultInt("TileSize", 3)
            }
            addBlockList("Clock") {
                addEnum("Type", ClassName("gregc.gregchess.chess.component.ChessClock", "Type"), "INCREMENT", false)
                addDuration("Initial", null)
                addDuration("Increment", "0s", true)
            }
        }
    }
}