package ca.dieterlunn.bridgewright;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shape of a bridge: every block to place and what kind of block it is.
 * <p>
 * The bridge is laid out along a line between the two deck positions. Positions along it are
 * sampled once per block ({@code k = 0..K}), and heights are worked out in half-block steps so
 * ramps use top and bottom slabs and never climb more than half a block per block.
 */
public final class BridgePlan {
    public enum Part { SLAB_BOTTOM, SLAB_TOP, FULL, FENCE }

    private static final int NONE = Integer.MIN_VALUE;
    private static final int MAX_DROP = 64;

    public final Map<BlockPos, Part> parts = new LinkedHashMap<>();
    public final BridgeType type;
    public final double length;
    /** Height change between the ends, in half blocks. */
    public final int halfSteps;
    /** The bridge couldn't be raised enough to leave the requested clearance over water. */
    public boolean clearanceShort;
    /** Translation key of a reason this bridge can't be built, or null. */
    @Nullable
    public String error;

    private BridgePlan(BridgeType type, double length, int halfSteps) {
        this.type = type;
        this.length = length;
        this.halfSteps = halfSteps;
    }

    /** True when the ends are so far apart in height that a ramp can't be walked up. */
    public boolean tooSteep() {
        return Math.abs(halfSteps) > Math.max(1, Math.round(length));
    }

    public int count(Part... kinds) {
        int n = 0;
        for (Part p : parts.values()) {
            for (Part k : kinds) if (p == k) n++;
        }
        return n;
    }

    /** One column of the bridge: a deck cell and everything above/below it. */
    private record Col(int x, int z, double s, int k, boolean edge) {
    }

    /**
     * @param a deck position at the first end (the block above the one clicked)
     * @param b deck position at the second end
     */
    public static BridgePlan compute(Level level, BlockPos a, BlockPos b, BridgeSettings settings) {
        BridgeType type = settings.type();
        int width = settings.width();
        int clearance = settings.clearance();

        double dx = b.getX() - a.getX();
        double dz = b.getZ() - a.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        BridgePlan plan = new BridgePlan(type, len, 2 * (b.getY() - a.getY()));
        if (type == BridgeType.SUSPENSION && width < 3) {
            plan.error = "message.bridgewright.suspension_width";
            return plan;
        }

        int bigK = Math.max(1, (int) Math.round(len));
        List<Col> cols = layoutColumns(a, b, width, len, bigK);

        // Heights along the bridge, in half blocks.
        int h0 = 2 * a.getY(), h1 = 2 * b.getY();
        int[] base = new int[bigK + 1];
        int[] target = new int[bigK + 1];
        double archRise = bigK / 3.0;
        boolean arch = type == BridgeType.ARCH || type == BridgeType.STONE_ARCH;
        for (int k = 0; k <= bigK; k++) {
            double f = (double) k / bigK;
            base[k] = (int) Math.round(h0 + (h1 - h0) * f);
            target[k] = base[k] + (arch ? (int) Math.round(archRise * 4 * f * (1 - f)) : 0);
        }

        // Where the bridge crosses water, and how high its underside must be there.
        int[] req = new int[bigK + 1];
        boolean[] water = new boolean[bigK + 1];
        java.util.Arrays.fill(req, NONE);
        for (Col c : cols) {
            int top = waterTop(level, c.x, Math.floorDiv(base[c.k], 2), c.z);
            if (top == NONE) continue;
            water[c.k] = true;
            if (clearance > 0) req[c.k] = Math.max(req[c.k], 2 * (top + clearance));
        }

        // Raise the deck to meet the clearance, ramping up and down at half a block per block.
        // A stone arch needs one more block under the deck for its crown.
        int extra = type == BridgeType.STONE_ARCH ? 2 : 0;
        int[] env = new int[bigK + 1];
        for (int k = 0; k <= bigK; k++) env[k] = req[k] == NONE ? NONE : req[k] + extra;
        for (int k = 1; k <= bigK; k++) if (env[k - 1] != NONE) env[k] = Math.max(env[k], env[k - 1] - 1);
        for (int k = bigK - 1; k >= 0; k--) if (env[k + 1] != NONE) env[k] = Math.max(env[k], env[k + 1] - 1);

        int[] deck = new int[bigK + 1];
        for (int k = 0; k <= bigK; k++) {
            deck[k] = Math.max(target[k], env[k]);
            // The ends stay where they were marked, so the ramps from them must stay walkable.
            if (!plan.tooSteep()) deck[k] = Math.min(deck[k], Math.min(h0 + k, h1 + (bigK - k)));
        }
        if (type != BridgeType.STONE_ARCH) {
            for (int k = 0; k <= bigK; k++) {
                if (req[k] != NONE && deck[k] < req[k]) plan.clearanceShort = true;
            }
        }

        // Deck and railings.
        for (Col c : cols) {
            int h = deck[c.k];
            BlockPos pos = new BlockPos(c.x, Math.floorDiv(h, 2), c.z);
            boolean top = Math.floorMod(h, 2) == 1;
            // A stone arch is solid, so a raised half step is a full block rather than a top slab.
            Part part = top ? (type == BridgeType.STONE_ARCH ? Part.FULL : Part.SLAB_TOP) : Part.SLAB_BOTTOM;
            plan.parts.put(pos, part);
            if (c.edge) plan.parts.put(pos.above(), Part.FENCE);
        }

        switch (type) {
            case STONE_ARCH -> addStoneArch(level, plan, cols, deck, req, bigK);
            case TRESTLE -> addTrestle(level, plan, cols, deck, water, clearance, width, bigK);
            case SUSPENSION -> addSuspension(plan, cols, deck, bigK);
            default -> {
            }
        }
        return plan;
    }

