package dev.maxcraft.logistics;

import net.minecraft.world.item.ItemStack;

/**
 * Implemented by {@code PackageRepackageHelperMixin} so that the Repackager can hand the helper every fragment in its
 * inventory before the merge starts.
 *
 * <p>See {@code RepackagerBlockEntityMixin} for why that is necessary.
 */
public interface RepackageHelperAccess {

    /**
     * Adds a fragment, bypassing the "already collected" short circuit the helper applies once
     * {@link #maxcraft$beginPreCollect()} has been called.
     */
    void maxcraft$preCollect(ItemStack box);

    /**
     * Marks the helper as pre-collected: from here on {@code addPackageFragment} only reports whether an order is
     * complete and stops adding duplicates, because the caller has already handed over the whole inventory.
     */
    void maxcraft$beginPreCollect();

    /**
     * Makes the completeness check strict: an order is only complete once the fragments that arrived actually cover
     * everything the order asked for, instead of merely carrying every fragment index. The Large Re-Packager runs
     * this way, so it never merges a half delivered order into a package.
     */
    void maxcraft$setStrict(boolean strict);

    /**
     * Makes the merge produce one package per order whatever the order contains. Create's per-recipe split hands out
     * one package per craft entry, which leaves a machine at the far end holding a fragment of the ingredients.
     */
    void maxcraft$setSinglePackage(boolean singlePackage);
}
