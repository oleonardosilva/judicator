package com.elless.judicator.api.staff;

import java.util.Map;
import java.util.Objects;

/**
 * What one staff member applied in a period, as {@code /activity} counts it.
 *
 * @param staff  the staff member's name (or {@code Console}, or a plugin's)
 * @param counts how many of each kind: a {@link com.elless.judicator.api.punishment.PunishmentType} name, or
 *               {@code WARN}; kinds with none are left out
 */
public record StaffActivity(String staff, Map<String, Integer> counts) {

    public StaffActivity {
        Objects.requireNonNull(staff, "staff");
        counts = Map.copyOf(counts);
    }

    /**
     * @return everything applied
     */
    public int total() {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }
}
