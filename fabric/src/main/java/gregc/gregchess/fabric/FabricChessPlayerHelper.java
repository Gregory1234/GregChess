package gregc.gregchess.fabric;

import gregc.gregchess.Color;
import gregc.gregchess.fabric.player.FabricChessSide;
import gregc.gregchess.player.ChessPlayer;
import org.jetbrains.annotations.NotNull;

public interface FabricChessPlayerHelper extends ChessPlayer<FabricChessSide> {

    @NotNull
    @Override
    default FabricChessSide createChessSide(@NotNull Color color) {
        throw new UnsupportedOperationException();
    }
}
