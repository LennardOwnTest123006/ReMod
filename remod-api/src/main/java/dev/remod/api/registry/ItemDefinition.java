package dev.remod.api.registry;

import dev.remod.api.game.Identifier;
import dev.remod.api.game.Text;

/**
 * A declarative description of an item.
 *
 * <p>ReMod registers <em>descriptions</em> rather than Minecraft item
 * instances. The version adapter turns a description into whatever the running
 * Minecraft build needs -- item properties moved from a builder to a component
 * map in 1.20.5, for example, and a mod written against this class did not have
 * to change.</p>
 *
 * <pre>{@code
 * context.registries().items().register(
 *         ItemDefinition.builder(Identifier.of("simplemod", "ruby"))
 *                 .displayName(Text.literal("Ruby"))
 *                 .maxStackSize(16)
 *                 .rarity(Rarity.RARE)
 *                 .creativeTab(Identifier.parse("minecraft:ingredients"))
 *                 .build());
 * }</pre>
 */
public final class ItemDefinition {

    private final Identifier id;
    private final Text displayName;
    private final int maxStackSize;
    private final int maxDamage;
    private final Rarity rarity;
    private final Identifier creativeTab;
    private final boolean fireResistant;
    private final FoodProperties food;

    private ItemDefinition(Builder builder) {
        this.id = builder.id;
        this.displayName = builder.displayName;
        this.maxStackSize = builder.maxStackSize;
        this.maxDamage = builder.maxDamage;
        this.rarity = builder.rarity;
        this.creativeTab = builder.creativeTab;
        this.fireResistant = builder.fireResistant;
        this.food = builder.food;
    }

    public static Builder builder(Identifier id) {
        return new Builder(id);
    }

    public Identifier id() {
        return id;
    }

    /** The display name, or {@code null} to use the translation key {@code item.<ns>.<path>}. */
    public Text displayName() {
        return displayName;
    }

    public int maxStackSize() {
        return maxStackSize;
    }

    /** Durability, or 0 for an item that does not take damage. */
    public int maxDamage() {
        return maxDamage;
    }

    public Rarity rarity() {
        return rarity;
    }

    /** The creative tab to appear in, or {@code null} for none. */
    public Identifier creativeTab() {
        return creativeTab;
    }

    public boolean isFireResistant() {
        return fireResistant;
    }

    /** Food properties, or {@code null} when the item is not edible. */
    public FoodProperties food() {
        return food;
    }

    /** The translation key Minecraft would use for this item. */
    public String translationKey() {
        return id.translationKey("item");
    }

    @Override
    public String toString() {
        return "item " + id;
    }

    /** Fluent builder for {@link ItemDefinition}. */
    public static final class Builder {

        private final Identifier id;
        private Text displayName;
        private int maxStackSize = 64;
        private int maxDamage;
        private Rarity rarity = Rarity.COMMON;
        private Identifier creativeTab;
        private boolean fireResistant;
        private FoodProperties food;

        private Builder(Identifier id) {
            if (id == null) {
                throw new IllegalArgumentException("An item needs an identifier");
            }
            this.id = id;
        }

        public Builder displayName(Text value) {
            this.displayName = value;
            return this;
        }

        public Builder maxStackSize(int value) {
            if (value < 1 || value > 99) {
                throw new IllegalArgumentException(
                        "maxStackSize for " + id + " must be between 1 and 99, was " + value);
            }
            this.maxStackSize = value;
            return this;
        }

        /** Makes the item damageable. Minecraft forbids stacking damageable items. */
        public Builder maxDamage(int value) {
            if (value < 0) {
                throw new IllegalArgumentException("maxDamage for " + id + " cannot be negative");
            }
            this.maxDamage = value;
            if (value > 0) {
                this.maxStackSize = 1;
            }
            return this;
        }

        public Builder rarity(Rarity value) {
            this.rarity = value == null ? Rarity.COMMON : value;
            return this;
        }

        public Builder creativeTab(Identifier value) {
            this.creativeTab = value;
            return this;
        }

        public Builder fireResistant(boolean value) {
            this.fireResistant = value;
            return this;
        }

        public Builder food(FoodProperties value) {
            this.food = value;
            return this;
        }

        public ItemDefinition build() {
            return new ItemDefinition(this);
        }
    }

    /** Nutrition and saturation for an edible item. */
    public static final class FoodProperties {

        private final int nutrition;
        private final float saturation;
        private final boolean alwaysEdible;

        public FoodProperties(int nutrition, float saturation, boolean alwaysEdible) {
            if (nutrition < 0) {
                throw new IllegalArgumentException("Food nutrition cannot be negative");
            }
            this.nutrition = nutrition;
            this.saturation = saturation;
            this.alwaysEdible = alwaysEdible;
        }

        public int nutrition() {
            return nutrition;
        }

        public float saturation() {
            return saturation;
        }

        /** True to allow eating on a full hunger bar, as golden apples do. */
        public boolean isAlwaysEdible() {
            return alwaysEdible;
        }
    }
}
