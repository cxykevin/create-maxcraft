package dev.maxcraft.network;

import dev.maxcraft.Maxcraft;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Payload registration.
 */
@EventBusSubscriber(modid = Maxcraft.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class MaxcraftNetwork {

    private MaxcraftNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
            .playToServer(GridSizePacket.TYPE, GridSizePacket.STREAM_CODEC, GridSizePacket::handle);
    }
}
