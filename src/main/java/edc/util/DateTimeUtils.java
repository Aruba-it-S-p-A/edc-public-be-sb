package edc.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class DateTimeUtils {

    public static final ZoneOffset UTC = ZoneOffset.UTC;
    public static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public static OffsetDateTime nowUtc() {
        return OffsetDateTime.now(UTC);
    }

    public static OffsetDateTime toUtc(LocalDateTime localDateTime) {
        return localDateTime.atOffset(UTC);
    }

    public static OffsetDateTime fromTimestamp(long timestamp) {
        return Instant.ofEpochMilli(timestamp).atOffset(UTC);
    }

    public static long toTimestamp(OffsetDateTime offsetDateTime) {
        return offsetDateTime.toInstant().toEpochMilli();
    }

    public static OffsetDateTime parseIso(String isoString) {
        return OffsetDateTime.parse(isoString, ISO_FORMATTER);
    }

    public static String formatIso(OffsetDateTime dateTime) {
        return dateTime.format(ISO_FORMATTER);
    }

    public static OffsetDateTime convertToUtc(OffsetDateTime dateTime) {
        return dateTime.withOffsetSameInstant(UTC);
    }
}
