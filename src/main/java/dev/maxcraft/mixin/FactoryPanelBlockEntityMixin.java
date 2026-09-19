package dev.maxcraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;

import dev.maxcraft.content.logistics.GaugePanelSizes;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/**
 * Persists each panel's crafting grid size on the block entity.
 *
 * <p>Create only writes a panel's own data once that panel is in use - connected to inputs and given a count - so a
 * size set on an otherwise unconfigured panel would be lost on reload, and a gauge placed from an item would come up
 * without the sizes that item was carrying. The sizes are kept on the block entity instead, which is always written;
 * {@code GaugePanelSizes} holds them and explains the rest.
 */
@Mixin(FactoryPanelBlockEntity.class)
public abstract class FactoryPanelBlockEntityMixin {

    @Shadow
    public java.util.EnumMap<PanelSlot, FactoryPanelBehaviour> panels;

    @Unique
    private FactoryPanelBlockEntity maxcraft$gauge() {
        return (FactoryPanelBlockEntity) (Object) this;
    }

    @Inject(method = "write", at = @At("RETURN"), require = 0)
    private void maxcraft$writeGridSizes(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket,
                                         CallbackInfo ci) {
        CompoundTag sizes = GaugePanelSizes.of(maxcraft$gauge());
        if (!sizes.isEmpty())
            tag.put(GaugePanelSizes.KEY, sizes);
    }

    @Inject(method = "read", at = @At("RETURN"), require = 0)
    private void maxcraft$readGridSizes(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket,
                                        CallbackInfo ci) {
        if (!tag.contains(GaugePanelSizes.KEY))
            return;

        // A gauge's panels exist from the moment its block entity is built, so the sizes land on them even while the
        // panels are still unused - which is the state a gauge is in when it is placed from an item.
        boolean changed = GaugePanelSizes.apply(maxcraft$gauge(), tag.getCompound(GaugePanelSizes.KEY));
        if (changed && clientPacket)
            GaugePanelSizes.appearanceChanged(maxcraft$gauge());
    }
}
