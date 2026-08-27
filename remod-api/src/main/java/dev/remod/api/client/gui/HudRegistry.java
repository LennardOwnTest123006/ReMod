package dev.remod.api.client.gui;

import java.util.Collection;

/** Where client mods register HUD layers. */
public interface HudRegistry {

    /**
     * Adds a layer drawn over the game.
     *
     * @param id    unique id, conventionally {@code <modid>:<name>}
     * @param layer the renderer
     * @return a handle that removes the layer when closed
     */
    HudHandle register(String id, HudLayer layer);

    Collection<String> layerIds();

    /** A registered layer, which can be hidden or removed. */
    interface HudHandle extends AutoCloseable {

        String id();

        void setVisible(boolean visible);

        boolean isVisible();

        void remove();

        @Override
        default void close() {
            remove();
        }
    }
}
