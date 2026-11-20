package ca.dieterlunn.bridgewright.network;

import ca.dieterlunn.bridgewright.BridgeSettings;
import ca.dieterlunn.bridgewright.BridgeType;
import ca.dieterlunn.bridgewright.BridgeWandItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Sent when the wand's settings screen closes, to save the settings onto the wand. */
public record SetWandSettingsPacket(InteractionHand hand, BridgeSettings settings) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(hand);
        buf.writeEnum(settings.type());
        buf.writeVarInt(settings.width());
        buf.writeVarInt(settings.clearance());
    }

    public static SetWandSettingsPacket decode(FriendlyByteBuf buf) {
        InteractionHand hand = buf.readEnum(InteractionHand.class);
        BridgeType type = buf.readEnum(BridgeType.class);
        int width = buf.readVarInt();
        int clearance = buf.readVarInt();
        return new SetWandSettingsPacket(hand, new BridgeSettings(type, width, clearance));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof BridgeWandItem) {
            settings.sanitized().write(stack);
        }
    }
}
