package ca.dieterlunn.bridgewright.client;

import ca.dieterlunn.bridgewright.BridgeSettings;
import ca.dieterlunn.bridgewright.BridgeType;
import ca.dieterlunn.bridgewright.network.BridgewrightNetwork;
import ca.dieterlunn.bridgewright.network.SetWandSettingsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

import java.util.ArrayList;
import java.util.List;

/** Pick the bridge type, width and boat clearance. Settings are saved to the wand when the screen closes. */
public class BridgeWandScreen extends Screen {
    private static final int W = 200;

    private final InteractionHand hand;
    private BridgeType type;
    private int bridgeWidth;
    private int clearance;

    private int top;

    public BridgeWandScreen(InteractionHand hand, BridgeSettings settings) {
        super(Component.translatable("screen.bridgewright.title"));
        this.hand = hand;
        this.type = settings.type();
        this.bridgeWidth = settings.width();
        this.clearance = settings.clearance();
    }

    @Override
    protected void init() {
        int x = (this.width - W) / 2;
        top = this.height / 2 - 70;

        addRenderableWidget(CycleButton.<BridgeType>builder(BridgeType::displayName)
                .withValues(BridgeType.values())
                .withInitialValue(type)
                .create(x, top, W, 20, Component.translatable("screen.bridgewright.type"), (b, v) -> type = v));

        List<Integer> widths = new ArrayList<>();
        for (int w = 1; w <= BridgeSettings.maxWidth(); w += 2) widths.add(w);
        if (!widths.contains(bridgeWidth)) bridgeWidth = widths.get(widths.size() - 1);
        addRenderableWidget(CycleButton.<Integer>builder(v -> Component.literal(Integer.toString(v)))
                .withValues(widths)
                .withInitialValue(bridgeWidth)
                .create(x, top + 24, W, 20, Component.translatable("screen.bridgewright.width"), (b, v) -> bridgeWidth = v));

        List<Integer> clearances = new ArrayList<>();
        for (int c = 0; c <= BridgeSettings.MAX_CLEARANCE; c++) clearances.add(c);
        addRenderableWidget(CycleButton.<Integer>builder(v -> v == 0
                        ? Component.translatable("screen.bridgewright.off")
                        : Component.translatable("screen.bridgewright.blocks", v))
                .withValues(clearances)
                .withInitialValue(clearance)
                .create(x, top + 48, W, 20, Component.translatable("screen.bridgewright.clearance"), (b, v) -> clearance = v));

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(x, top + 130, W, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, title, this.width / 2, top - 20, 0xFFFFFF);
        int x = (this.width - W) / 2;
        g.drawWordWrap(font, type.description(), x, top + 76, W, 0xC0C0C0);
        if (clearance > 0) {
            g.drawWordWrap(font, Component.translatable("screen.bridgewright.clearance_hint", clearance),
                    x, top + 108, W, 0x808080);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        BridgewrightNetwork.CHANNEL.sendToServer(
                new SetWandSettingsPacket(hand, new BridgeSettings(type, bridgeWidth, clearance)));
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
