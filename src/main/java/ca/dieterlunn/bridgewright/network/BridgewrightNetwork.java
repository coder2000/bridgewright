package ca.dieterlunn.bridgewright.network;

import ca.dieterlunn.bridgewright.Bridgewright;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class BridgewrightNetwork {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Bridgewright.MOD_ID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private BridgewrightNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(SetWandSettingsPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetWandSettingsPacket::encode)
                .decoder(SetWandSettingsPacket::decode)
                .consumerMainThread(SetWandSettingsPacket::handle)
                .add();
    }
}
