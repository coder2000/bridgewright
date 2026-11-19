package ca.dieterlunn.bridgewright;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Places a {@link BridgePlan} using materials from the player's inventory. */
public final class BridgeBuilder {
    private BridgeBuilder() {
    }

    /** @return true if anything was built */
    public static boolean build(ServerLevel level, ServerPlayer player, BridgePlan plan) {
        boolean creative = player.getAbilities().instabuild;
        if (!player.mayBuild()) return false;

        Item slab = findItem(player, BridgeBuilder::isSlab);
        Item fence = findItem(player, BridgeBuilder::isRailing);
        if (slab == null) {
            if (!creative) {
                error(player, Component.translatable("message.bridgewright.no_slabs"));
                return false;
            }
            slab = Items.OAK_SLAB;
        }
        if (fence == null && creative) fence = Items.OAK_FENCE;

        // Work out what actually needs placing; anything solid already in the way is left alone.
        List<BlockPos> deck = new ArrayList<>();
        for (BlockPos p : plan.deck.keySet()) {
            if (canPlace(level, player, p)) deck.add(p);
        }
        List<BlockPos> rails = new ArrayList<>();
        if (fence != null) {
            for (BlockPos p : plan.railings) {
                if (canPlace(level, player, p)) rails.add(p);
            }
        }

        if (!creative) {
            int haveSlabs = count(player, slab);
            int haveFences = fence == null ? 0 : count(player, fence);
            if (haveSlabs < deck.size() || haveFences < rails.size()) {
                error(player, Component.translatable("message.bridgewright.missing",
                        deck.size(), slab.getDescription(), haveSlabs,
                        rails.size(),
                        fence == null ? Component.translatable("message.bridgewright.fences") : fence.getDescription(),
                        haveFences));
                return false;
            }
        }

        BlockState slabBase = ((BlockItem) slab).getBlock().defaultBlockState();
        int placedSlabs = 0, placedFences = 0;
        for (BlockPos p : deck) {
            BlockState state = slabBase.setValue(SlabBlock.TYPE, plan.deck.get(p));
            if (place(level, player, p, state)) {
                placedSlabs++;
                if (!creative) consume(player, slab);
            }
        }
        if (fence != null) {
            Block fenceBlock = ((BlockItem) fence).getBlock();
            for (BlockPos p : rails) {
                // Connect to railings already placed; later neighbours update this one as they go down.
                BlockState state = Block.updateFromNeighbourShapes(fenceBlock.defaultBlockState(), level, p);
                if (place(level, player, p, state)) {
                    placedFences++;
                    if (!creative) consume(player, fence);
                }
            }
        }

        if (placedSlabs + placedFences == 0) {
            error(player, Component.translatable("message.bridgewright.nothing"));
            return false;
        }

        BlockPos mid = deck.isEmpty() ? player.blockPosition() : deck.get(deck.size() / 2);
        SoundType sound = slabBase.getSoundType();
        level.playSound(null, mid, sound.getPlaceSound(), SoundSource.BLOCKS, 1.0f, sound.getPitch() * 0.8f);

        Component done = (!plan.railings.isEmpty() && fence == null)
                ? Component.translatable("message.bridgewright.built_no_rails", placedSlabs)
                : Component.translatable("message.bridgewright.built", placedSlabs, placedFences);
        player.displayClientMessage(done.copy().withStyle(ChatFormatting.GREEN), true);
        if (plan.tooSteep()) {
            player.displayClientMessage(Component.translatable("message.bridgewright.steep").withStyle(ChatFormatting.YELLOW), false);
        }
        return true;
    }

    private static boolean isSlab(ItemStack s) {
        return s.getItem() instanceof BlockItem bi && bi.getBlock() instanceof SlabBlock;
    }

    /** Fences, walls, glass panes and iron bars all work as railings. */
    private static boolean isRailing(ItemStack s) {
        return s.getItem() instanceof BlockItem bi
                && (bi.getBlock() instanceof CrossCollisionBlock || bi.getBlock() instanceof WallBlock);
    }

    /** The first matching item, checking the offhand, then the hotbar, then the rest of the inventory. */
    private static Item findItem(ServerPlayer player, Predicate<ItemStack> test) {
        Inventory inv = player.getInventory();
        ItemStack off = inv.offhand.get(0);
        if (!off.isEmpty() && test.test(off)) return off.getItem();
        for (ItemStack s : inv.items) {
            if (!s.isEmpty() && test.test(s)) return s.getItem();
        }
        return null;
    }

    private static int count(ServerPlayer player, Item item) {
        Inventory inv = player.getInventory();
        int n = 0;
        for (ItemStack s : inv.items) if (s.is(item)) n += s.getCount();
        for (ItemStack s : inv.offhand) if (s.is(item)) n += s.getCount();
        return n;
    }

    /** Takes one item, from the offhand first, then from the back of the main inventory. */
    private static void consume(ServerPlayer player, Item item) {
        Inventory inv = player.getInventory();
        for (ItemStack s : inv.offhand) {
            if (s.is(item)) {
                s.shrink(1);
                return;
            }
        }
        for (int i = inv.items.size() - 1; i >= 0; i--) {
            ItemStack s = inv.items.get(i);
            if (s.is(item)) {
                s.shrink(1);
                return;
            }
        }
    }

    private static boolean canPlace(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!level.isInWorldBounds(pos) || !level.mayInteract(player, pos)) return false;
        return level.getBlockState(pos).canBeReplaced();
    }

    private static boolean place(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        FluidState fluid = level.getFluidState(pos);
        if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            state = state.setValue(BlockStateProperties.WATERLOGGED, fluid.getType() == Fluids.WATER && fluid.isSource());
        }
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        if (!level.setBlock(pos, state, Block.UPDATE_ALL)) return false;
        // Let claim/protection mods veto the placement.
        if (ForgeEventFactory.onBlockPlace(player, snapshot, Direction.UP)) {
            snapshot.restore(true, false);
            return false;
        }
        return true;
    }

    private static void error(ServerPlayer player, Component msg) {
        player.displayClientMessage(msg.copy().withStyle(ChatFormatting.RED), true);
    }
}
