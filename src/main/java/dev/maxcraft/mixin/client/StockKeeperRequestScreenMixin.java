package dev.maxcraft.mixin.client;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;

import dev.maxcraft.content.logistics.ExtendedStockTickerBlockEntity;

/**
 * Raises the number of distinct item types an order may contain, for the Extended Stock Ticker only.
 *
 * <p>Create limits an order to {@code cols} entries - the same 9 the request screen is wide - in exactly three
 * places: adding an item by click, adding one by scroll, and how much room a recipe order may take. {@code cols} is
 * a constant variable, so javac inlined it as a literal 9, and each of those three methods contains exactly one such
 * literal; replacing it is therefore precise and leaves every layout use of {@code cols} untouched.
 *
 * <p>The order bar only shows nine entries; anything past that is summarised as {@code [+N]} and can still be
 * adjusted by clicking the item in the stock grid, which is how Create already treats an overfull order.
 */
@Mixin(StockKeeperRequestScreen.class)
public abstract class StockKeeperRequestScreenMixin {

    @Shadow
    @Final
    int cols;

    @Shadow
    StockTickerBlockEntity blockEntity;

    @Unique
    private int maxcraft$orderLimit() {
        return ExtendedStockTickerBlockEntity.orderLimit(blockEntity, cols);
    }

    /** {@code itemsToOrder.size() >= 9} while clicking a stock entry. */
    @ModifyConstant(method = "mouseClicked", constant = @Constant(intValue = 9))
    private int maxcraft$clickOrderLimit(int original) {
        return maxcraft$orderLimit();
    }

    /** The same check while scrolling. */
    @ModifyConstant(method = "mouseScrolled", constant = @Constant(intValue = 9))
    private int maxcraft$scrollOrderLimit(int original) {
        return maxcraft$orderLimit();
    }

    /** {@code 9 - itemsToOrder.size()}, the room left for a recipe order. */
    @ModifyConstant(method = "requestCraftable", constant = @Constant(intValue = 9))
    private int maxcraft$craftOrderLimit(int original) {
        return maxcraft$orderLimit();
    }
}
