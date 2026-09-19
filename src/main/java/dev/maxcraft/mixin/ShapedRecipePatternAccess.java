package dev.maxcraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.item.crafting.ShapedRecipePattern;

/**
 * Raises the ceiling on how large a shaped pattern may be.
 *
 * <p>NeoForge already turned vanilla's fixed 3x3 into two mutable package-private statics, and grows them whenever a
 * larger recipe is constructed - but that happens after parsing, so the first recipe bigger than the current ceiling
 * still fails to load. Setting them up front is all it takes to allow patterns larger than 9x9.
 *
 * <p>These are non-final, so an accessor mixin can simply write them.
 */
@Mixin(ShapedRecipePattern.class)
public interface ShapedRecipePatternAccess {

    @Accessor("maxWidth")
    static void maxcraft$setMaxWidth(int width) {
        throw new AssertionError();
    }

    @Accessor("maxHeight")
    static void maxcraft$setMaxHeight(int height) {
        throw new AssertionError();
    }
}
