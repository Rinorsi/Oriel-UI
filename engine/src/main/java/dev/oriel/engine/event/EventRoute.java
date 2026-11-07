package dev.oriel.engine.event;

import dev.oriel.engine.dom.Node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Reentrant document-local route buffers. The ancestor snapshot survives listener-driven tree mutations. */
public final class EventRoute {
    private final List<Node[]> frames = new ArrayList<>();
    private int depth;

    public void dispatch(Node target, Event event, String inlineHandler, boolean compare) {
        int slot = depth++;
        if (slot == frames.size()) frames.add(new Node[16]);
        Node[] path = frames.get(slot);
        int size = 0;
        try {
            for (Node node = target; node != null; node = node.parentNode()) {
                if (size == path.length) { path = Arrays.copyOf(path, size * 2); frames.set(slot, path); }
                path[size++] = node;
            }
            if (compare) {
                List<Node> reference = new ArrayList<>();
                for (Node n = target; n != null; n = n.parentNode()) reference.add(n);
                if (reference.size() != size) throw new IllegalStateException("input route size differs for " + target);
                for (int i = 0; i < size; i++) if (reference.get(i) != path[i])
                    throw new IllegalStateException("input route differs at " + i + " for " + target + " event=" + event.type);
            }
            for (int i = size - 1; i > 0 && !event.propagationStopped(); i--) {
                Event.Access.at(event, path[i], Event.Phase.CAPTURING);
                Node.EventAccess.invoke(path[i], event, true);
            }
            if (!event.propagationStopped()) {
                Event.Access.at(event, target, Event.Phase.AT_TARGET);
                Node.EventAccess.invoke(target, event, true);
                if (!event.immediatePropagationStopped()) Node.EventAccess.invoke(target, event, false);
                Node.EventAccess.inline(target, event, inlineHandler);
            }
            if (event.bubbles) for (int i = 1; i < size && !event.propagationStopped(); i++) {
                Event.Access.at(event, path[i], Event.Phase.BUBBLING);
                Node.EventAccess.invoke(path[i], event, false);
                Node.EventAccess.inline(path[i], event, inlineHandler);
            }
        } finally {
            Arrays.fill(path, 0, size, null);
            depth--;
        }
    }
}
