package com.elless.judicator.punish;

import com.elless.judicator.punish.types.PunishType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.elless.judicator.testing.FakeConfig.map;
import static com.elless.judicator.testing.FakeConfig.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguredReasonTest {

    @Test
    void legacyReasonHasOneStep() {
        final ConfiguredReason reason = ConfiguredReason.from(of(map(
                "reason", "Cheating", "permission", "punish.moderator", "type", "TEMPBAN", "ip", false,
                "duration", "7:months,2:weeks")));

        assertEquals("Cheating", reason.getReason());
        assertEquals("punish.moderator", reason.getPermission());
        assertFalse(reason.isProgressive());
        assertEquals(PunishType.TEMPBAN, reason.getType());
        assertEquals("7:months,2:weeks", reason.getDuration());
        assertEquals(reason.stepFor(0), reason.stepFor(5));
    }

    @Test
    void iconIsOptional() {
        assertEquals("tnt", ConfiguredReason.from(of(map("reason", "Serious", "type", "BAN", "icon", "tnt"))).getIcon());
        assertNull(ConfiguredReason.from(of(map("reason", "Serious", "type", "BAN"))).getIcon());
    }

    @Test
    void permanentLegacyReason() {
        final ConfiguredReason reason = ConfiguredReason.from(of(map("reason", "Serious", "type", "BAN", "ip", true)));
        assertTrue(reason.isPermanent());
        assertTrue(reason.isIp());
        assertTrue(reason.stepFor(0).isPermanent());
    }

    @Test
    void progressiveReasonEscalatesAndRepeatsTheLastStep() {
        final ConfiguredReason reason = ConfiguredReason.from(of(map(
                "reason", "Player Offense", "ip", false,
                "steps", List.of(
                        map("type", "TEMPMUTE", "duration", "7:d,2:h"),
                        map("type", "TEMPMUTE", "duration", "30:d"),
                        map("type", "MUTE")))));

        assertTrue(reason.isProgressive());
        assertEquals(new ConfiguredReason.Step(PunishType.TEMPMUTE, "7:d,2:h"), reason.stepFor(0));
        assertEquals(new ConfiguredReason.Step(PunishType.TEMPMUTE, "30:d"), reason.stepFor(1));
        assertEquals(PunishType.MUTE, reason.stepFor(2).type());
        assertNull(reason.stepFor(2).duration());
        assertEquals(reason.stepFor(2), reason.stepFor(10));
        // A negative count (should not happen) falls back to the first step instead of failing.
        assertEquals(reason.stepFor(0), reason.stepFor(-1));
        // The first step also fills the legacy fields used by older code paths.
        assertEquals(PunishType.TEMPMUTE, reason.getType());
        assertEquals("7:d,2:h", reason.getDuration());
    }
}
