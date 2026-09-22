package dev.maxcraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;

import dev.maxcraft.MaxcraftAdvancements;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Notices the one thing a Mechanical Crafter array can make that no other machine can.
 *
 * <p>{@code tick} asks the grid what the arrangement it is holding would produce, right when the last crafter of a
 * chain finishes. The Creative Blaze Cake only ever comes out of the 30x30 Easter egg, so the result of that lookup
 * is the exact moment the array has made one - and everyone near it gets to see it happen.
 */
@Mixin(MechanicalCrafterBlockEntity.class)
public abstract class MechanicalCrafterBlockEntityMixin {

    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/kinetics/crafter/RecipeGridHandler;tryToApplyRecipe"
                + "(Lnet/minecraft/world/level/Level;"
                + "Lcom/simibubi/create/content/kinetics/crafter/RecipeGridHandler$GroupedItems;)"
                + "Lnet/minecraft/world/item/ItemStack;"
        )
    )
    private ItemStack maxcraft$watchForTheEasterEgg(Level level, RecipeGridHandler.GroupedItems items) {
        ItemStack result = RecipeGridHandler.tryToApplyRecipe(level, items);
        // An arrangement that matches nothing at all comes back as null, and is handed straight back to Create.
        if (result != null && result.is(AllItems.CREATIVE_BLAZE_CAKE.get()))
            MaxcraftAdvancements.awardWitnesses(level, ((MechanicalCrafterBlockEntity) (Object) this).getBlockPos(),
                MaxcraftAdvancements.CREATIVE_BLAZE_CAKE);
        return result;
    }
}
