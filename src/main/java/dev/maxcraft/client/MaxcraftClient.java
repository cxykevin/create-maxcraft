package dev.maxcraft.client;

import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visualization.BlockEntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagerRenderer;
import com.simibubi.create.content.logistics.packager.PackagerVisual;

import dev.maxcraft.Maxcraft;
import dev.maxcraft.registry.MaxcraftBlockEntityTypes;
import dev.maxcraft.registry.MaxcraftBlocks;

import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * Client side setup for the machines this mod adds.
 *
 * <p>The packager family borrows Create's packager renderer and visual outright, so those machines animate exactly
 * like the machines they are based on.
 */
@EventBusSubscriber(modid = Maxcraft.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class MaxcraftClient {

    private MaxcraftClient() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(MaxcraftBlockEntityTypes.LARGE_PACKAGER.get(), PackagerRenderer::new);
        event.registerBlockEntityRenderer(MaxcraftBlockEntityTypes.LARGE_REPACKAGER.get(), PackagerRenderer::new);
    }

    /**
     * Create's packager visual for our machine block entities. The renderer draws the held box and the visual draws
     * the hatch and tray, so vanilla rendering must not be skipped.
     */
    private static <T extends PackagerBlockEntity> BlockEntityVisualizer<T> maxcraftPackagerVisual() {
        return new BlockEntityVisualizer<T>() {
            @Override
            public BlockEntityVisual<? super T> createVisual(VisualizationContext context, T blockEntity,
                                                             float partialTick) {
                return new PackagerVisual<>(context, blockEntity, partialTick);
            }

            @Override
            public boolean skipVanillaRender(T blockEntity) {
                return false;
            }
        };
    }

    /**
     * Loads a mixin target that would otherwise only load when a player opens it.
     *
     * <p>A mixin config is validated when its target class loads, so a patch that no longer matches Create would
     * otherwise stay silent until someone opens the screen. Loading without initialising it keeps static
     * initialisers out of the way.
     */
    private static void preload(String className) {
        try {
            Class.forName(className, false, MaxcraftClient.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            Maxcraft.LOGGER.warn("Maxcraft: could not preload {}", className, e);
        }
    }

    /**
     * Asks for the brass factory panels to be baked.
     *
     * <p>This runs while the client's resources are being loaded, and it is the only moment a model can be added to
     * the bake - Flywheel's own registry is read from this same event, so registering again here means a bake can
     * never miss the models however the handlers are ordered.
     */
    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        MaxcraftPanelModels.registerAdditional(event);
    }

    /** Reports once per resource load whether the brass panels came out of the bake. */
    @SubscribeEvent
    public static void panelModelsBaked(ModelEvent.BakingCompleted event) {
        // Flywheel fills its partial models in from this same event, so look at them once the dust has settled.
        Minecraft.getInstance()
            .execute(MaxcraftPanelModels::verifyBaked);
    }

    /**
     * Hands this mod's machines over to Create's Ponder scenes, so that hovering one and holding the ponder key plays
     * the scene of the machine it is based on.
     *
     * <p>Ponder reads its plugins once the game has finished loading, and whichever mod adds one first does not
     * matter - they are kept sorted - so this only has to happen before that, which client setup is.
     */
    @SubscribeEvent
    public static void registerPonder(FMLClientSetupEvent event) {
        PonderIndex.addPlugin(new MaxcraftPonderPlugin());
    }

    @SubscribeEvent
    public static void registerVisuals(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            preload("com.simibubi.create.content.logistics.factoryBoard.FactoryPanelScreen");
            preload("com.simibubi.create.content.logistics.factoryBoard.FactoryPanelModel");

            ItemBlockRenderTypes.setRenderLayer(MaxcraftBlocks.LARGE_PACKAGER.get(), RenderType.cutoutMipped());
            ItemBlockRenderTypes.setRenderLayer(MaxcraftBlocks.LARGE_REPACKAGER.get(), RenderType.cutoutMipped());
            ItemBlockRenderTypes.setRenderLayer(MaxcraftBlocks.EXTENDED_STOCK_TICKER.get(),
                RenderType.cutoutMipped());

            VisualizerRegistry.setVisualizer(MaxcraftBlockEntityTypes.LARGE_PACKAGER.get(),
                maxcraftPackagerVisual());
            VisualizerRegistry.setVisualizer(MaxcraftBlockEntityTypes.LARGE_REPACKAGER.get(),
                maxcraftPackagerVisual());
        });
    }
}
