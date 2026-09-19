package dev.maxcraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;

import dev.maxcraft.content.logistics.GridSizeHolder;

/**
 * Removes the cap on how many inputs a factory panel accepts, for panels that have been marked as extended.
 *
 * <p>Create allows nine, which is exactly a 3x3 grid's worth of materials, so a panel working with a larger recipe
 * could never be given the ingredients for it: the tenth connection was refused outright, which reads as the material
 * never having been added rather than as anything to do with the display.
 *
 * <p>An extended panel has no limit at all - there is no fixed grid size for it to follow. An ordinary panel keeps
 * Create's nine.
 */
@Mixin(FactoryPanelBehaviour.class)
public abstract class FactoryPanelInputLimitMixin {

    @ModifyConstant(method = "addConnection", constant = @Constant(intValue = 9))
    private int maxcraft$inputLimit(int original) {
        return ((GridSizeHolder) this).maxcraft$isExtended() ? Integer.MAX_VALUE : original;
    }
}
