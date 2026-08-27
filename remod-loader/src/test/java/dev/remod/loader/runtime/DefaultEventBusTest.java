package dev.remod.loader.runtime;

import dev.remod.api.event.AbstractCancellableEvent;
import dev.remod.api.event.Event;
import dev.remod.api.event.EventPriority;
import dev.remod.api.event.Subscription;
import dev.remod.common.log.ReModLog;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultEventBusTest {

    private static class Base implements Event {
    }

    private static final class Derived extends Base {
    }

    private static final class Vetoable extends AbstractCancellableEvent {
    }

    @Test
    void deliversToSubscribersOfTheExactType() {
        DefaultEventBus bus = new DefaultEventBus();
        List<String> seen = new ArrayList<>();
        bus.subscribe(Base.class, event -> seen.add("base"));

        bus.post(new Base());

        assertEquals(List.of("base"), seen);
    }

    @Test
    void deliversSubtypesToSupertypeSubscribers() {
        DefaultEventBus bus = new DefaultEventBus();
        List<String> seen = new ArrayList<>();
        bus.subscribe(Base.class, event -> seen.add("base"));
        bus.subscribe(Derived.class, event -> seen.add("derived"));
        bus.subscribe(Event.class, event -> seen.add("any"));

        bus.post(new Derived());

        assertEquals(3, seen.size());
        assertTrue(seen.containsAll(List.of("base", "derived", "any")));

        seen.clear();
        bus.post(new Base());
        // A Base is not a Derived, so the Derived listener must not fire.
        assertEquals(2, seen.size());
        assertFalse(seen.contains("derived"));
    }

    @Test
    void runsHigherPrioritiesFirstAndRegistrationOrderWithinAPriority() {
        DefaultEventBus bus = new DefaultEventBus();
        List<String> order = new ArrayList<>();
        bus.subscribe(Base.class, EventPriority.NORMAL, event -> order.add("normal-1"));
        bus.subscribe(Base.class, EventPriority.LOWEST, event -> order.add("lowest"));
        bus.subscribe(Base.class, EventPriority.HIGHEST, event -> order.add("highest"));
        bus.subscribe(Base.class, EventPriority.NORMAL, event -> order.add("normal-2"));
        bus.subscribe(Base.class, EventPriority.HIGH, event -> order.add("high"));

        bus.post(new Base());

        assertEquals(List.of("highest", "high", "normal-1", "normal-2", "lowest"), order);
    }

    @Test
    void cancellationIsVisibleToLaterListenersAndTheCaller() {
        DefaultEventBus bus = new DefaultEventBus();
        List<Boolean> observed = new ArrayList<>();
        bus.subscribe(Vetoable.class, EventPriority.HIGHEST, event -> event.cancel());
        bus.subscribe(Vetoable.class, EventPriority.LOW, event -> observed.add(event.isCancelled()));

        Vetoable event = bus.post(new Vetoable());

        assertEquals(List.of(Boolean.TRUE), observed);
        assertTrue(event.isCancelled());
    }

    @Test
    void unsubscribeStopsDelivery() {
        DefaultEventBus bus = new DefaultEventBus();
        List<String> seen = new ArrayList<>();
        Subscription subscription = bus.subscribe(Base.class, event -> seen.add("x"));

        bus.post(new Base());
        assertTrue(subscription.isActive());
        subscription.unsubscribe();
        bus.post(new Base());

        assertEquals(1, seen.size());
        assertFalse(subscription.isActive());
        // Unsubscribing twice is harmless.
        subscription.unsubscribe();
    }

    @Test
    void subscribeOnceFiresExactlyOnce() {
        DefaultEventBus bus = new DefaultEventBus();
        List<String> seen = new ArrayList<>();
        bus.subscribeOnce(Base.class, event -> seen.add("once"));

        bus.post(new Base());
        bus.post(new Base());
        bus.post(new Base());

        assertEquals(1, seen.size());
        assertEquals(0, bus.listenerCount(Base.class));
    }

    @Test
    void aThrowingListenerDoesNotStopTheOthers() {
        ReModLog.reset();
        DefaultEventBus bus = new DefaultEventBus();
        List<String> seen = new ArrayList<>();
        bus.subscribe(Base.class, EventPriority.HIGHEST, event -> {
            throw new IllegalStateException("mod bug");
        });
        bus.subscribe(Base.class, event -> seen.add("survivor"));

        bus.post(new Base());

        assertEquals(List.of("survivor"), seen);
    }

    @Test
    void aRepeatedlyThrowingListenerIsDisabledRatherThanFloodingTheLog() {
        ReModLog.reset();
        DefaultEventBus bus = new DefaultEventBus();
        int[] calls = {0};
        bus.subscribe(Base.class, event -> {
            calls[0]++;
            throw new IllegalStateException("always broken");
        });

        for (int i = 0; i < 50; i++) {
            bus.post(new Base());
        }

        // Disabled after the failure limit, not called 50 times.
        assertEquals(5, calls[0]);
        assertEquals(0, bus.listenerCount(Base.class));
    }

    @Test
    void perModViewsOnlyDetachTheirOwnListeners() {
        DefaultEventBus bus = new DefaultEventBus();
        ModEventBus alpha = new ModEventBus(bus, "alpha");
        ModEventBus beta = new ModEventBus(bus, "beta");
        List<String> seen = new ArrayList<>();
        alpha.subscribe(Base.class, event -> seen.add("alpha"));
        beta.subscribe(Base.class, event -> seen.add("beta"));

        alpha.unsubscribeAll();
        bus.post(new Base());

        assertEquals(List.of("beta"), seen);
        assertEquals(0, alpha.ownedCount());
        assertEquals(1, beta.ownedCount());
    }

    @Test
    void unsubscribingAModByIdRemovesItsListeners() {
        DefaultEventBus bus = new DefaultEventBus();
        ModEventBus alpha = new ModEventBus(bus, "alpha");
        List<String> seen = new ArrayList<>();
        alpha.subscribe(Base.class, event -> seen.add("alpha"));

        bus.unsubscribeAllOf("alpha");
        bus.post(new Base());

        assertTrue(seen.isEmpty());
    }

    @Test
    void aListenerMaySubscribeDuringDispatchWithoutBreakingIteration() {
        DefaultEventBus bus = new DefaultEventBus();
        List<String> seen = new ArrayList<>();
        bus.subscribe(Base.class, event -> {
            seen.add("first");
            bus.subscribe(Base.class, later -> seen.add("added"));
        });

        bus.post(new Base());
        bus.post(new Base());

        assertTrue(seen.contains("added"));
    }
}
