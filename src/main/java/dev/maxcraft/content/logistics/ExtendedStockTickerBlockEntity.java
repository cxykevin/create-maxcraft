package dev.maxcraft.content.logistics;

import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;

import dev.maxcraft.registry.MaxcraftBlockEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 扩展仓储发报机 / Extended Stock Ticker - a Stock Ticker that accepts orders with more distinct item types.
 *
 *
 * <p>Everything else - the stock snapshot, the categories, the shop payments, the keeper hat - is Create's, because
 * this is Create's stock ticker.
 */
public class ExtendedStockTickerBlockEntity extends StockTickerBlockEntity {

    /**
     * How many different item types a single order may contain.
     *
     * <p>Create ties this to the request screen's column count ({@code cols}, 9), which is also the width of the
     * order bar, so an order can hold nine types and no more. The extended ticker allows 27.
     */
    public static final int ORDER_LIMIT = 27;

    public ExtendedStockTickerBlockEntity(BlockPos pos, BlockState state) {
        super(MaxcraftBlockEntityTypes.EXTENDED_STOCK_TICKER.get(), pos, state);
    }

    /**
     * The order size limit that applies to a ticker: {@link #ORDER_LIMIT} for an extended one, and Create's own
     * limit for anything else.
     */
    public static int orderLimit(StockTickerBlockEntity ticker, int fallback) {
        return ticker instanceof ExtendedStockTickerBlockEntity ? ORDER_LIMIT : fallback;
    }
}
