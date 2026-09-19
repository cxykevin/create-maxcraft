package dev.maxcraft.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;

import dev.maxcraft.Maxcraft;

/**
 * Reports every order that is given a crafting pattern.
 *
 * <p>This is the first moment a pattern exists at all: Create only attaches one when the machine that placed the
 * order has a crafting arrangement to send, so a delivery that arrives without a pattern and never logged a line
 * here was already patternless when it left the ordering machine.
 */
@Mixin(PackageOrderWithCrafts.class)
public abstract class PackageOrderWithCraftsMixin {

    @Inject(method = "singleRecipe", at = @At("HEAD"))
    private static void maxcraft$singleRecipe(List<BigItemStack> pattern,
                                              CallbackInfoReturnable<PackageOrderWithCrafts> cir) {
        Maxcraft.LOGGER.info("Maxcraft: an order was given a {}-cell crafting pattern", pattern.size());
    }
}
