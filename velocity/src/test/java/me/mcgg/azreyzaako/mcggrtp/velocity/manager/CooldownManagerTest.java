package me.mcgg.azreyzaako.mcggrtp.velocity.manager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CooldownManagerTest {
    @Test
    void markUsedActivatesCooldown() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-16T00:00:00Z"));
        CooldownManager manager = new CooldownManager(clock, true, 300);
        UUID playerId = UUID.randomUUID();

        manager.markUsed(playerId);

        CooldownManager.CooldownState state = manager.getState(playerId);
        assertTrue(state.active());
        assertTrue(state.remainingSeconds() > 0);
    }

    @Test
    void expiredCooldownBecomesInactive() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-16T00:00:00Z"));
        CooldownManager manager = new CooldownManager(clock, true, 10);
        UUID playerId = UUID.randomUUID();
        manager.markUsed(playerId);

        clock.advanceSeconds(11);

        assertFalse(manager.getState(playerId).active());
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
