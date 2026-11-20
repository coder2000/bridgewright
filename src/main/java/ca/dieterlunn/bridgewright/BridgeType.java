package ca.dieterlunn.bridgewright;

import net.minecraft.network.chat.Component;

public enum BridgeType {
    /** Straight deck between the two points. */
    FLAT("flat"),
    /** Deck curves up to a high point in the middle. */
    ARCH("arch"),
    /** Arch with a solid masonry underside, using full blocks that match the slabs. */
    STONE_ARCH("stone_arch"),
    /** Deck held up by fence legs down to the ground or riverbed. */
    TRESTLE("trestle"),
    /** Deck hung from fence cables between towers at each end. */
    SUSPENSION("suspension");

    public final String id;

    BridgeType(String id) {
        this.id = id;
    }

    /** Trestle legs and suspension cables can't be built without fences. */
    public boolean needsFences() {
        return this == TRESTLE || this == SUSPENSION;
    }

    public Component displayName() {
        return Component.translatable("bridgewright.type." + id);
    }

    public Component description() {
        return Component.translatable("bridgewright.type." + id + ".desc");
    }

    public static BridgeType byId(String id) {
        for (BridgeType t : values()) {
            if (t.id.equals(id)) return t;
        }
        return FLAT;
    }
}
