package dev.maxcraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;

/**
 * Reaches the crafter a Mechanical Crafter inventory belongs to, which its position is read from.
 */
@Mixin(targets = "com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity$Inventory")
public interface CrafterInventoryAccess {

    @Accessor("blockEntity")
    MechanicalCrafterBlockEntity maxcraft$blockEntity();
}
