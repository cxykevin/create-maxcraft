package dev.maxcraft.registry;

import dev.maxcraft.Maxcraft;
import dev.maxcraft.content.logistics.ExtendedStockTickerBlockEntity;
import dev.maxcraft.content.logistics.LargePackagerBlockEntity;
import dev.maxcraft.content.logistics.LargeRepackagerBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Block entity types added by this mod.
 */
public final class MaxcraftBlockEntityTypes {

    public static final DeferredRegister<BlockEntityType<?>> REGISTRAR =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Maxcraft.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LargePackagerBlockEntity>> LARGE_PACKAGER =
        REGISTRAR.register("large_packager",
            () -> BlockEntityType.Builder.of(LargePackagerBlockEntity::new, MaxcraftBlocks.LARGE_PACKAGER.get())
                .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LargeRepackagerBlockEntity>>
        LARGE_REPACKAGER = REGISTRAR.register("large_repackager",
            () -> BlockEntityType.Builder
                .of(LargeRepackagerBlockEntity::new, MaxcraftBlocks.LARGE_REPACKAGER.get())
                .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExtendedStockTickerBlockEntity>>
        EXTENDED_STOCK_TICKER = REGISTRAR.register("extended_stock_ticker",
            () -> BlockEntityType.Builder
                .of(ExtendedStockTickerBlockEntity::new, MaxcraftBlocks.EXTENDED_STOCK_TICKER.get())
                .build(null));

    private MaxcraftBlockEntityTypes() {
    }

    public static void register(IEventBus modEventBus) {
        REGISTRAR.register(modEventBus);
    }
}
