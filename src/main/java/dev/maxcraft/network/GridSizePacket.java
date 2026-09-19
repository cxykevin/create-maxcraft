package dev.maxcraft.network;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;

import dev.maxcraft.Maxcraft;
import dev.maxcraft.content.logistics.GridSizeHolder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Tells the server that a factory gauge's crafting grid size changed.
 *
 * <p>Create syncs the rest of a panel's configuration through its own packet; this is the one extra field.
 */
public record GridSizePacket(FactoryPanelPosition panel, int size) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GridSizePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Maxcraft.MOD_ID, "grid_size"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GridSizePacket> STREAM_CODEC = StreamCodec.composite(
        FactoryPanelPosition.STREAM_CODEC.cast(), GridSizePacket::panel,
        ByteBufCodecs.VAR_INT, GridSizePacket::size,
        GridSizePacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GridSizePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;

            FactoryPanelBehaviour behaviour = FactoryPanelBehaviour.at(player.serverLevel(), packet.panel());
            if (behaviour == null)
                return;

            ((GridSizeHolder) behaviour).maxcraft$setGridSize(packet.size());
            if (behaviour.blockEntity instanceof SmartBlockEntity smart)
                smart.notifyUpdate();
        });
    }
}