    /** Which columns make up the deck, and which of them are along its sides. */
    private static List<Col> layoutColumns(BlockPos a, BlockPos b, int width, double len, int bigK) {
        double ux = (b.getX() - a.getX()) / len, uz = (b.getZ() - a.getZ()) / len; // along the bridge
        double nx = -uz, nz = ux;                                                 // across the bridge
        double half = width / 2.0;

        int pad = width + 1;
        int minX = Math.min(a.getX(), b.getX()) - pad, maxX = Math.max(a.getX(), b.getX()) + pad;
        int minZ = Math.min(a.getZ(), b.getZ()) - pad, maxZ = Math.max(a.getZ(), b.getZ()) + pad;

        Map<Long, double[]> raw = new LinkedHashMap<>(); // packed xz -> {t, s}
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double cx = x - a.getX(), cz = z - a.getZ();
                double t = cx * ux + cz * uz;
                double s = cx * nx + cz * nz;
                if (t >= -0.5 && t <= len + 0.5 && Math.abs(s) <= half + 1e-6) {
                    raw.put(pack(x, z), new double[]{t, s});
                }
            }
        }

        List<Col> cols = new ArrayList<>(raw.size());
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (Map.Entry<Long, double[]> e : raw.entrySet()) {
            int x = (int) (e.getKey() >> 32), z = (int) (long) e.getKey();
            double t = e.getValue()[0], s = e.getValue()[1];
            int k = Math.max(0, Math.min(bigK, (int) Math.round(t / len * bigK)));
            boolean edge = false;
            if (width >= 3) {
                for (int[] d : dirs) {
                    int ox = x + d[0], oz = z + d[1];
                    if (raw.containsKey(pack(ox, oz))) continue;
                    double os = (ox - a.getX()) * nx + (oz - a.getZ()) * nz;
                    // Only gaps off to the side count; gaps past the ends are the entrances.
                    if (Math.abs(os) > half + 1e-6) {
                        edge = true;
                        break;
                    }
                }
            }
            cols.add(new Col(x, z, s, k, edge));
        }
        return cols;
    }

    /** Solid spandrels under the deck, curving down to meet the banks. */
    private static void addStoneArch(Level level, BridgePlan plan, List<Col> cols, int[] deck, int[] req, int bigK) {
        double archDepth = Math.max(3, bigK / 2.0);
        for (Col c : cols) {
            double f = (double) c.k / bigK;
            double u = 2 * f - 1;
            int depth = (int) Math.round((1 - Math.sqrt(Math.max(0, 1 - u * u))) * archDepth);
            int y = Math.floorDiv(deck[c.k], 2);
            int lowest = y;
            for (int yy = y - 1; yy >= y - 1 - depth && yy > y - MAX_DROP; yy--) {
                if (req[c.k] != NONE && 2 * yy < req[c.k]) break; // keep the opening clear for boats
                BlockPos p = new BlockPos(c.x, yy, c.z);
                if (level.isOutsideBuildHeight(p) || !level.getBlockState(p).canBeReplaced()) break;
                plan.parts.putIfAbsent(p, Part.FULL);
                lowest = yy;
            }
            if (req[c.k] != NONE && 2 * lowest < req[c.k]) plan.clearanceShort = true;
        }
    }

    /**
     * Every few blocks, a row of fences under the deck with legs down to the ground. Over water
     * with clearance on, legs are left out of a channel in the middle of the widest stretch.
     */
    private static void addTrestle(Level level, BridgePlan plan, List<Col> cols, int[] deck, boolean[] water,
                                   int clearance, int width, int bigK) {
        boolean[] channel = new boolean[bigK + 1];
        if (clearance > 0) {
            int bestStart = -1, bestLen = 0;
            for (int k = 0; k <= bigK; ) {
                if (!water[k]) {
                    k++;
                    continue;
                }
                int start = k;
                while (k <= bigK && water[k]) k++;
                if (k - start > bestLen) {
                    bestLen = k - start;
                    bestStart = start;
                }
            }
            if (bestLen > 0) {
                int cw = Math.max(4, bestLen / 3);
                int from = bestStart + (bestLen - cw) / 2;
                for (int k = from; k < from + cw; k++) {
                    if (k >= 0 && k <= bigK) channel[k] = true;
                }
            }
        }

        // Group columns by station so a leg is only built if the whole beam can be.
        Map<Integer, List<Col>> stations = new HashMap<>();
        for (Col c : cols) {
            if (c.k < 1 || c.k > bigK - 1 || channel[c.k]) continue;
            if (Math.floorMod(c.k - bigK / 2, 4) != 0) continue;
            stations.computeIfAbsent(c.k, k -> new ArrayList<>()).add(c);
        }
        for (List<Col> station : stations.values()) {
            for (Col c : station) {
                int y = Math.floorDiv(deck[c.k], 2);
                BlockPos beam = new BlockPos(c.x, y - 1, c.z);
                if (!level.getBlockState(beam).canBeReplaced()) continue;
                plan.parts.putIfAbsent(beam, Part.FENCE);

                boolean legColumn = c.edge || Math.abs(c.s) < 0.5 || width == 1;
                if (!legColumn) continue;
                List<BlockPos> leg = new ArrayList<>();
                boolean grounded = false;
                for (int yy = y - 2; yy > y - MAX_DROP; yy--) {
                    BlockPos p = new BlockPos(c.x, yy, c.z);
                    if (level.isOutsideBuildHeight(p)) break;
                    if (!level.getBlockState(p).canBeReplaced()) {
                        grounded = true;
                        break;
                    }
                    leg.add(p);
                }
                if (grounded) leg.forEach(p -> plan.parts.putIfAbsent(p, Part.FENCE));
            }
        }
    }

    /** Fence towers at both ends with cables sagging between them along each side, and hangers down to the railing. */
    private static void addSuspension(BridgePlan plan, List<Col> cols, int[] deck, int bigK) {
        int towerH = Math.max(5, Math.min(14, bigK / 6 + 4));
        for (Col c : cols) {
            if (!c.edge) continue;
            double f = (double) c.k / bigK;
            double u = 2 * f - 1;
            int top, low;
            if (c.k <= 1 || c.k >= bigK - 1) {
                top = towerH;
                low = 2;
            } else {
                top = 1 + (int) Math.round((towerH - 1) * u * u);
                // Fill down far enough to meet the next column's cable so the fences join up.
                double slope = (towerH - 1) * 4 * Math.abs(u) / bigK;
                low = c.k % 3 == 0 ? 2 : Math.max(2, top - (int) Math.ceil(slope));
            }
            int y = Math.floorDiv(deck[c.k], 2);
            for (int r = low; r <= top; r++) {
                plan.parts.putIfAbsent(new BlockPos(c.x, y + r, c.z), Part.FENCE);
            }
        }
    }

    /** The y just above the water surface in this column, or NONE if there's no water below. */
    private static int waterTop(Level level, int x, int startY, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = startY; y > startY - MAX_DROP; y--) {
            p.set(x, y, z);
            if (level.isOutsideBuildHeight(p)) return NONE;
            BlockState state = level.getBlockState(p);
            FluidState fluid = state.getFluidState();
            if (!fluid.isEmpty()) return fluid.is(FluidTags.WATER) ? y + 1 : NONE;
            if (state.isAir() || state.canBeReplaced() || state.is(Blocks.LILY_PAD)) continue;
            return NONE;
        }
        return NONE;
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
