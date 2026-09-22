package dev.maxcraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.packager.PackagerBlock;

import dev.maxcraft.MaxcraftAdvancements;
import dev.maxcraft.content.logistics.LargePackagerBlock;
import dev.maxcraft.content.logistics.LargeRepackagerBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Turns a Packager into a Large Packager, and a Re-Packager into a Large Re-Packager, when they are right clicked
 * with a Large Package Component.
 *
 * <p>Both block classes share this method. Each upgrade only accepts its own machine, so a Re-Packager is never
 * swallowed by the packager upgrade and neither machine converts twice.
 */
@Mixin(PackagerBlock.class)
public abstract class PackagerBlockMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void maxcraft$upgrade(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                  InteractionHand hand, BlockHitResult hitResult,
                                  CallbackInfoReturnable<ItemInteractionResult> cir) {
        if (!LargePackagerBlock.isUpgradeItem(stack))
            return;

        boolean packager = AllBlocks.PACKAGER.has(state);
        boolean converted = packager
            ? LargePackagerBlock.convert(level, pos, player, stack)
            : AllBlocks.REPACKAGER.has(state) && LargeRepackagerBlock.convert(level, pos, player, stack);
        if (!converted)
            return;

        // 大包裹 - the advancement is about the packager; a re-packager upgrade is a different machine.
        if (packager)
            MaxcraftAdvancements.award(player, MaxcraftAdvancements.LARGE_PACKAGE);

        cir.setReturnValue(level.isClientSide() ? ItemInteractionResult.SUCCESS
            : ItemInteractionResult.sidedSuccess(false));
    }
}
