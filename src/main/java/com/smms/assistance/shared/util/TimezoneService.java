package com.smms.assistance.shared.util;

import java.time.*;
import java.util.Set;

public final class TimezoneService {

    private TimezoneService() {}

    public static final String UTC = "UTC";

    public static LocalDateTime utcNow() {
        return Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime();
    }

    public static LocalDate todayAtZone(String timezoneId) {
        return ZonedDateTime.now(ZoneId.of(timezoneId)).toLocalDate();
    }

    public static LocalTime nowAtZone(String timezoneId) {
        return ZonedDateTime.now(ZoneId.of(timezoneId)).toLocalTime();
    }

    public static ZonedDateTime zonedNow(String timezoneId) {
        return ZonedDateTime.now(ZoneId.of(timezoneId));
    }

    public static String convertUtcToZone(LocalDateTime utc, String targetZoneId) {
        if (utc == null) return null;
        return utc.atZone(ZoneOffset.UTC)
                .withZoneSameInstant(ZoneId.of(targetZoneId))
                .toLocalDateTime()
                .toString();
    }

    public static boolean isValidTimezone(String timezoneId) {
        if (timezoneId == null || timezoneId.isBlank()) return false;
        try {
            return Set.of(ZoneId.getAvailableZoneIds()).contains(timezoneId);
        } catch (Exception e) {
            return false;
        }
    }

    public static String defaultTimezone() {
        return "America/Lima";
    }
}
