package ca.dieterlunn.bridgewright;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * What the wand builds, stored on the wand.
 *
 * @param width     odd width in blocks, including railings
 * @param clearance blocks of air to leave between water and the underside of the bridge (0 = off)
 */
public record BridgeSettings(BridgeType type, int width, int clearance) {
    public static final int MAX_CLEARANCE = 8;
    public static final BridgeSettings DEFAULT = new BridgeSettings(BridgeType.FLAT, 3, 2);

    private static final String TYPE = "Type";
    private static final String WIDTH = "Width";
    private static final String CLEARANCE = "Clearance";

    public static BridgeSettings of(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return DEFAULT.sanitized();
        BridgeType type = tag.contains(TYPE, Tag.TAG_STRING) ? BridgeType.byId(tag.getString(TYPE)) : DEFAULT.type;
        int width = tag.contains(WIDTH, Tag.TAG_INT) ? tag.getInt(WIDTH) : DEFAULT.width;
        int clearance = tag.contains(CLEARANCE, Tag.TAG_INT) ? tag.getInt(CLEARANCE) : DEFAULT.clearance;
        return new BridgeSettings(type, width, clearance).sanitized();
    }

    public void write(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(TYPE, type.id);
        tag.putInt(WIDTH, width);
        tag.putInt(CLEARANCE, clearance);
    }

    public BridgeSettings sanitized() {
        int w = Math.max(1, Math.min(width, maxWidth()));
        if (w % 2 == 0) w--;
        int c = Math.max(0, Math.min(clearance, MAX_CLEARANCE));
        return new BridgeSettings(type == null ? BridgeType.FLAT : type, w, c);
    }

    /** Largest odd width allowed by the config. */
    public static int maxWidth() {
        int max = BridgewrightConfig.MAX_WIDTH.get();
        return max % 2 == 0 ? max - 1 : max;
    }
}
