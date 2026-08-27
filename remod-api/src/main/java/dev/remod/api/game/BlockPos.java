package dev.remod.api.game;

/** An immutable integer block coordinate. */
public final class BlockPos {

    public static final BlockPos ORIGIN = new BlockPos(0, 0, 0);

    private final int x;
    private final int y;
    private final int z;

    public BlockPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public BlockPos offset(int dx, int dy, int dz) {
        return new BlockPos(x + dx, y + dy, z + dz);
    }

    public BlockPos above() {
        return offset(0, 1, 0);
    }

    public BlockPos below() {
        return offset(0, -1, 0);
    }

    /** The centre of this block, which is what entities are positioned at. */
    public Vec3 center() {
        return new Vec3(x + 0.5, y + 0.5, z + 0.5);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof BlockPos)) {
            return false;
        }
        BlockPos that = (BlockPos) other;
        return x == that.x && y == that.y && z == that.z;
    }

    @Override
    public int hashCode() {
        return (x * 31 + y) * 31 + z;
    }

    @Override
    public String toString() {
        return "[" + x + ", " + y + ", " + z + "]";
    }
}
