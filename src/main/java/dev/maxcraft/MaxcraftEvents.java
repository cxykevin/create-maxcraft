package dev.maxcraft;

import com.simibubi.create.AllCreativeModeTabs;

import dev.maxcraft.content.logistics.ExtendedGaugeItem;
import dev.maxcraft.registry.MaxcraftBlockEntityTypes;
import dev.maxcraft.registry.MaxcraftBlocks;
import dev.maxcraft.registry.MaxcraftItems;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Registration events that are not tied to a single registry.
 */
@EventBusSubscriber(modid = Maxcraft.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class MaxcraftEvents {

    private MaxcraftEvents() {
    }

    /**
     * Create registers the packager's item handler itself; the large packager needs its own entry because it has its
     * own block entity type.
     */
    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            MaxcraftBlockEntityTypes.LARGE_PACKAGER.get(),
            (blockEntity, side) -> blockEntity.inventory);

        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            MaxcraftBlockEntityTypes.LARGE_REPACKAGER.get(),
            (blockEntity, side) -> blockEntity.inventory);

        // Create exposes the stock ticker's shop payments to hoppers; the extended ticker needs its own entry.
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            MaxcraftBlockEntityTypes.EXTENDED_STOCK_TICKER.get(),
            (blockEntity, side) -> blockEntity.getReceivedPaymentsHandler());
    }

    /** Both new items join Create's own tab, next to the machines they belong with. */
    @SubscribeEvent
    public static void buildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey()
            .equals(AllCreativeModeTabs.BASE_CREATIVE_TAB.getKey()))
            return;
        event.accept(MaxcraftItems.LARGE_PACKAGE_COMPONENT.get());
        event.accept(MaxcraftBlocks.LARGE_PACKAGER_ITEM.get());
        event.accept(MaxcraftBlocks.LARGE_REPACKAGER_ITEM.get());
        event.accept(MaxcraftBlocks.EXTENDED_STOCK_TICKER_ITEM.get());

        // The extended gauge is Create's own gauge carrying a grid size, so it is offered as a prepared item rather
        // than as a machine of its own.
        event.accept(ExtendedGaugeItem.create());
    }
}
