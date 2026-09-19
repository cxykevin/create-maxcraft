package dev.maxcraft.content.logistics;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/**
 * The grid sizes of a gauge's panels, as they travel with the gauge itself.
 *
 * <p>A panel's size lives on the panel, which is what the machine works with - but a panel only writes itself to NBT
 * once it is in use, and a gauge that has just been placed has panels that are not in use yet. The sizes are
 * therefore kept on the block entity as well, under this one key, and that copy is what a gauge item carries, what a
 * broken gauge drops and what a placed gauge is restored from.
 */
public final class GaugePanelSizes {

    /** The key the sizes are stored under, on the block entity and inside a gauge item alike. */
    public static final String KEY = "MaxcraftGridSizes";

    private GaugePanelSizes() {
    }

    /** Every panel's size, by slot. Panels left at Create's own 3x3 are not written. */
    public static CompoundTag of(FactoryPanelBlockEntity gauge) {
        CompoundTag sizes = new CompoundTag();
        for (PanelSlot slot : PanelSlot.values()) {
            FactoryPanelBehaviour behaviour = gauge.panels.get(slot);
            if (behaviour == null)
                continue;
            int size = ((GridSizeHolder) behaviour).maxcraft$gridSize();
            if (size != GridSizeHolder.MIN_GRID_SIZE)
                sizes.putInt(slot.getSerializedName(), size);
        }
        return sizes;
    }

    /**
     * The key a gauge <i>item</i> carries its size under.
     *
     * <p>An item holds one size, not a map of them: two gauge items only stack when their data is identical, and a
     * map of per-slot sizes made every gauge with a differently arranged panel a different item. Which panel a size
     * ends up on is decided when the item is used, not when it was made.
     */
    public static final String ITEM_SIZE_KEY = "Size";

    /** The size a gauge item carries - never below Create's own grid. */
    public static int sizeOf(CompoundTag itemTag) {
        if (itemTag == null || itemTag.isEmpty())
            return GridSizeHolder.MIN_GRID_SIZE;
        if (itemTag.contains(ITEM_SIZE_KEY))
            return Math.max(GridSizeHolder.MIN_GRID_SIZE, itemTag.getInt(ITEM_SIZE_KEY));

        // Items made before the size was a single value carry a map of them; take the largest it holds.
        int largest = GridSizeHolder.MIN_GRID_SIZE;
        for (String slot : itemTag.getAllKeys())
            largest = Math.max(largest, itemTag.getInt(slot));
        return largest;
    }

    /** True if any of the gauge's panels is past Create's own grid, and the largest of them if so. */
    public static int largestSize(FactoryPanelBlockEntity gauge) {
        int largest = GridSizeHolder.MIN_GRID_SIZE;
        for (PanelSlot slot : PanelSlot.values()) {
            FactoryPanelBehaviour behaviour = gauge.panels.get(slot);
            if (behaviour == null)
                continue;
            GridSizeHolder holder = (GridSizeHolder) behaviour;
            if (holder.maxcraft$isExtended())
                largest = Math.max(largest, holder.maxcraft$gridSize());
        }
        return largest;
    }

    /** Hands stored sizes to the panels. Returns whether that changed anything. */
    public static boolean apply(FactoryPanelBlockEntity gauge, CompoundTag sizes) {
        boolean changed = false;
        for (PanelSlot slot : PanelSlot.values()) {
            if (!sizes.contains(slot.getSerializedName()))
                continue;
            FactoryPanelBehaviour behaviour = gauge.panels.get(slot);
            if (behaviour == null)
                continue;
            GridSizeHolder holder = (GridSizeHolder) behaviour;
            int size = sizes.getInt(slot.getSerializedName());
            if (holder.maxcraft$gridSize() == size && holder.maxcraft$hasStoredSize())
                continue;
            holder.maxcraft$setGridSize(size);
            changed = true;
        }
        return changed;
    }

    /**
     * Announces that the gauge looks different now.
     *
     * <p>How a gauge is drawn is worked out per chunk section from the block entity's model data, and nothing about
     * the blockstate changes when a panel's grid grows - so without this the panel keeps its old texture until
     * something else happens to rebuild that chunk. Create has the same problem with the shape of a panel and marks
     * the block entity to be redrawn for it; this does both that and the model data, on whichever side it is called.
     */
    public static void appearanceChanged(FactoryPanelBlockEntity gauge) {
        gauge.redraw = true;
        gauge.setChanged();
        gauge.notifyUpdate();
        gauge.requestModelDataUpdate();

        Level level = gauge.getLevel();
        if (level != null && level.isClientSide)
            level.sendBlockUpdated(gauge.getBlockPos(), gauge.getBlockState(), gauge.getBlockState(), 16);
    }
}
