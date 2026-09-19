package dev.maxcraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;

import dev.maxcraft.content.logistics.GridSizeHolder;

import net.minecraft.util.Mth;

/**
 * Gives every factory panel a crafting grid size.
 *
 * <p>This is the whole of the "extended gauge": there is no second block, only a number held by the panel. Anything
 * above Create's 3x3 is what makes a panel search for and accept larger recipes, and the number travels with the
 * gauge - through the item, the block entity and the client - so both sides agree without having to recognise a
 * machine.
 *
 * <p>Storing it is {@code GaugePanelSizes}' job rather than the panel's: a panel only writes itself to NBT once it is
 * in use, which is exactly the state a freshly placed gauge is not in.
 */
@Mixin(FactoryPanelBehaviour.class)
public abstract class FactoryPanelBehaviourMixin implements GridSizeHolder {

    @Unique
    private int maxcraft$gridSize;

    @Override
    public boolean maxcraft$isExtended() {
        return maxcraft$gridSize > MIN_GRID_SIZE;
    }

    @Override
    public int maxcraft$gridSize() {
        return maxcraft$gridSize >= MIN_GRID_SIZE ? maxcraft$gridSize : MIN_GRID_SIZE;
    }

    @Override
    public void maxcraft$setGridSize(int size) {
        maxcraft$gridSize = Mth.clamp(size, MIN_GRID_SIZE, MAX_GRID_SIZE);
    }

    @Override
    public boolean maxcraft$hasStoredSize() {
        return maxcraft$gridSize >= MIN_GRID_SIZE;
    }
}
