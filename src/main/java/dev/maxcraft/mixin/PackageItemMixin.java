package dev.maxcraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.box.PackageStyles;

import dev.maxcraft.logistics.PackageContents;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Lifts the nine stack limit off of packages.
 *
 * <p>Unpatched Create builds a fixed {@code new ItemStackHandler(9)} whenever it reads or writes package contents,
 * which silently caps every package at nine stacks:
 * <ul>
 *   <li>{@code getContents} hands back nine slots, so anything past slot 9 is either dropped by callers or makes
 *       {@code ItemHelper#fillItemStackHandler} walk off the end of the handler.</li>
 *   <li>{@code containing} writes whatever it is given, but chokes once a package needs more than the 256 slots
 *       {@code ItemContainerContents} allows.</li>
 * </ul>
 * Both are redirected to {@link PackageContents}, which stores oversized contents in this mod's own unbounded
 * component while leaving ordinary packages byte-identical to vanilla Create.
 *
 * <p>Note that the packager's own nine-stack-per-package behaviour is deliberately untouched: that is the request
 * and fragmentation system, not storage.
 */
@Mixin(PackageItem.class)
public abstract class PackageItemMixin {

    @Inject(
        method = "getContents(Lnet/minecraft/world/item/ItemStack;)Lnet/neoforged/neoforge/items/ItemStackHandler;",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void maxcraft$getContents(ItemStack box, CallbackInfoReturnable<ItemStackHandler> cir) {
        cir.setReturnValue(PackageContents.toHandler(box));
    }

    @Inject(
        method = "containing(Lnet/neoforged/neoforge/items/ItemStackHandler;)Lnet/minecraft/world/item/ItemStack;",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void maxcraft$containing(ItemStackHandler stacks, CallbackInfoReturnable<ItemStack> cir) {
        // Mirrors PackageItem#containing, minus the ceiling on how much a package may hold.
        ItemStack box = PackageStyles.getRandomBox();
        PackageContents.write(box, stacks);
        cir.setReturnValue(box);
    }
}
