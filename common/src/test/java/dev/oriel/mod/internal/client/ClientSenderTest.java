package dev.oriel.mod.internal.client;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ClientSenderTest {
    @Test void unavailableConnectionOrChannelNeverSends() {
        var sent = new AtomicInteger();
        var errors = new ArrayList<String>();
        var disconnected = new ClientSender<String>(() -> false, p -> { fail("channel checked without connection"); return true; }, p -> sent.incrementAndGet(), errors::add);
        assertFalse(disconnected.send("message"));
        var missing = new ClientSender<String>(() -> true, p -> false, p -> sent.incrementAndGet(), errors::add);
        assertFalse(missing.send("closed"));
        assertEquals(0, sent.get());
        assertEquals(2, errors.size());
        assertTrue(errors.getFirst().contains("connection"));
        assertTrue(errors.getLast().contains("channel"));
    }

    @Test void connectedTransportReportsDispatchNotBusinessSuccess() {
        var sent = new ArrayList<String>();
        var errors = new ArrayList<String>();
        var sender = new ClientSender<String>(() -> true, p -> p.equals("message"), sent::add, errors::add);
        assertTrue(sender.send("message"));
        assertFalse(sender.send("closed"));
        assertEquals(java.util.List.of("message"), sent);
    }

    @Test void disconnectRaceIsAnExplicitFailure() {
        var errors = new ArrayList<String>();
        var sender = new ClientSender<String>(() -> true, p -> true, p -> { throw new IllegalStateException("connection closed"); }, errors::add);
        assertFalse(sender.send("message"));
        assertTrue(errors.getFirst().contains("connection closed"));
    }
}
