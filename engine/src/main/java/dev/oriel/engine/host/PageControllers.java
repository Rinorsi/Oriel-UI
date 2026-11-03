package dev.oriel.engine.host;

import dev.oriel.engine.dom.Document;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Java controllers for pages: the declarative page stays HTML and CSS, and its behaviour lives in a Java object
 * that listens to the page's events, keeps its state and updates the DOM. A document attaches the controller
 * registered for its URL once it has parsed and run its scripts, before {@code DOMContentLoaded}, in every host (the
 * game, the previewer and tests alike). A controller that throws stops the page like any other engine error.
 */
public final class PageControllers {
    /** The behaviour of one loaded page. A new one is made for every load of the page (reloads included). */
    @FunctionalInterface
    public interface Controller {
        void attach(Document document);

        /**
         * What a hot reload of the same page should carry over (the model, not the DOM), or null to start fresh.
         * Called on the old controller just before its page is thrown away.
         */
        default Object saveState() {
            return null;
        }

        /** Takes over {@link #saveState()} of the controller this one replaces; called after {@link #attach}. */
        default void restoreState(Object state) {}
    }

    private static final Map<String, Supplier<? extends Controller>> BY_URL = new ConcurrentHashMap<>();

    private PageControllers() {}

    /** Registers the controller factory for the page at {@code url} ({@code ns:path/page.html}), replacing any. */
    public static void register(String url, Supplier<? extends Controller> factory) {
        BY_URL.put(url, factory);
    }

    public static boolean has(String url) {
        return BY_URL.containsKey(url);
    }

    /** Attaches the controller registered for the document's URL, if there is one. */
    public static void attach(Document document) {
        Supplier<? extends Controller> factory = BY_URL.get(document.url());
        if (factory == null) return;
        Controller controller = factory.get();
        document.setController(controller);
        controller.attach(document);
    }
}
