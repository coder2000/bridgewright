package ca.dieterlunn.bridgewright;

import net.minecraftforge.common.ForgeConfigSpec;

public final class BridgewrightConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue MAX_LENGTH;
    public static final ForgeConfigSpec.IntValue MAX_WIDTH;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        MAX_LENGTH = b.comment("Longest bridge (horizontal distance between the two points, in blocks).")
                .defineInRange("maxLength", 128, 2, 1024);
        MAX_WIDTH = b.comment("Widest bridge the wand can be set to, including railings. Widths are odd: 1, 3, 5, ...")
                .defineInRange("maxWidth", 7, 1, 15);
        SPEC = b.build();
    }

    private BridgewrightConfig() {
    }
}
