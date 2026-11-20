package ca.dieterlunn.bridgewright;

import ca.dieterlunn.bridgewright.BridgePlan.Part;
import ca.dieterlunn.bridgewright.client.ClientHooks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Use on a block to mark the two ends of a bridge, then use in the air to build it from slabs and
 * fences in your inventory. Sneak + use in the air opens the settings screen.
 */
public class BridgeWandItem extends Item {
    private static final String POS_A = "PosA";
    private static final String POS_B = "PosB";
    private static final String DIM = "Dim";
    private static final int MAX_PREVIEW_PARTICLES = 600;

    public BridgeWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;

        ItemStack stack = ctx.getItemInHand();
        CompoundTag tag = stack.getOrCreateTag();

        if (player.isShiftKeyDown()) {
            clear(tag);
            message(player, Component.translatable("message.bridgewright.cleared"));
            return InteractionResult.SUCCESS;
        }

        // The deck sits on top of the clicked block.
        BlockPos deckPos = ctx.getClickedPos().above();
        String dim = level.dimension().location().toString();
        boolean sameDim = dim.equals(tag.getString(DIM));

        if (!tag.contains(POS_A, Tag.TAG_LONG) || tag.contains(POS_B, Tag.TAG_LONG) || !sameDim) {
            clear(tag);
            tag.putLong(POS_A, deckPos.asLong());
            tag.putString(DIM, dim);
            message(player, Component.translatable("message.bridgewright.pos_a", fmt(deckPos)));
        } else {
            BlockPos a = BlockPos.of(tag.getLong(POS_A));
            if (a.getX() == deckPos.getX() && a.getZ() == deckPos.getZ()) {
                message(player, Component.translatable("message.bridgewright.same_spot").withStyle(ChatFormatting.RED));
                return InteractionResult.SUCCESS;
            }
            tag.putLong(POS_B, deckPos.asLong());
            double dx = deckPos.getX() - a.getX(), dz = deckPos.getZ() - a.getZ();
            int maxLength = BridgewrightConfig.MAX_LENGTH.get();
            if (Math.sqrt(dx * dx + dz * dz) > maxLength) {
                message(player, Component.translatable("message.bridgewright.too_long",
                        (int) Math.round(Math.sqrt(dx * dx + dz * dz)), maxLength).withStyle(ChatFormatting.RED));
                return InteractionResult.SUCCESS;
            }
            BridgeSettings settings = BridgeSettings.of(stack);
            BridgePlan plan = BridgePlan.compute(level, a, deckPos, settings);
            if (plan.error != null) {
                message(player, Component.translatable(plan.error).withStyle(ChatFormatting.RED));
            } else {
                message(player, Component.translatable("message.bridgewright.pos_b", fmt(deckPos),
                        settings.type().displayName(), (int) Math.round(plan.length), materials(plan)));
                if (player instanceof ServerPlayer sp) preview((ServerLevel) level, sp, plan);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.isShiftKeyDown()) {
            if (level.isClientSide) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openWandScreen(hand));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (level.isClientSide) return InteractionResultHolder.success(stack);

        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(POS_A, Tag.TAG_LONG) || !tag.contains(POS_B, Tag.TAG_LONG)) {
            message(player, Component.translatable("message.bridgewright.need_points").withStyle(ChatFormatting.YELLOW));
            return InteractionResultHolder.fail(stack);
        }
        if (!level.dimension().location().toString().equals(tag.getString(DIM))) {
            message(player, Component.translatable("message.bridgewright.wrong_dimension").withStyle(ChatFormatting.RED));
            return InteractionResultHolder.fail(stack);
        }

        BlockPos a = BlockPos.of(tag.getLong(POS_A));
        BlockPos b = BlockPos.of(tag.getLong(POS_B));
        int maxLength = BridgewrightConfig.MAX_LENGTH.get();
        double dx = b.getX() - a.getX(), dz = b.getZ() - a.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length > maxLength) {
            message(player, Component.translatable("message.bridgewright.too_long",
                    (int) Math.round(length), maxLength).withStyle(ChatFormatting.RED));
            return InteractionResultHolder.fail(stack);
        }

        // Plan against the world as it is now, with the wand's current settings.
        BridgePlan plan = BridgePlan.compute(level, a, b, BridgeSettings.of(stack));
        if (BridgeBuilder.build((ServerLevel) level, (ServerPlayer) player, plan)) {
            clear(tag);
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.fail(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(POS_A, Tag.TAG_LONG) && tag.contains(POS_B, Tag.TAG_LONG);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        BridgeSettings s = BridgeSettings.of(stack);
        tooltip.add(Component.translatable("tooltip.bridgewright.settings", s.type().displayName(), s.width(),
                s.clearance() == 0 ? Component.translatable("screen.bridgewright.off")
                        : Component.translatable("screen.bridgewright.blocks", s.clearance()))
                .withStyle(ChatFormatting.GRAY));
        if (tag != null && tag.contains(POS_A, Tag.TAG_LONG)) {
            tooltip.add(Component.translatable("tooltip.bridgewright.pos_a", fmt(BlockPos.of(tag.getLong(POS_A)))).withStyle(ChatFormatting.GRAY));
        }
        if (tag != null && tag.contains(POS_B, Tag.TAG_LONG)) {
            tooltip.add(Component.translatable("tooltip.bridgewright.pos_b", fmt(BlockPos.of(tag.getLong(POS_B)))).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.bridgewright.help1").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.bridgewright.help2").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.bridgewright.help3").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** "12 slabs, 30 fences, 40 blocks" (only the materials this bridge uses). */
    private static Component materials(BridgePlan plan) {
        int slabs = plan.count(Part.SLAB_BOTTOM, Part.SLAB_TOP);
        int fences = plan.count(Part.FENCE);
        int full = plan.count(Part.FULL);
        int stairs = plan.count(Part.STAIR);
        return full > 0
                ? Component.translatable("message.bridgewright.materials_full", slabs, fences, full, stairs)
                : Component.translatable("message.bridgewright.materials", slabs, fences);
    }

    private static void clear(CompoundTag tag) {
        tag.remove(POS_A);
        tag.remove(POS_B);
        tag.remove(DIM);
    }

    /** Shows the planned bridge with particles: green for the deck, white for fences, sparks for masonry. */
    private static void preview(ServerLevel level, ServerPlayer player, BridgePlan plan) {
        int every = Math.max(1, plan.parts.size() / MAX_PREVIEW_PARTICLES + 1);
        int i = 0;
        for (Map.Entry<BlockPos, Part> e : plan.parts.entrySet()) {
            if (i++ % every != 0) continue;
            BlockPos p = e.getKey();
            ParticleOptions particle;
            double y;
            switch (e.getValue()) {
                case SLAB_BOTTOM -> { particle = ParticleTypes.HAPPY_VILLAGER; y = 0.55; }
                case SLAB_TOP -> { particle = ParticleTypes.HAPPY_VILLAGER; y = 1.05; }
                case FULL, STAIR -> { particle = ParticleTypes.CRIT; y = 0.5; }
                default -> { particle = ParticleTypes.END_ROD; y = 0.5; }
            }
            level.sendParticles(player, particle, true, p.getX() + 0.5, p.getY() + y, p.getZ() + 0.5, 1, 0, 0, 0, 0);
        }
    }

    static Component fmt(BlockPos p) {
        return Component.literal(p.getX() + ", " + p.getY() + ", " + p.getZ());
    }

    static void message(Player player, Component msg) {
        player.displayClientMessage(msg, true);
    }
}
