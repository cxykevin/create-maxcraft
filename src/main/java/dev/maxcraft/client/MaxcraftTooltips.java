package dev.maxcraft.client;

import java.util.List;

import com.simibubi.create.AllBlocks;

import dev.maxcraft.Maxcraft;
import dev.maxcraft.registry.MaxcraftBlocks;
import dev.maxcraft.registry.MaxcraftDataComponents;
import dev.maxcraft.registry.MaxcraftItems;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Adds the "hold shift" descriptions to what this mod adds, and to Create's own factory gauge.
 *
 * <p>Create's machines explain themselves behind a shift press, so these do the same: without it there is one line
 * saying there is more to read, and with it the item says what it is for and how it is upgraded. The gauge is
 * Create's own item, so its description depends on whether the stack carries this mod's grid size.
 */
@EventBusSubscriber(modid = Maxcraft.MOD_ID, value = Dist.CLIENT)
public final class MaxcraftTooltips {

    private static final String HOLD_SHIFT = "maxcraft.tooltip.hold_shift";

    private static final List<String> COMPONENT_LINES = List.of(
        "maxcraft.tooltip.large_package_component.1",
        "maxcraft.tooltip.large_package_component.2",
        "maxcraft.tooltip.large_package_component.3");

    private static final List<String> LARGE_PACKAGER_LINES = List.of(
        "maxcraft.tooltip.large_packager.1",
        "maxcraft.tooltip.large_packager.2");

    private static final List<String> LARGE_REPACKAGER_LINES = List.of(
        "maxcraft.tooltip.large_repackager.1",
        "maxcraft.tooltip.large_repackager.2");

    private static final List<String> STOCK_TICKER_LINES = List.of(
        "maxcraft.tooltip.extended_stock_ticker.1",
        "maxcraft.tooltip.extended_stock_ticker.2");

    private static final List<String> GAUGE_LINES = List.of(
        "maxcraft.tooltip.factory_gauge.1",
        "maxcraft.tooltip.factory_gauge.2");

    private static final List<String> PLAIN_PACKAGER_LINES = List.of("maxcraft.tooltip.packager");

    private static final List<String> PLAIN_REPACKAGER_LINES = List.of("maxcraft.tooltip.repackager");

    private static final List<String> PLAIN_STOCK_TICKER_LINES = List.of("maxcraft.tooltip.stock_ticker");

    private static final List<String> EXTENDED_GAUGE_LINES = List.of(
        "maxcraft.tooltip.extended_factory_gauge.1",
        "maxcraft.tooltip.extended_factory_gauge.2");

    private MaxcraftTooltips() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        List<String> lines = maxcraft$description(event.getItemStack());
        if (lines == null)
            return;

        if (!Screen.hasShiftDown()) {
            event.getToolTip()
                .add(Component.translatable(HOLD_SHIFT)
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        for (String line : lines)
            event.getToolTip()
                .add(Component.translatable(line)
                    .withStyle(ChatFormatting.GRAY));
    }

    /** The description of this stack, or null when it is not one of ours to explain. */
    private static List<String> maxcraft$description(ItemStack stack) {
        if (stack.is(MaxcraftItems.LARGE_PACKAGE_COMPONENT.get()))
            return COMPONENT_LINES;
        if (stack.is(MaxcraftBlocks.LARGE_PACKAGER_ITEM.get()))
            return LARGE_PACKAGER_LINES;
        if (stack.is(MaxcraftBlocks.LARGE_REPACKAGER_ITEM.get()))
            return LARGE_REPACKAGER_LINES;
        if (stack.is(MaxcraftBlocks.EXTENDED_STOCK_TICKER_ITEM.get()))
            return STOCK_TICKER_LINES;
        // Create's own machines say how to turn them into the large version, which is the only way a player would
        // find out that the component does that.
        if (stack.is(AllBlocks.PACKAGER.get()
            .asItem()))
            return PLAIN_PACKAGER_LINES;
        if (stack.is(AllBlocks.REPACKAGER.get()
            .asItem()))
            return PLAIN_REPACKAGER_LINES;
        if (stack.is(AllBlocks.STOCK_TICKER.get()
            .asItem()))
            return PLAIN_STOCK_TICKER_LINES;
        if (stack.is(AllBlocks.FACTORY_GAUGE.get()
            .asItem()))
            return stack.has(MaxcraftDataComponents.GAUGE_GRID_SIZES.get()) ? EXTENDED_GAUGE_LINES : GAUGE_LINES;
        return null;
    }
}
