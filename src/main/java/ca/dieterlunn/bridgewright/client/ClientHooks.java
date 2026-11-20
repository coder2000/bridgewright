package ca.dieterlunn.bridgewright.client;

import ca.dieterlunn.bridgewright.BridgeSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

/** Client-only entry points, kept separate so the dedicated server never loads client classes. */
public final class ClientHooks {
    private ClientHooks() {
    }

    public static void openWandScreen(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.setScreen(new BridgeWandScreen(hand, BridgeSettings.of(mc.player.getItemInHand(hand))));
    }
}
