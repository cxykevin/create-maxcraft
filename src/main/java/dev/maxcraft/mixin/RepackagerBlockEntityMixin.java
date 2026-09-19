package dev.maxcraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.repackager.PackageRepackageHelper;
import com.simibubi.create.content.logistics.packager.repackager.RepackagerBlockEntity;

import dev.maxcraft.logistics.RepackageHelperAccess;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Keeps the Repackager from deleting packages it never merged.
 *
 * <p>Upstream stops scanning its inventory the moment an order's fragments look complete, then deletes <em>every</em>
 * package carrying that order id. Any fragment still sitting further along in the inventory - a duplicate fragment
 * index, or a fragment produced by a request that outran the packager's own bookkeeping - is destroyed without ever
 * reaching the merge, so its items simply vanish.
 *
 * <p>Handing the helper the whole inventory up front means the merge counts every fragment that the removal pass is
 * about to consume. Nothing is deleted that was not merged first.
 */
@Mixin(RepackagerBlockEntity.class)
public abstract class RepackagerBlockEntityMixin {

    @Shadow
    public PackageRepackageHelper repackageHelper;

    @Inject(
        method = "attemptToRepackage",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/packager/repackager/PackageRepackageHelper;clear()V",
            shift = At.Shift.AFTER
        )
    )
    private void maxcraft$collectEveryFragment(IItemHandler targetInv, CallbackInfo ci) {
        RepackageHelperAccess helper = (RepackageHelperAccess) repackageHelper;
        boolean found = false;

        for (int slot = 0; slot < targetInv.getSlots(); slot++) {
            ItemStack extracted = targetInv.extractItem(slot, 1, true);
            if (extracted.isEmpty() || !PackageItem.isPackage(extracted))
                continue;
            if (!repackageHelper.isFragmented(extracted))
                continue;
            helper.maxcraft$preCollect(extracted);
            found = true;
        }

        if (found)
            helper.maxcraft$beginPreCollect();
    }
}
