package dev.maxcraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.tterrag.registrate.util.entry.BlockEntry;

import dev.maxcraft.content.logistics.ExtendedStockTickerBlock;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Lets a Blaze Burner find an Extended Stock Ticker.
 *
 * <p>Create's shop feature looks for a stock ticker by comparing the block against its own; without this the
 * extended ticker would be invisible to it and the shop would not open.
 */
@Mixin(BlazeBurnerBlockEntity.class)
public abstract class BlazeBurnerBlockEntityMixin {

    @Redirect(
        method = "getStockTicker",
        at = @At(
            value = "INVOKE",
            target = "Lcom/tterrag/registrate/util/entry/BlockEntry;has(Lnet/minecraft/world/level/block/state/BlockState;)Z"
        )
    )
    private static boolean maxcraft$isStockTicker(BlockEntry<?> entry, BlockState state) {
        return entry.has(state) || state.getBlock() instanceof ExtendedStockTickerBlock;
    }
}
