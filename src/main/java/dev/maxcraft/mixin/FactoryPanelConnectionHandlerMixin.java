package dev.maxcraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnectionHandler;

/**
 * Relaxes the connection handler's mirror of the nine input limit.
 *
 * <p>It refuses with "cannot add more inputs" before the connection is even attempted, so it has to let an extended
 * panel's connections through; the real limit is enforced where the connection is made. It cannot tell which panel it
 * is looking at, so it never objects and lets that check decide.
 */
@Mixin(FactoryPanelConnectionHandler.class)
public abstract class FactoryPanelConnectionHandlerMixin {

    @ModifyConstant(method = "checkForIssues", constant = @Constant(intValue = 9))
    private static int maxcraft$relaxedInputLimit(int original) {
        return Integer.MAX_VALUE;
    }
}
