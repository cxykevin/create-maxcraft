package dev.maxcraft.content.logistics;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlock;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;

import dev.maxcraft.registry.MaxcraftBlockEntityTypes;
import dev.maxcraft.registry.MaxcraftBlocks;
import dev.maxcraft.registry.MaxcraftItems;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 扩展仓储发报机 / Extended Stock Ticker - a Stock Ticker that fills 27 stacks into a package.
 *
 * <p>It is upgraded from a Stock Ticker in place: right click one with a Large Package Component, or craft the two
 * together. Converting keeps the ticker's whole block entity state, so the configured logistics network, categories
 * and shop payments all survive.
 */
public class ExtendedStockTickerBlock extends StockTickerBlock {

    public static final MapCodec<ExtendedStockTickerBlock> CODEC = simpleCodec(ExtendedStockTickerBlock::new);

    public ExtendedStockTickerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends StockTickerBlockEntity> getBlockEntityType() {
        return MaxcraftBlockEntityTypes.EXTENDED_STOCK_TICKER.get();
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    /** True if the held stack can upgrade a stock ticker. */
    public static boolean isUpgradeItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == MaxcraftItems.LARGE_PACKAGE_COMPONENT.get();
    }

    /**
     * Replaces a stock ticker with an extended one, carrying its block entity data over.
     *
     * @return true if the conversion happened
     */
    public static boolean convert(Level level, BlockPos pos) {
        if (level.isClientSide())
            return false;

        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ExtendedStockTickerBlock)
            return false;
        if (!(level.getBlockEntity(pos) instanceof StockTickerBlockEntity))
            return false;

        return InPlaceUpgrade.apply(level, pos, MaxcraftBlocks.EXTENDED_STOCK_TICKER.get()
            .defaultBlockState()
            .setValue(FACING, state.getValue(FACING)));
    }

    /** The interaction form: converts and takes the component out of the player's hand. */
    public static boolean convert(Level level, BlockPos pos, Player player, ItemStack stack) {
        if (!convert(level, pos))
            return false;
        if (!level.isClientSide() && (player == null || !player.getAbilities().instabuild))
            stack.shrink(1);
        return true;
    }
}
