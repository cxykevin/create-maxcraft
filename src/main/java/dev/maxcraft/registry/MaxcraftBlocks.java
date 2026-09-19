package dev.maxcraft.registry;

import dev.maxcraft.Maxcraft;
import dev.maxcraft.content.logistics.ExtendedStockTickerBlock;
import dev.maxcraft.content.logistics.LargePackagerBlock;
import dev.maxcraft.content.logistics.LargeRepackagerBlock;

import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Blocks added by this mod.
 */
public final class MaxcraftBlocks {

    public static final DeferredRegister.Blocks REGISTRAR = DeferredRegister.createBlocks(Maxcraft.MOD_ID);

    /**
     * 大型打包机 / Large Packager.
     *
     * <p>Properties mirror Create's own packager, which is built from a gold block copy with its map colour and sound
     * overridden.
     */
    public static final DeferredBlock<LargePackagerBlock> LARGE_PACKAGER = REGISTRAR.register("large_packager",
        () -> new LargePackagerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_BLOCK)
            .mapColor(MapColor.TERRACOTTA_BLUE)
            .sound(SoundType.NETHERITE_BLOCK)
            .noOcclusion()
            .isRedstoneConductor((state, level, pos) -> false)));

    public static final DeferredItem<BlockItem> LARGE_PACKAGER_ITEM =
        MaxcraftItems.REGISTRAR.registerSimpleBlockItem("large_packager", LARGE_PACKAGER);

    /** 大型理包机 / Large Re-Packager - merges a whole order into one package, once it has all arrived. */
    public static final DeferredBlock<LargeRepackagerBlock> LARGE_REPACKAGER = REGISTRAR.register("large_repackager",
        () -> new LargeRepackagerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_BLOCK)
            .mapColor(MapColor.TERRACOTTA_BLUE)
            .sound(SoundType.NETHERITE_BLOCK)
            .noOcclusion()
            .isRedstoneConductor((state, level, pos) -> false)));

    public static final DeferredItem<BlockItem> LARGE_REPACKAGER_ITEM =
        MaxcraftItems.REGISTRAR.registerSimpleBlockItem("large_repackager", LARGE_REPACKAGER);

    /**
     * 扩展仓储发报机 / Extended Stock Ticker.
     *
     * <p>Built exactly like Create's stock ticker: soft metal made of glass, mined with an axe or a pickaxe.
     */
    public static final DeferredBlock<ExtendedStockTickerBlock> EXTENDED_STOCK_TICKER =
        REGISTRAR.register("extended_stock_ticker",
            () -> new ExtendedStockTickerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_BLOCK)
                .sound(SoundType.GLASS)));

    /** Uses Create's own item so the configured network is shown and carried like every other linked block. */
    public static final DeferredItem<BlockItem> EXTENDED_STOCK_TICKER_ITEM =
        MaxcraftItems.REGISTRAR.registerItem("extended_stock_ticker",
            properties -> new LogisticallyLinkedBlockItem(EXTENDED_STOCK_TICKER.get(), properties),
            new Item.Properties());

    private MaxcraftBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        REGISTRAR.register(modEventBus);
    }
}
