package dev.maxcraft.mixin;

import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.kinetics.crafter.ConnectedInputHandler.ConnectedInput;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity.Inventory;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.impl.unpacking.CrafterUnpackingHandler;

import dev.maxcraft.content.logistics.LargePackageContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lets a package from a Large Packager fill a whole Mechanical Crafter array.
 *
 * <p>Create's handler walks {@code min(crafters, pattern length)} cells and puts one item in each, so a 3x3 pattern
 * caps a delivery at nine items however large the array - or the package - is. That is right for an ordinary package,
 * which never holds more than nine stacks anyway, but it silently refuses the rest of a large one.
 *
 * <p>Here the pattern's own shape decides: its row length is recovered from the number of entries, and the walk
 * covers exactly that many cells, never more crafters than the pattern describes. The pattern is carried in the
 * package's order context, so it survives the Re-Packager's merge untouched.
 */
@Mixin(CrafterUnpackingHandler.class)
public abstract class CrafterUnpackingHandlerMixin {

    /**
     * The side length of a pattern given its entry count. A gauge's grid is square, so a perfect square count is
     * that grid; anything else is treated as a single row, which is the shape Create's own layout implies.
     */
    @Unique
    private static int maxcraft$patternWidth(int patternSize) {
        int root = (int) Math.round(Math.sqrt(patternSize));
        return root > 0 && root * root == patternSize ? root : patternSize;
    }

    /**
     * How many crafters one row of the array holds, read from the array itself.
     *
     * <p>Create orders the crafters by row, so everything sharing the first row's height is that row. An array that
     * lies flat - every crafter at the same height - has no rows to read, and keeps the pattern's own width instead.
     */
    @Unique
    private static int maxcraft$arrayWidth(List<Inventory> inventories, int fallback) {
        if (inventories.isEmpty())
            return fallback;

        int first = ((CrafterInventoryAccess) inventories.get(0)).maxcraft$blockEntity()
            .getBlockPos()
            .getY();
        int width = 0;
        for (Inventory inventory : inventories) {
            if (((CrafterInventoryAccess) inventory).maxcraft$blockEntity()
                .getBlockPos()
                .getY() != first)
                break;
            width++;
        }

        return width > 0 && width < inventories.size() ? width : fallback;
    }

    @Inject(method = "unpack", at = @At("HEAD"), cancellable = true)
    private void maxcraft$unpack(Level level, BlockPos pos, BlockState state, Direction side, List<ItemStack> items,
                                 @Nullable PackageOrderWithCrafts orderContext, boolean simulate,
                                 CallbackInfoReturnable<Boolean> cir) {
        // Said before anything else, so a delivery that is handled by Create's own code is visible too - that is
        // the path an ordinary packager takes, and it is the one a player is most likely to be using.
        dev.maxcraft.Maxcraft.LOGGER.info("Maxcraft: a crafter was handed {} items, crafting info {}, own handler {}",
            items.size(), PackageOrderWithCrafts.hasCraftingInformation(orderContext)
                ? orderContext.getCraftingInformation()
                    .size()
                : "none",
            LargePackageContext.isActive());

        if (!LargePackageContext.isActive())
            return;
        if (!PackageOrderWithCrafts.hasCraftingInformation(orderContext))
            return;

        List<BigItemStack> craftingContext = orderContext.getCraftingInformation();
        if (craftingContext.isEmpty())
            return;

        if (!(level.getBlockEntity(pos) instanceof MechanicalCrafterBlockEntity crafter)) {
            cir.setReturnValue(false);
            return;
        }

        ConnectedInput input = crafter.getInput();
        List<Inventory> inventories = input.getInventories(level, pos);
        if (inventories.isEmpty()) {
            cir.setReturnValue(false);
            return;
        }

        // The pattern is a flat list, so its shape is recovered from its length. A factory gauge's grid is always
        // square, which is why 25 entries means 5x5 and 9 means 3x3. Walking the array instead - which is what
        // Create does, capped at nine - would place a 3x3 pattern's items into the wrong crafters of a 5x5 array.
        int width = maxcraft$patternWidth(craftingContext.size());

        // The array's rows are what the pattern's rows have to line up with. Create hands the crafters over row by
        // row - top to bottom, and left to right within a row - so the length of the first row is the width the
        // pattern is laid out at. A pattern that is wider or narrower than the array would otherwise have its second
        // row start in the middle of the array's first, and the machine would assemble the wrong shape.
        int rowWidth = maxcraft$arrayWidth(inventories, width);
        dev.maxcraft.Maxcraft.LOGGER.info("Maxcraft: unpacking a {}-cell pattern as {} wide into {} crafters",
            craftingContext.size(), width, inventories.size());

        // One item in each cell the pattern asks for is what completes the arrangement, and a package carrying a
        // whole batch of crafts has more than that to give. The same cells are therefore filled further, one item per
        // cell per round, until the delivery runs out or nothing more fits: a round at a time, so a batch is spread
        // evenly over the cells that want it instead of piling up in the first one - which would leave the machine
        // able to run only as many crafts as its emptiest cell allows.
        //
        // Upstream stops after the first round. Everything past one craft then stayed in the package, the leftover
        // check below refused the delivery, and the package was never accepted at all - which is what a full batch
        // sent to a large crafter looked like.
        boolean moved = true;
        while (moved) {
            moved = false;

            for (int i = 0; i < craftingContext.size(); i++) {
                BigItemStack targetStack = craftingContext.get(i);
                if (targetStack.stack.isEmpty())
                    continue;

                int cell = i / width * rowWidth + i % width;
                if (cell < 0 || cell >= inventories.size())
                    continue;

                Inventory inventory = inventories.get(cell);
                for (ItemStack stack : items) {
                    if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, targetStack.stack))
                        continue;
                    if (!inventory.insertItem(0, stack.copyWithCount(1), simulate)
                        .isEmpty())
                        continue;

                    stack.shrink(1);
                    moved = true;
                    break;
                }
            }
        }

        for (ItemStack item : items) {
            if (!item.isEmpty()) {
                cir.setReturnValue(false);
                return;
            }
        }

        if (!simulate)
            crafter.checkCompletedRecipe(true);

        cir.setReturnValue(true);
    }
}
