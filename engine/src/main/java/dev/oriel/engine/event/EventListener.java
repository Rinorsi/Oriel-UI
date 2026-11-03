package dev.oriel.engine.event;

/** A listener added with {@link dev.oriel.engine.dom.Node#addEventListener}. */
@FunctionalInterface
public interface EventListener {
    void handleEvent(Event event);
}
