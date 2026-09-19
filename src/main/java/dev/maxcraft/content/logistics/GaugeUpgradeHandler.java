package dev.maxcraft.content.logistics;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;

import dev.maxcraft.Maxcraft;
import dev.maxcraft.registry.MaxcraftItems;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Gives a Factory Gauge's panels a large crafting grid when it is right clicked with a Large Package Component.
 *
 * <p>This has to run as a very early interaction event rather than on the block itself. A factory gauge's panels are
 * value boxes, and Create's own {@code ValueSettingsInputHandler} cancels the click as soon as it lands on one, so
 * the block's item interaction never runs for the part of the gauge a player actually aims at.
 *
 * <p>Running at {@link EventPriority#HIGHEST} means this sees the click first, and cancelling it keeps Create from
 * opening the panel configuration on top of the upgrade. An empty hand is left completely alone.
 */
@EventBusSubscriber(modid = Maxcraft.MOD_ID)
public final class GaugeUpgradeHandler {

    private GaugeUpgradeHandler() {
    }

    /** Which panel of the gauge the click landed on, the same way Create works it out for its own interactions. */
    private static PanelSlot maxcraft$targetedSlot(PlayerInteractEvent.RightClickBlock event,
                                                   FactoryPanelBlockEntity gauge) {
        if (event.getHitVec() == null)
            return null;
        return FactoryPanelBlock.getTargetedSlot(event.getPos(), gauge.getBlockState(),
            event.getHitVec()
                .getLocation());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND)
            return;

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || stack.getItem() != MaxcraftItems.LARGE_PACKAGE_COMPONENT.get())
            return;
        if (!(event.getLevel()
            .getBlockState(event.getPos())
            .getBlock() instanceof FactoryPanelBlock))
            return;
        if (!(event.getLevel()
            .getBlockEntity(event.getPos()) instanceof FactoryPanelBlockEntity gauge))
            return;

        // Holding the component always means "upgrade", whether or not the panel already has a size. It raises a
        // grid rather than replacing it, so a panel a player has already widened is never narrowed back.
        //
        // Only the panel that was actually clicked is upgraded: a gauge's four panels are four separate machines
        // sharing one block, and each keeps its own size.
        PanelSlot slot = maxcraft$targetedSlot(event, gauge);
        FactoryPanelBehaviour panel = slot == null ? null : gauge.panels.get(slot);
        boolean changed = false;
        if (panel != null) {
            GridSizeHolder holder = (GridSizeHolder) panel;
            int target = Math.max(holder.maxcraft$gridSize(), GridSizeHolder.DEFAULT_EXTENDED_GRID_SIZE);
            if (target != holder.maxcraft$gridSize() || !holder.maxcraft$hasStoredSize()) {
                holder.maxcraft$setGridSize(target);
                changed = true;
            }
        }

        if (changed) {
            GaugePanelSizes.appearanceChanged(gauge);
            if (!event.getLevel().isClientSide() && !event.getEntity()
                .getAbilities().instabuild)
                stack.shrink(1);
        }

        event.setUseItem(TriState.FALSE);
        event.setUseBlock(TriState.FALSE);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
