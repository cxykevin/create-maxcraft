package dev.maxcraft.content.logistics;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;

import dev.maxcraft.registry.MaxcraftDataComponents;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

/**
 * The Extended Factory Gauge as an item: Create's own Factory Gauge carrying the grid size its panels should start
 * with.
 *
 * <p>Placing it hands that data to the gauge that appears, so the machine is an ordinary gauge whose panels already
 * work with a larger grid. The same item is what the crafting recipe produces, what a gauge with a large grid drops
 * when it is broken, and what the creative tab offers — and it is deliberately bare every time: no network and no
 * other block entity data, so the player binds it to a network themselves before it can go down.
 */
public final class ExtendedGaugeItem {

    /** Selects the marked item's model in Create's gauge item model. */
    public static final int MODEL_DATA = 1;

    private ExtendedGaugeItem() {
    }

    /** The size a gauge item carries, in the shape the component stores it. */
    public static CompoundTag gridSizes(int size) {
        CompoundTag sizes = new CompoundTag();
        sizes.putInt(GaugePanelSizes.ITEM_SIZE_KEY, size);
        return sizes;
    }

    /** Create's Factory Gauge, marked so that the panels it creates start with {@link GridSizeHolder#DEFAULT_EXTENDED_GRID_SIZE}. */
    public static ItemStack create() {
        return marked(GridSizeHolder.DEFAULT_EXTENDED_GRID_SIZE);
    }

    /** The same, carrying this grid size. */
    public static ItemStack marked(int size) {
        return marked(gridSizes(size));
    }

    /**
     * Create's Factory Gauge carrying these grid sizes - and nothing else at all.
     *
     * <p>No block entity data means no network: exactly like a gauge straight off the crafting table, this one has
     * to be bound to a network by the player before it can be placed, and Create's tuning is what puts the network
     * on it. The sizes ride in a component of this mod's own, which that tuning leaves alone.
     */
    public static ItemStack marked(CompoundTag sizes) {
        ItemStack stack = new ItemStack(AllBlocks.FACTORY_GAUGE.get());
        stack.set(MaxcraftDataComponents.GAUGE_GRID_SIZES.get(), sizes.copy());
        return mark(stack);
    }

    /**
     * Gives a gauge that carries large grid sizes its own model and name.
     *
     * <p>The two have to be set from code rather than from the recipe: {@code custom_name} is written with a flat
     * string-only codec, so a recipe can only give an item a literal name, and this one has to be translatable.
     */
    public static ItemStack mark(ItemStack stack) {
        CompoundTag sizes = stack.get(MaxcraftDataComponents.GAUGE_GRID_SIZES.get());
        if (sizes == null || sizes.isEmpty())
            return stack;

        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(MODEL_DATA));
        if (!stack.has(DataComponents.CUSTOM_NAME))
            stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.maxcraft.extended_factory_gauge")
                .withStyle(style -> style.withItalic(false)));
        return stack;
    }
}
