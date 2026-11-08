package dev.oriel.kit.preset;

import dev.oriel.kit.OrielKit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NestedNoticeTest {
    @Test void noticeShowsProvidedParentsAndCheckedDismissalRequestsOff() {
        var host = new KitHost();
        host.acceptSends = true;
        var doc = host.open(OrielKit.page("nested-notice"), new NestedNoticePreset(), 427, 240,
                "{\"parents\":\"Backpack (0.12.1), Tools (0.12.0)\",\"active\":\"Backpack (0.12.1)\"}");
        assertTrue(doc.getElementById("parents").textContent().contains("Tools"));
        assertTrue(doc.getElementById("active").textContent().contains("Backpack"));
        doc.getElementById("never").setChecked(true);
        doc.getElementById("dismiss").click();
        assertEquals("oriel {\"op\":\"nested-dismiss\",\"disable\":true}", host.sent.getLast());
        doc.close();
    }

    @Test void ordinaryDismissalDoesNotDisableFutureFingerprints() {
        var host = new KitHost();
        host.acceptSends = true;
        var doc = host.open(OrielKit.page("nested-notice"), new NestedNoticePreset());
        doc.getElementById("dismiss").click();
        assertEquals("oriel {\"op\":\"nested-dismiss\",\"disable\":false}", host.sent.getLast());
        doc.close();
    }
}
