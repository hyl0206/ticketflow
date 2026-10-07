package com.ticketflow.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

public final class TicketNoGenerator {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.BASIC_ISO_DATE;

    private TicketNoGenerator() {
    }

    public static String generate() {
        String date = LocalDate.now().format(DATE_FORMATTER);

        String randomPart = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 6)
                .toUpperCase(Locale.ROOT);

        return "TK" + date + randomPart;
    }
}