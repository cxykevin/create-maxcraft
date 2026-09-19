package dev.maxcraft.content.logistics;

/**
 * Implemented by {@code FactoryPanelBehaviourMixin}: the size of the crafting recipe a factory gauge works with.
 *
 * <p>Create bakes a 3x3 into the panel in several places and stores the recipe as a flat list with no shape. The size
 * kept here is both the bound on which recipes the panel accepts and the width that pattern is read back as, which
 * is what lets a panel work with mechanical crafting. It lives in the panel's own data, so it needs no machine of
 * its own.
 */
public interface GridSizeHolder {

    /** The smallest usable grid: Create's own 3x3. */
    int MIN_GRID_SIZE = 3;

    /** The largest grid a panel may be set to. */
    int MAX_GRID_SIZE = 30;

    /** Where a panel starts once it has been given a large grid. */
    int DEFAULT_EXTENDED_GRID_SIZE = 5;

    /** The size this panel uses. */
    int maxcraft$gridSize();

    /** Sets the size, clamped to {@link #MIN_GRID_SIZE}..{@link #MAX_GRID_SIZE}. */
    void maxcraft$setGridSize(int size);

    /** True if this panel works with grids larger than Create's. */
    boolean maxcraft$isExtended();

    /** True if a size has actually been stored for this panel, rather than falling back to a default. */
    boolean maxcraft$hasStoredSize();
}
