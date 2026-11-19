package ca.dieterlunn.bridgewright;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Use on a block to mark the two ends of a bridge, then use on air to build it from slabs and
 * fences in your inventory.
 */
public class BridgeWandItem extends Item {
    private static final String POS_A = "PosA";
    private static final String POS_B = "PosB";
    private static final String DIM = "Dim";
    private static final String WIDTH = "Width";

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
            BridgePlan plan = BridgePlan.compute(a, deckPos, getWidth(stack));
            message(player, Component.translatable("message.bridgewright.pos_b", fmt(deckPos),
                    (int) Math.round(plan.length), plan.deck.size(), plan.railings.size()));
            if (player instanceof ServerPlayer sp) preview((ServerLevel) level, sp, plan);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);

        if (player.isShiftKeyDown()) {
            int next = getWidth(stack) + 2;
            if (next > maxWidth()) next = 1;
            stack.getOrCreateTag().putInt(WIDTH, next);
            message(player, Component.translatable("message.bridgewright.width", next));
            return InteractionResultHolder.success(stack);
        }

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
        BridgePlan plan = BridgePlan.compute(a, b, getWidth(stack));
        int maxLength = BridgewrightConfig.MAX_LENGTH.get();
        if (plan.length > maxLength) {
            message(player, Component.translatable("message.bridgewright.too_long",
                    (int) Math.round(plan.length), maxLength).withStyle(ChatFormatting.RED));
            return InteractionResultHolder.fail(stack);
        }

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
        tooltip.add(Component.translatable("tooltip.bridgewright.width", getWidth(stack)).withStyle(ChatFormatting.GRAY));
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

    /** Largest odd width allowed by the config. */
    private static int maxWidth() {
        int max = BridgewrightConfig.MAX_WIDTH.get();
        return max % 2 == 0 ? max - 1 : max;
    }

    public static int getWidth(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        int w = tag != null && tag.contains(WIDTH, Tag.TAG_INT) ? tag.getInt(WIDTH) : 3;
        w = Math.max(1, Math.min(w, maxWidth()));
        return w % 2 == 0 ? w - 1 : w;
    }

    private static void clear(CompoundTag tag) {
        tag.remove(POS_A);
        tag.remove(POS_B);
        tag.remove(DIM);
    }

    /** Shows the planned deck with particles: green for walkway, white for railing posts. */
    private static void preview(ServerLevel level, ServerPlayer player, BridgePlan plan) {
        int shown = 0;
        for (Map.Entry<BlockPos, SlabType> e : plan.deck.entrySet()) {
            if (shown++ > 400) break;
            BlockPos p = e.getKey();
            double y = p.getY() + (e.getValue() == SlabType.TOP ? 1.05 : 0.55);
            boolean rail = plan.railings.contains(p.above());
            level.sendParticles(player, rail ? ParticleTypes.END_ROD : ParticleTypes.HAPPY_VILLAGER, true,
                    p.getX() + 0.5, y, p.getZ() + 0.5, 1, 0, 0, 0, 0);
        }
    }

    static Component fmt(BlockPos p) {
        return Component.literal(p.getX() + ", " + p.getY() + ", " + p.getZ());
    }

    static void message(Player player, Component msg) {
        player.displayClientMessage(msg, true);
    }
}
