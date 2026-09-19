package dev.maxcraft.logistics;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.content.logistics.BigItemStack;

import net.minecraft.world.item.ItemStack;

/**
 * Turns a recipe's own grid into the pattern a large Mechanical Crafter array expects.
 *
 * <p>A recipe smaller than the array - a 3x3 crafting recipe on a 5x5 crafter - is laid out in the array's top left,
 * and the rest of the grid is filled with empty cells. That is what makes it a pattern for the larger machine: the
 * cells keep their rows and columns instead of being walked one after another, which would put a 3x3 recipe's ninth
 * item into the fifth crafter of the array's first row and leave the machine with an arrangement that is not the
 * recipe at all.
 *
 * <p>The grid is assumed to be rectangular and as wide as the pattern the panel sent; a recipe that does not fit the
 * grid keeps its own size, so nothing is ever squashed.
 */
public final class CraftingPattern {

    private CraftingPattern() {
    }

    /** A gap in a pattern, the same empty cell Create uses. */
    public static BigItemStack empty() {
        return new BigItemStack(ItemStack.EMPTY, 1);
    }

    /**
     * The recipe's cells laid into a grid-wide pattern, in the top left, with gaps everywhere else.
     *
     * @param recipe       the recipe's cells, row by row, {@code recipeWidth} to a row
     * @param recipeWidth  how many cells the recipe is wide
     * @param recipeHeight how many cells the recipe is tall
     * @param grid         the size of the machine's grid; a recipe larger than it keeps its own size
     */
    public static List<BigItemStack> inGrid(List<BigItemStack> recipe, int recipeWidth, int recipeHeight, int grid) {
        if (recipeWidth <= 0 || recipeHeight <= 0)
            return recipe;

        int width = Math.max(grid, recipeWidth);
        int height = Math.max(grid, recipeHeight);
        List<BigItemStack> pattern = new ArrayList<>(width * height);

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int index = row * recipeWidth + col;
                boolean inside = row < recipeHeight && col < recipeWidth && index < recipe.size();
                pattern.add(inside ? recipe.get(index) : empty());
            }
        }

        return pattern;
    }
}
