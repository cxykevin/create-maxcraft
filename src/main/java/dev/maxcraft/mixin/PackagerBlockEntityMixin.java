package dev.maxcraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;

import dev.maxcraft.MaxcraftConfig;
import dev.maxcraft.content.logistics.LargePackageContext;
import dev.maxcraft.content.logistics.LargePackagerBlock;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Lets the Large Packager fill more than nine stacks into a package.
 *
 * <p>{@code attemptToSend} sizes its staging handler and its extraction loop with {@code PackageItem.SLOTS}, which is
 * a compile time constant and has therefore been inlined into Create's bytecode - patching the field would do
 * nothing. Both uses are the literal 9 in that one method, so they are replaced here instead, with the package
 * size from the server config.
 *
 * <p>Everything else about packaging, including how requests are fragmented, is untouched: an ordinary packager
 * still produces nine stack packages for every request, wherever the request came from.
 */
@Mixin(PackagerBlockEntity.class)
public abstract class PackagerBlockEntityMixin {

    /**
     * Marks the unpack as belonging to a Large Packager.
     *
     * <p>Unpacking handlers only receive the package's contents, so the only place that knows which machine is doing
     * the unpacking is here. The unpack runs synchronously inside this call. Everything else - the package's size,
     * who packed it - is deliberately not consulted.
     */
    @Inject(method = "unwrapBox", at = @At("HEAD"))
    private void maxcraft$beginUnwrap(ItemStack box, boolean simulate, CallbackInfoReturnable<Boolean> cir) {
        BlockEntity self = (BlockEntity) (Object) this;
        LargePackageContext.set(self.getBlockState()
            .getBlock() instanceof LargePackagerBlock);
    }

    @Inject(method = "unwrapBox", at = @At("RETURN"))
    private void maxcraft$endUnwrap(ItemStack box, boolean simulate, CallbackInfoReturnable<Boolean> cir) {
        LargePackageContext.clear();
    }

    @ModifyConstant(method = "attemptToSend", constant = @Constant(intValue = PackageItem.SLOTS))
    private int maxcraft$packageSlotLimit(int original) {
        BlockEntity self = (BlockEntity) (Object) this;
        if (self.getBlockState()
            .getBlock() instanceof LargePackagerBlock)
            return MaxcraftConfig.maxPackageStacks();
        return original;
    }
}
