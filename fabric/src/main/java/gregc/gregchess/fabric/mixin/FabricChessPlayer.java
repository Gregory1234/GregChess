package gregc.gregchess.fabric.mixin;

import gregc.gregchess.Color;
import gregc.gregchess.fabric.FabricChessPlayerHelper;
import gregc.gregchess.fabric.player.FabricChessSide;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PlayerEntity.class)
public class FabricChessPlayer implements FabricChessPlayerHelper {
    @NotNull
    @Override
    public FabricChessSide createChessSide(@NotNull Color color) {
        return new FabricChessSide(((PlayerEntity) (Object) this).getGameProfile(), color);
    }
}
