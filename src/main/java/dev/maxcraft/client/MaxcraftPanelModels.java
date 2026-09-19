package dev.maxcraft.client;

import java.util.List;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.maxcraft.Maxcraft;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * The brass copies of Create's factory panel, used to draw the panels of a gauge that works with a large grid.
 *
 * <p>Create draws a placed panel from a partial model rather than from the blockstate, and picks it by the panel's
 * type and whether the panel currently has anything to do - so these are the same four models with the panel texture
 * swapped for a brass one. The shape and how a panel is placed on the block stay Create's.
 *
 * <p><b>When this class loads matters.</b> Flywheel's partial models are registered for baking from a list that is
 * read once, while the client's resources are being loaded - and that happens <i>before</i> the client setup event.
 * A partial model created after it is never baked, and {@link PartialModel#get()} then returns {@code null}, which
 * is what a chunk mesh is not prepared to handle. {@link #init()} is therefore called from the mod constructor, and
 * {@link #registerAdditional(ModelEvent.RegisterAdditional)} registers the models again from an event of our own so
 * a bake can never miss them.
 *
 * <p>Careful with what this class references: it is loaded during mod construction, so it deliberately touches no
 * Create class. The panel enums are narrowed to two booleans by the caller instead.
 */
public final class MaxcraftPanelModels {

    private static final PartialModel PANEL = block("extended_gauge/panel");

    private static final PartialModel PANEL_WITH_BULB = block("extended_gauge/panel_with_bulb");

    private static final PartialModel PANEL_RESTOCKER = block("extended_gauge/panel_restocker");

    private static final PartialModel PANEL_RESTOCKER_WITH_BULB =
        block("extended_gauge/panel_restocker_with_bulb");

    private static final List<PartialModel> MODELS =
        List.of(PANEL, PANEL_WITH_BULB, PANEL_RESTOCKER, PANEL_RESTOCKER_WITH_BULB);

    /**
     * Loads this class, which is what puts the models above in Flywheel's registry of partial models.
     *
     * <p>Called from the mod constructor, on the client only: the registry is read once, early, and a model that
     * misses it is never baked.
     */
    public static void init() {
    }

    private static PartialModel block(String path) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(Maxcraft.MOD_ID, "block/" + path));
    }

    /** The model for a panel, by whether it is a restocker's and whether it is currently doing anything. */
    public static PartialModel forPanel(boolean restocker, boolean active) {
        if (restocker)
            return active ? PANEL_RESTOCKER_WITH_BULB : PANEL_RESTOCKER;
        return active ? PANEL_WITH_BULB : PANEL;
    }

    /** Asks for the brass models to be baked, without relying on Flywheel having seen them in time. */
    public static void registerAdditional(ModelEvent.RegisterAdditional event) {
        for (PartialModel partial : MODELS)
            event.register(ModelResourceLocation.standalone(partial.modelLocation()));
    }

    /**
     * Checks, once the bake is over, that every brass model came out.
     *
     * <p>A model that is missing here is not fatal any more - a panel simply keeps Create's texture - but it means
     * the gauge will not look any different, so it is worth saying out loud in the log.
     */
    public static void verifyBaked() {
        int baked = 0;
        for (PartialModel partial : MODELS) {
            BakedModel model = partial.get();
            if (model != null)
                baked++;
        }
        if (baked == MODELS.size())
            Maxcraft.LOGGER.info("Maxcraft: {} brass factory panel models baked", baked);
        else
            Maxcraft.LOGGER.warn("Maxcraft: only {} of {} brass factory panel models baked - extended gauges will keep "
                + "Create's panel texture", baked, MODELS.size());
    }

    private MaxcraftPanelModels() {
    }
}
