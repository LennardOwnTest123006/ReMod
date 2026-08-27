package dev.remod.api.registry;

import dev.remod.api.game.Identifier;
import dev.remod.api.game.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A creative-inventory tab.
 *
 * <p>Mods may either add their content to a vanilla tab (by setting
 * {@code creativeTab} on the item or block) or declare their own tab here.</p>
 */
public final class CreativeTabDefinition {

    private final Identifier id;
    private final Text title;
    private final Identifier icon;
    private final List<Identifier> entries;

    private CreativeTabDefinition(Builder builder) {
        this.id = builder.id;
        this.title = builder.title;
        this.icon = builder.icon;
        this.entries = Collections.unmodifiableList(new ArrayList<>(builder.entries));
    }

    public static Builder builder(Identifier id) {
        return new Builder(id);
    }

    public Identifier id() {
        return id;
    }

    public Text title() {
        return title;
    }

    /** The item shown on the tab button. */
    public Identifier icon() {
        return icon;
    }

    /** Items explicitly listed in the tab, in order. */
    public List<Identifier> entries() {
        return entries;
    }

    @Override
    public String toString() {
        return "creative tab " + id;
    }

    /** Fluent builder for {@link CreativeTabDefinition}. */
    public static final class Builder {

        private final Identifier id;
        private Text title;
        private Identifier icon;
        private final List<Identifier> entries = new ArrayList<>();

        private Builder(Identifier id) {
            if (id == null) {
                throw new IllegalArgumentException("A creative tab needs an identifier");
            }
            this.id = id;
            this.title = Text.translatable(id.translationKey("itemGroup"));
        }

        public Builder title(Text value) {
            if (value != null) {
                this.title = value;
            }
            return this;
        }

        public Builder icon(Identifier itemId) {
            this.icon = itemId;
            return this;
        }

        public Builder add(Identifier itemId) {
            if (itemId != null) {
                entries.add(itemId);
            }
            return this;
        }

        public CreativeTabDefinition build() {
            if (icon == null && !entries.isEmpty()) {
                icon = entries.get(0);
            }
            return new CreativeTabDefinition(this);
        }
    }
}
