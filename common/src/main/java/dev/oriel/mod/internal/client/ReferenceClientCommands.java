package dev.oriel.mod.internal.client;

/** Instantiation of reference-only screens must not enter the core command class's verifier graph. */
final class ReferenceClientCommands {
    private ReferenceClientCommands() {}
    static void canvas() { McClient.setScreen(new CanvasTestScreen()); }
}
