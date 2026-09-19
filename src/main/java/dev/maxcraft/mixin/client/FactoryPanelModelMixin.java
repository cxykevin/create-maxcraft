package dev.maxcraft.mixin.client;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelState;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelType;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelModel;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.foundation.model.BakedQuadHelper;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.maxcraft.Maxcraft;
import dev.maxcraft.client.MaxcraftPanelModels;
import dev.maxcraft.content.logistics.GridSizeHolder;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/**
 * Draws a placed gauge's panels in brass when the panel works with a large grid.
 *
 * <p>A factory gauge has no blockstate model of its own: Create bakes one model for the block and fills in the
 * panels per position, reading which panels exist and what they are doing from model data. That is the only place
 * a panel's appearance can differ from its neighbours, so the model data is extended with the set of slots that
 * are extended, and the panel is laid out from a brass copy of Create's model instead.
 *
 * <p>Panels that were never given a large grid are left entirely to Create - both injections fall through unless
 * the slot is in that set.
 */
@Mixin(FactoryPanelModel.class)
public abstract class FactoryPanelModelMixin {

    @Unique
    private static final ModelProperty<EnumSet<PanelSlot>> MAXCRAFT_EXTENDED_SLOTS = new ModelProperty<>();

    @Unique
    private static boolean maxcraft$reportedMissingModel;

    /** The set of extended panels each gauge was last drawn with, so a change is reported once. */
    @Unique
    private static final Map<BlockPos, EnumSet<PanelSlot>> MAXCRAFT_DRAWN = new ConcurrentHashMap<>();

    /** Which of the gauge's panels are extended, so the model knows which of them to draw in brass. */
    @Inject(method = "gatherModelData", at = @At("RETURN"), cancellable = true)
    private void maxcraft$gatherExtendedSlots(ModelData.Builder builder, BlockAndTintGetter world, BlockPos pos,
                                              BlockState state, ModelData blockEntityData,
                                              CallbackInfoReturnable<ModelData.Builder> cir) {
        EnumSet<PanelSlot> extended = EnumSet.noneOf(PanelSlot.class);
        for (PanelSlot slot : PanelSlot.values()) {
            FactoryPanelBehaviour behaviour = FactoryPanelBehaviour.at(world, new FactoryPanelPosition(pos, slot));
            if (behaviour instanceof GridSizeHolder holder && holder.maxcraft$isExtended())
                extended.add(slot);
        }
        EnumSet<PanelSlot> drawnBefore = MAXCRAFT_DRAWN.put(pos.immutable(), EnumSet.copyOf(extended));
        if (!extended.equals(drawnBefore))
            Maxcraft.LOGGER.info("Maxcraft: the gauge at {} is drawn with extended panels {}", pos, extended);

        cir.setReturnValue(cir.getReturnValue()
            .with(MAXCRAFT_EXTENDED_SLOTS, extended));
    }

    /**
     * Lays an extended panel out exactly as Create does, from the brass model.
     *
     * <p>The vertex transform is Create's own, copied rather than called: the panel that is added is the one place
     * the sprite is chosen, and the brass model has to go through the same slot offset and block rotation as the
     * original to sit in the same spot.
     */
    @Inject(method = "addPanel", at = @At("HEAD"), cancellable = true)
    private void maxcraft$addExtendedPanel(List<BakedQuad> quads, BlockState state, PanelSlot slot, PanelType type,
                                           PanelState panelState, RandomSource rand, ModelData data,
                                           RenderType renderType, boolean ponder, CallbackInfo ci) {
        EnumSet<PanelSlot> extended = data.get(MAXCRAFT_EXTENDED_SLOTS);
        if (extended == null || !extended.contains(slot))
            return;

        PartialModel factoryPanel =
            MaxcraftPanelModels.forPanel(type == PanelType.PACKAGER, panelState == PanelState.ACTIVE);
        BakedModel bakedPanel = factoryPanel.get();

        // A model that did not come out of the bake must never be fatal - this runs on the chunk meshing threads,
        // where a null is a crash rather than a missing texture. The panel simply keeps Create's own appearance.
        if (bakedPanel == null) {
            if (!maxcraft$reportedMissingModel) {
                maxcraft$reportedMissingModel = true;
                Maxcraft.LOGGER.warn("Maxcraft: the brass factory panel models are not baked - extended gauges will "
                    + "keep Create's panel texture");
            }
            return;
        }

        ci.cancel();

        List<BakedQuad> quadsToAdd = bakedPanel.getQuads(state, null, rand, data, RenderType.solid());
        float xRot = (180.0F / (float) Math.PI) * FactoryPanelBlock.getXRot(state);
        float yRot = (180.0F / (float) Math.PI) * FactoryPanelBlock.getYRot(state);

        for (BakedQuad bakedQuad : quadsToAdd) {
            int[] vertices = bakedQuad.getVertices();
            int[] transformedVertices = Arrays.copyOf(vertices, vertices.length);
            Vec3 quadNormal = Vec3.atLowerCornerOf(bakedQuad.getDirection()
                .getNormal());
            quadNormal = VecHelper.rotate(quadNormal, 180.0, Axis.Y);
            quadNormal = VecHelper.rotate(quadNormal, (double) (xRot + 90.0F), Axis.X);
            quadNormal = VecHelper.rotate(quadNormal, (double) yRot, Axis.Y);

            for (int i = 0; i < vertices.length / BakedQuadHelper.VERTEX_STRIDE; i++) {
                Vec3 vertex = BakedQuadHelper.getXYZ(vertices, i);
                vertex = vertex.add((double) slot.xOffset * 0.5, 0.0, (double) slot.yOffset * 0.5);
                vertex = VecHelper.rotateCentered(vertex, 180.0, Axis.Y);
                vertex = VecHelper.rotateCentered(vertex, (double) (xRot + 90.0F), Axis.X);
                vertex = VecHelper.rotateCentered(vertex, (double) yRot, Axis.Y);
                BakedQuadHelper.setXYZ(transformedVertices, i, vertex);
                BakedQuadHelper.setNormalXYZ(transformedVertices, i, new Vec3(0.0, 1.0, 0.0));
            }

            Direction newNormal = Direction.fromDelta((int) Math.round(quadNormal.x),
                (int) Math.round(quadNormal.y), (int) Math.round(quadNormal.z));
            quads.add(new BakedQuad(transformedVertices, bakedQuad.getTintIndex(), newNormal,
                bakedQuad.getSprite(), !ponder && bakedQuad.isShade()));
        }
    }
}
