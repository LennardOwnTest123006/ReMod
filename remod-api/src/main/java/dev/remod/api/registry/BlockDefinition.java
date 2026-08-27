package dev.remod.api.registry;

import dev.remod.api.game.Identifier;
import dev.remod.api.game.Text;

/**
 * A declarative description of a block.
 *
 * <p>As with {@link ItemDefinition}, ReMod records the description and lets the
 * version adapter build the version-appropriate block. Setting
 * {@link Builder#withItem(boolean)} (the default) also registers the matching
 * block item, which is what a mod almost always wants.</p>
 */
public final class BlockDefinition {

    private final Identifier id;
    private final Text displayName;
    private final float hardness;
    private final float resistance;
    private final int lightLevel;
    private final boolean requiresTool;
    private final SoundGroup soundGroup;
    private final MaterialKind material;
    private final boolean withItem;
    private final Identifier creativeTab;

    private BlockDefinition(Builder builder) {
        this.id = builder.id;
        this.displayName = builder.displayName;
        this.hardness = builder.hardness;
        this.resistance = builder.resistance;
        this.lightLevel = builder.lightLevel;
        this.requiresTool = builder.requiresTool;
        this.soundGroup = builder.soundGroup;
        this.material = builder.material;
        this.withItem = builder.withItem;
        this.creativeTab = builder.creativeTab;
    }

    public static Builder builder(Identifier id) {
        return new Builder(id);
    }

    public Identifier id() {
        return id;
    }

    public Text displayName() {
        return displayName;
    }

    /** Mining time. Vanilla stone is 1.5; obsidian is 50. */
    public float hardness() {
        return hardness;
    }

    /** Blast resistance. Vanilla stone is 6; obsidian is 1200. */
    public float resistance() {
        return resistance;
    }

    /** Emitted light, 0-15. */
    public int lightLevel() {
        return lightLevel;
    }

    /** True when the correct tool is required for the block to drop anything. */
    public boolean requiresTool() {
        return requiresTool;
    }

    public SoundGroup soundGroup() {
        return soundGroup;
    }

    public MaterialKind material() {
        return material;
    }

    /** True when a matching block item should be registered too. */
    public boolean hasItem() {
        return withItem;
    }

    public Identifier creativeTab() {
        return creativeTab;
    }

    public String translationKey() {
        return id.translationKey("block");
    }

    @Override
    public String toString() {
        return "block " + id;
    }

    /** The broad material families Minecraft has kept stable across versions. */
    public enum MaterialKind {
        STONE, METAL, WOOD, DIRT, SAND, GLASS, WOOL, PLANT, ICE
    }

    /** The step/break/place sound family a block uses. */
    public enum SoundGroup {
        STONE, METAL, WOOD, GRAVEL, GLASS, WOOL, GRASS, SAND, SNOW
    }

    /** Fluent builder for {@link BlockDefinition}. */
    public static final class Builder {

        private final Identifier id;
        private Text displayName;
        private float hardness = 1.5f;
        private float resistance = 6.0f;
        private int lightLevel;
        private boolean requiresTool = true;
        private SoundGroup soundGroup = SoundGroup.STONE;
        private MaterialKind material = MaterialKind.STONE;
        private boolean withItem = true;
        private Identifier creativeTab;

        private Builder(Identifier id) {
            if (id == null) {
                throw new IllegalArgumentException("A block needs an identifier");
            }
            this.id = id;
        }

        public Builder displayName(Text value) {
            this.displayName = value;
            return this;
        }

        public Builder hardness(float value) {
            if (value < 0) {
                throw new IllegalArgumentException(
                        "hardness for " + id + " cannot be negative (use -1 semantics via strength)");
            }
            this.hardness = value;
            return this;
        }

        public Builder resistance(float value) {
            if (value < 0) {
                throw new IllegalArgumentException("resistance for " + id + " cannot be negative");
            }
            this.resistance = value;
            return this;
        }

        /** Sets hardness and resistance together, as vanilla's own builder does. */
        public Builder strength(float hardnessValue, float resistanceValue) {
            return hardness(hardnessValue).resistance(resistanceValue);
        }

        public Builder lightLevel(int value) {
            if (value < 0 || value > 15) {
                throw new IllegalArgumentException(
                        "lightLevel for " + id + " must be between 0 and 15, was " + value);
            }
            this.lightLevel = value;
            return this;
        }

        public Builder requiresTool(boolean value) {
            this.requiresTool = value;
            return this;
        }

        public Builder soundGroup(SoundGroup value) {
            this.soundGroup = value == null ? SoundGroup.STONE : value;
            return this;
        }

        public Builder material(MaterialKind value) {
            this.material = value == null ? MaterialKind.STONE : value;
            return this;
        }

        /** Set to false for a block that should have no item form. */
        public Builder withItem(boolean value) {
            this.withItem = value;
            return this;
        }

        public Builder creativeTab(Identifier value) {
            this.creativeTab = value;
            return this;
        }

        public BlockDefinition build() {
            return new BlockDefinition(this);
        }
    }
}
