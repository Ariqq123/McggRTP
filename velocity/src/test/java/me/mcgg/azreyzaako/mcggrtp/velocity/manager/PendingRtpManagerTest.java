package me.mcgg.azreyzaako.mcggrtp.velocity.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import me.mcgg.azreyzaako.mcggrtp.common.PendingRtp;
import org.junit.jupiter.api.Test;

class PendingRtpManagerTest {
    @Test
    void findsPendingRequestForMatchingServer() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-16T00:00:00Z"));
        PendingRtpManager manager = new PendingRtpManager(clock, 30);
        PendingRtp pending = new PendingRtp("req-1", UUID.randomUUID(), "survival-2", "world", "overworld", clock.millis());
        manager.put(pending);

        var found = manager.findFor(pending.playerUuid(), "survival-2");

        assertTrue(found.isPresent());
        assertEquals(pending, found.get());
    }

    @Test
    void ignoresExpiredRequests() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-16T00:00:00Z"));
        PendingRtpManager manager = new PendingRtpManager(clock, 5);
        PendingRtp pending = new PendingRtp("req-1", UUID.randomUUID(), "survival-2", "world", "overworld", clock.millis());
        manager.put(pending);

        clock.advanceSeconds(6);

        assertFalse(manager.findFor(pending.playerUuid(), "survival-2").isPresent());
    }

    @Test
    void clearRemovesOnlyMatchingRequest() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-16T00:00:00Z"));
        PendingRtpManager manager = new PendingRtpManager(clock, 30);
        PendingRtp pending = new PendingRtp("req-1", UUID.randomUUID(), "survival-2", "world", "overworld", clock.millis());
        manager.put(pending);

        manager.clear(pending.playerUuid(), "req-1");

        assertFalse(manager.findFor(pending.playerUuid(), "survival-2").isPresent());
    }

    @Test
    void expiredLookupRemovesTrackedPendingEntryWithoutFullScan() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-16T00:00:00Z"));
        PendingRtpManager manager = new PendingRtpManager(clock, 5);
        PendingRtp pending = new PendingRtp("req-1", UUID.randomUUID(), "survival-2", "world", "overworld", clock.millis());
        manager.put(pending);

        clock.advanceSeconds(6);

        assertFalse(manager.findFor(pending.playerUuid(), "survival-2").isPresent());
        assertEquals(0, manager.trackedCount());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }
    }
}
