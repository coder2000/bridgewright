package ca.dieterlunn.bridgewright;

import ca.dieterlunn.bridgewright.BridgePlan.Part;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Places a {@link BridgePlan} using materials from the player's inventory. */
public final class BridgeBuilder {
    private BridgeBuilder() {
    }

    /** @return true if anything was built */
    public static boolean build(ServerLevel level, ServerPlayer player, BridgePlan plan) {
        if (plan.error != null) {
            error(player, Component.translatable(plan.error));
            return false;
        }
        boolean creative = player.getAbilities().instabuild;
        if (!player.mayBuild()) return false;

        // Pick materials.
        Item slab = findItem(player, BridgeBuilder::isSlab);
        if (slab == null) {
            if (!creative) {
                error(player, Component.translatable("message.bridgewright.no_slabs"));
                return false;
            }
            slab = Items.OAK_SLAB;
        }
        Item fence = findItem(player, BridgeBuilder::isRailing);
        if (fence == null && creative) fence = Items.OAK_FENCE;
        if (fence == null && plan.type.needsFences()) {
            error(player, Component.translatable("message.bridgewright.needs_fences", plan.type.displayName()));
            return false;
        }
        Item full = null;
        if (plan.count(Part.FULL) > 0) {
            full = matchingFullBlock(player, slab, creative);
            if (full == null) {
                error(player, Component.translatable("message.bridgewright.no_full_block", slab.getDescription()));
                return false;
            }
        }

        // Stairs smooth the underside of a stone arch. They're optional: without any, it's left stepped.
        Item stairs = plan.count(Part.STAIR) > 0 ? pickStairs(player, slab, creative) : null;

        // Work out what actually needs placing; anything solid already in the way is left alone.
        Map<Part, List<BlockPos>> todo = new EnumMap<>(Part.class);
        for (Part p : Part.values()) todo.put(p, new ArrayList<>());
        for (Map.Entry<BlockPos, Part> e : plan.parts.entrySet()) {
            if (e.getValue() == Part.FENCE && fence == null) continue; // no fences: no railings
            if (e.getValue() == Part.STAIR && stairs == null) continue;
            if (canPlace(level, player, e.getKey())) todo.get(e.getValue()).add(e.getKey());
        }
        int needSlabs = todo.get(Part.SLAB_BOTTOM).size() + todo.get(Part.SLAB_TOP).size();
        int needFull = todo.get(Part.FULL).size();
        int needStairs = todo.get(Part.STAIR).size();
        int needFences = todo.get(Part.FENCE).size();

        if (!creative) {
            MutableComponent missing = Component.empty();
            boolean any = false;
            any |= addShortage(missing, any, player, slab, needSlabs);
            if (full != null) any |= addShortage(missing, any, player, full, needFull);
            if (stairs != null) any |= addShortage(missing, any, player, stairs, needStairs);
            if (fence != null) any |= addShortage(missing, any, player, fence, needFences);
            if (any) {
                error(player, Component.translatable("message.bridgewright.missing", missing));
                return false;
            }
        }

        // Place: masonry first, then the deck, then fences so they join up with what's there.
        int placed = 0;
        Block slabBlock = ((BlockItem) slab).getBlock();
        if (full != null) {
            BlockState state = ((BlockItem) full).getBlock().defaultBlockState();
            List<BlockPos> fills = todo.get(Part.FULL);
            fills.sort((p, q) -> Integer.compare(p.getY(), q.getY()));
            placed += placeAll(level, player, fills, p -> state, creative ? null : full);
        }
        if (stairs != null) {
            Block stairBlock = ((BlockItem) stairs).getBlock();
            placed += placeAll(level, player, todo.get(Part.STAIR), p -> {
                BlockState s = stairBlock.defaultBlockState()
                        .setValue(StairBlock.FACING, plan.stairFacing.getOrDefault(p, Direction.NORTH))
                        .setValue(StairBlock.HALF, Half.TOP);
                return Block.updateFromNeighbourShapes(s, level, p);
            }, creative ? null : stairs);
        }
        placed += placeAll(level, player, todo.get(Part.SLAB_BOTTOM),
                p -> slabBlock.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), creative ? null : slab);
        placed += placeAll(level, player, todo.get(Part.SLAB_TOP),
                p -> slabBlock.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), creative ? null : slab);
        if (fence != null) {
            Block fenceBlock = ((BlockItem) fence).getBlock();
            List<BlockPos> fences = todo.get(Part.FENCE);
            fences.sort((p, q) -> Integer.compare(p.getY(), q.getY()));
            placed += placeAll(level, player, fences,
                    p -> Block.updateFromNeighbourShapes(fenceBlock.defaultBlockState(), level, p), creative ? null : fence);
        }

        if (placed == 0) {
            error(player, Component.translatable("message.bridgewright.nothing"));
            return false;
        }

        List<BlockPos> deck = todo.get(Part.SLAB_BOTTOM);
        BlockPos mid = deck.isEmpty() ? player.blockPosition() : deck.get(deck.size() / 2);
        SoundType sound = slabBlock.defaultBlockState().getSoundType();
        level.playSound(null, mid, sound.getPlaceSound(), SoundSource.BLOCKS, 1.0f, sound.getPitch() * 0.8f);

        boolean noRails = fence == null && plan.count(Part.FENCE) > 0;
        Component done = noRails
                ? Component.translatable("message.bridgewright.built_no_rails", plan.type.displayName(), placed)
                : Component.translatable("message.bridgewright.built", plan.type.displayName(), placed);
        player.displayClientMessage(done.copy().withStyle(ChatFormatting.GREEN), true);
        if (plan.tooSteep()) {
            player.displayClientMessage(Component.translatable("message.bridgewright.steep").withStyle(ChatFormatting.YELLOW), false);
        }
        if (plan.clearanceShort) {
            player.displayClientMessage(Component.translatable("message.bridgewright.clearance_short").withStyle(ChatFormatting.YELLOW), false);
        }
        return true;
    }

    private interface StateFor {
        BlockState at(BlockPos pos);
    }

    private static int placeAll(ServerLevel level, ServerPlayer player, List<BlockPos> positions, StateFor state,
                                @Nullable Item consume) {
        int n = 0;
        for (BlockPos p : positions) {
            if (place(level, player, p, state.at(p))) {
                n++;
                if (consume != null) consume(player, consume);
            }
        }
        return n;
    }

    private static boolean addShortage(MutableComponent out, boolean notFirst, ServerPlayer player, Item item, int need) {
        int have = count(player, item);
        if (have >= need) return false;
        if (notFirst) out.append(", ");
        out.append(Component.translatable("message.bridgewright.shortage", need, item.getDescription(), have));
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

    /**
     * The full block a slab is cut from, by name: stone brick slab → stone bricks, oak slab → oak
     * planks, quartz slab → quartz block, and so on. Prefers one the player is carrying.
     */
    @Nullable
    private static Item matchingFullBlock(ServerPlayer player, Item slab, boolean creative) {
        List<Item> candidates = fullBlockCandidates(slab);
        for (Item c : candidates) {
            if (count(player, c) > 0) return c;
        }
        if (!candidates.isEmpty()) return candidates.get(0); // creative uses it; survival is told they need it
        return creative ? Items.STONE_BRICKS : null;
    }

    /**
     * Stairs for the arch underside: ones matching the slabs if carried (stone brick slabs → stone
     * brick stairs), otherwise the first stairs in the inventory. Creative falls back to the match.
     */
    @Nullable
    private static Item pickStairs(ServerPlayer player, Item slab, boolean creative) {
        Item match = null;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(slab);
        if (id != null && id.getPath().endsWith("_slab")) {
            String path = id.getPath();
            ResourceLocation rl = ResourceLocation.tryBuild(id.getNamespace(), path.substring(0, path.length() - 5) + "_stairs");
            Item item = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
            if (item instanceof BlockItem bi && bi.getBlock() instanceof StairBlock) match = item;
        }
        if (match != null && count(player, match) > 0) return match;
        Item carried = findItem(player, s -> s.getItem() instanceof BlockItem bi && bi.getBlock() instanceof StairBlock);
        if (carried != null) return carried;
        return creative ? match : null;
    }

    private static List<Item> fullBlockCandidates(Item slab) {
        List<Item> out = new ArrayList<>();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(slab);
        if (id == null) return out;
        String path = id.getPath();
        String base = path.endsWith("_slab") ? path.substring(0, path.length() - 5) : path.replace("_slab", "");
        String[] names = {base, base + "s", base + "_planks", base + "_block",
                base.replace("_brick", "_bricks"), base.replace("_tile", "_tiles")};
        for (String ns : new String[]{id.getNamespace(), "minecraft"}) {
            for (String name : names) {
                ResourceLocation rl = ResourceLocation.tryBuild(ns, name);
                if (rl == null) continue;
                Item item = ForgeRegistries.ITEMS.getValue(rl);
                if (item instanceof BlockItem bi && item != Items.AIR && !(bi.getBlock() instanceof SlabBlock)
                        && !out.contains(item)) {
                    out.add(item);
                }
            }
        }
        return out;
    }

    /** The first matching item, checking the offhand, then the hotbar, then the rest of the inventory. */
    @Nullable
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
