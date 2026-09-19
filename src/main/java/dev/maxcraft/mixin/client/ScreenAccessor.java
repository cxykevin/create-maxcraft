package dev.maxcraft.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;

/**
 * {@code Screen#addRenderableWidget} and {@code Screen#removeWidget} are protected and inherited, so a mixin on a
 * screen cannot call them directly: a shadow or invoker only resolves members declared in the target class itself.
 */
@Mixin(Screen.class)
public interface ScreenAccessor {

    @Invoker("addRenderableWidget")
    <T extends GuiEventListener & Renderable & NarratableEntry> T maxcraft$addRenderableWidget(T widget);

    @Invoker("removeWidget")
    void maxcraft$removeWidget(GuiEventListener widget);
}
