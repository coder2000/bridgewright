package ca.dieterlunn.bridgewright;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The shape of a bridge between two deck positions: which cells get a slab (and whether it is a
 * top or bottom slab), and which cells get a railing on top of the deck.
 * <p>
 * Heights are worked out in half-block steps so a bridge between two different heights ramps
 * smoothly using top and bottom slabs.
 */
public final class BridgePlan {
    public final Map<BlockPos, SlabType> deck = new LinkedHashMap<>();
    public final Set<BlockPos> railings = new LinkedHashSet<>();
    public final double length;
    /** Height change in half blocks. */
    public final int halfSteps;

    private BridgePlan(double length, int halfSteps) {
        this.length = length;
        this.halfSteps = halfSteps;
    }

    /** True when the ramp rises more than half a block per block, so it can't be walked up. */
    public boolean tooSteep() {
        return Math.abs(halfSteps) > Math.ceil(length);
    }

    /**
     * @param a     deck position at the first end (the block above the one clicked)
     * @param b     deck position at the second end
     * @param width odd width in blocks, including railings
     */
    public static BridgePlan compute(BlockPos a, BlockPos b, int width) {
        double dx = b.getX() - a.getX();
        double dz = b.getZ() - a.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        int h0 = a.getY() * 2;
        int h1 = b.getY() * 2;
        BridgePlan plan = new BridgePlan(len, h1 - h0);

        double ux = dx / len, uz = dz / len;     // along the bridge
        double nx = -uz, nz = ux;                // across the bridge
        double half = width / 2.0;

        int pad = width + 1;
        int minX = Math.min(a.getX(), b.getX()) - pad, maxX = Math.max(a.getX(), b.getX()) + pad;
        int minZ = Math.min(a.getZ(), b.getZ()) - pad, maxZ = Math.max(a.getZ(), b.getZ()) + pad;

        // Pass 1: which columns are part of the deck.
        Map<Long, Double> columns = new LinkedHashMap<>(); // packed xz -> t along the bridge
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double cx = x - a.getX(), cz = z - a.getZ();
                double t = cx * ux + cz * uz;
                double s = cx * nx + cz * nz;
                if (t >= -0.5 && t <= len + 0.5 && Math.abs(s) <= half + 1e-6) {
                    columns.put(pack(x, z), t);
                }
            }
        }

        // Pass 2: heights, and railings on columns at the sides of the deck.
        for (Map.Entry<Long, Double> e : columns.entrySet()) {
            int x = unpackX(e.getKey()), z = unpackZ(e.getKey());
            double f = Math.max(0, Math.min(1, e.getValue() / len));
            int h = (int) Math.round(h0 + (h1 - h0) * f);
            int y = Math.floorDiv(h, 2);
            SlabType type = Math.floorMod(h, 2) == 0 ? SlabType.BOTTOM : SlabType.TOP;
            BlockPos pos = new BlockPos(x, y, z);
            plan.deck.put(pos, type);

            if (width >= 3 && isSideEdge(columns, a, x, z, nx, nz, half)) {
                plan.railings.add(pos.above());
            }
        }
        return plan;
    }

    private static boolean isSideEdge(Map<Long, Double> columns, BlockPos a, int x, int z,
                                      double nx, double nz, double half) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            int ox = x + d[0], oz = z + d[1];
            if (columns.containsKey(pack(ox, oz))) continue;
            double s = (ox - a.getX()) * nx + (oz - a.getZ()) * nz;
            // Only neighbours off to the side count; missing neighbours past the ends are the entrances.
            if (Math.abs(s) > half + 1e-6) return true;
        }
        return false;
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int unpackX(long p) {
        return (int) (p >> 32);
    }

    private static int unpackZ(long p) {
        return (int) p;
    }
}
