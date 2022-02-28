package gregc.gregchess.fabric.mixin;

import net.minecraft.util.WorldSavePath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(WorldSavePath.class)
public interface WorldSavePathCreator {
    @Invoker("<init>")
    static WorldSavePath create(String relativePath) {
        throw new UnsupportedOperationException();
    }
}
