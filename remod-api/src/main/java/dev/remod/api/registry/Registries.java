package dev.remod.api.registry;

/**
 * The registries available to a mod.
 *
 * <p>Registration is only legal during {@link dev.remod.api.LifecyclePhase#INIT}
 * (and the client/server init phases for side-specific content). Calling
 * outside that window throws, rather than silently registering content the
 * game will never see.</p>
 */
public interface Registries {

    Registry<ItemDefinition> items();

    Registry<BlockDefinition> blocks();

    Registry<CreativeTabDefinition> creativeTabs();

    /** True while registration is permitted. */
    boolean isOpen();
}
