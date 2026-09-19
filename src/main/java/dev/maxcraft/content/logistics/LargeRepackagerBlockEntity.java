package dev.maxcraft.content.logistics;

import com.simibubi.create.content.logistics.packager.repackager.RepackagerBlockEntity;

import dev.maxcraft.logistics.RepackageHelperAccess;
import dev.maxcraft.registry.MaxcraftBlockEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 大型理包机 / Large Re-Packager - merges a whole order into exactly one package, and refuses to do so until the
 * order has actually arrived.
 *
 * <p>Create's Re-Packager merges as soon as it has seen a package for every fragment index, which a partly fulfilled
 * request satisfies just as well as a complete one; a redstone signal makes it try again every tick, so a half
 * delivered order is turned into a package early, and everything that arrives afterwards is left to be merged again.
 * This machine additionally checks the collected contents against the order context, so it waits for the delivery to
 * be complete.
 *
 * <p>The merge itself is the same one this mod already gives the Re-Packager: one package, of any size. It also
 * refuses the per-recipe split, which would hand out one package per craft entry and leave a Mechanical Crafter
 * holding only a fraction of the ingredients.
 */
public class LargeRepackagerBlockEntity extends RepackagerBlockEntity {

    public LargeRepackagerBlockEntity(BlockPos pos, BlockState state) {
        super(MaxcraftBlockEntityTypes.LARGE_REPACKAGER.get(), pos, state);
        RepackageHelperAccess helper = (RepackageHelperAccess) repackageHelper;
        helper.maxcraft$setStrict(true);
        helper.maxcraft$setSinglePackage(true);
    }
}
