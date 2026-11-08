package dev.oriel.mod.internal.client;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Client-thread transport guard. A successful dispatch is not a server business acknowledgement. */
public final class ClientSender<T> {
    private final BooleanSupplier connected;
    private final Predicate<T> channel;
    private final Consumer<T> transport;
    private final Consumer<String> failure;

    public ClientSender(BooleanSupplier connected, Predicate<T> channel, Consumer<T> transport, Consumer<String> failure) {
        this.connected = connected;
        this.channel = channel;
        this.transport = transport;
        this.failure = failure;
    }

    public boolean send(T payload) {
        try {
            if (!connected.getAsBoolean()) {
                failure.accept("no active server connection");
                return false;
            }
            if (!channel.test(payload)) {
                failure.accept("server does not support this Oriel channel");
                return false;
            }
            transport.accept(payload);
            return true;
        } catch (RuntimeException e) {
            failure.accept("transport rejected payload: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }
    }
}
