package dev.maxcraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.logistics.stockTicker.StockTickerBlock;

import dev.maxcraft.content.logistics.ExtendedStockTickerBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Turns a Stock Ticker into an Extended Stock Ticker when it is right clicked with a Large Package Component.
 *
 * <p>Hooked ahead of Create's own interaction so the component never reaches the ticker's UI handling.
 */
@Mixin(StockTickerBlock.class)
public abstract class StockTickerBlockMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void maxcraft$upgrade(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                  InteractionHand hand, BlockHitResult hitResult,
                                  CallbackInfoReturnable<ItemInteractionResult> cir) {
        if (!ExtendedStockTickerBlock.isUpgradeItem(stack))
            return;
        // Also guards the extended ticker itself, which inherits this method.
        if ((Object) this instanceof ExtendedStockTickerBlock)
            return;
        if (!ExtendedStockTickerBlock.convert(level, pos, player, stack))
            return;

        cir.setReturnValue(level.isClientSide() ? ItemInteractionResult.SUCCESS
            : ItemInteractionResult.sidedSuccess(false));
    }
}
