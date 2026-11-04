package dev.oriel.mod.internal.client;


import java.lang.instrument.Instrumentation;

/**
 * The dev hot reload's agent: the JVM hands it the {@link Instrumentation} that can redefine loaded classes when
 * {@link DevReload} attaches it to the running game (dev runs only; release jars leave it out).
 */
public final class DevAgent {
    /** Set by the JVM when the agent is attached; read through the system class loader. */
    public static volatile Instrumentation instrumentation;

    private DevAgent() {}

    public static void agentmain(String args, Instrumentation inst) {
        instrumentation = inst;
    }
}
