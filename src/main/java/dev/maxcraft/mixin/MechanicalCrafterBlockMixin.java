package dev.maxcraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlock;

import dev.maxcraft.content.logistics.CrafterInputLinks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Makes a Large Package Component link a whole matrix of Mechanical Crafters in one click.
 *
 * <p>Hooked ahead of Create's own interaction: the component has no business with the crafter's front, where items
 * are put in by hand, so only the back face is taken and the click never reaches the machine.
 */
@Mixin(MechanicalCrafterBlock.class)
public abstract class MechanicalCrafterBlockMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void maxcraft$linkNeighbours(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                         InteractionHand hand, BlockHitResult hitResult,
                                         CallbackInfoReturnable<ItemInteractionResult> cir) {
        if (!CrafterInputLinks.isLinkItem(stack) || !CrafterInputLinks.isBackFace(state, hitResult.getDirection()))
            return;

        if (!level.isClientSide())
            CrafterInputLinks.toggleMatrix(level, pos, player);

        cir.setReturnValue(level.isClientSide() ? ItemInteractionResult.SUCCESS
            : ItemInteractionResult.sidedSuccess(false));
    }
}
