package dev.maxcraft.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.logistics.packager.PackagerRenderer;

import dev.maxcraft.content.logistics.LargePackagerBlock;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Gives the Large Packager the regular tray.
 *
 * <p>Create picks the tray model by asking whether the block is its own packager and otherwise falls back to the
 * Re-Packager's "defrag" tray, which would leave the large packager wearing the wrong furniture.
 */
@Mixin(PackagerRenderer.class)
public abstract class PackagerRendererMixin {

    @Inject(method = "getTrayModel", at = @At("HEAD"), cancellable = true)
    private static void maxcraft$getTrayModel(BlockState blockState, CallbackInfoReturnable<PartialModel> cir) {
        if (blockState.getBlock() instanceof LargePackagerBlock)
            cir.setReturnValue(AllPartialModels.PACKAGER_TRAY_REGULAR);
    }
}
