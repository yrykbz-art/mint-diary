package local.mintdiary;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class DateRules {
    private DateRules() {}

    public static long elapsedDays(LocalDate original, LocalDate today) {
        return Math.max(0, ChronoUnit.DAYS.between(original, today));
    }

    public static long daysUntilStart(LocalDate original, LocalDate today) {
        return Math.max(0, ChronoUnit.DAYS.between(today, original));
    }

    public static boolean occursOn(LocalDate original, LocalDate day) {
        // Comparing the actual month/day naturally limits February 29 to leap years.
        return !day.isBefore(original)
                && original.getMonthValue() == day.getMonthValue()
                && original.getDayOfMonth() == day.getDayOfMonth();
    }

    public static void validateDate(LocalDate date) {
        if (date == null || date.getYear() < 1000 || date.getYear() > 9999) {
            throw ApiException.badRequest("请选择 1000 年至 9999 年之间的有效日期");
        }
    }
}
