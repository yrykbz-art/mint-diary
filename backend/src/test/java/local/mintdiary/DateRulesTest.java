package local.mintdiary;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DateRulesTest {
    @Test void datesFitTheActualMysqlDateRange() {
        assertThrows(ApiException.class, () -> DateRules.validateDate(LocalDate.of(999, 12, 31)));
        assertDoesNotThrow(() -> DateRules.validateDate(LocalDate.of(1000, 1, 1)));
    }
    @Test void elapsedDaysKeepTheOriginalYearAndStartAtZero() {
        assertEquals(0, DateRules.elapsedDays(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 20)));
        assertEquals(19, DateRules.elapsedDays(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 20)));
        assertEquals(365, DateRules.elapsedDays(LocalDate.of(2025, 10, 20), LocalDate.of(2026, 10, 20)));
        assertEquals(2, DateRules.elapsedDays(LocalDate.of(2024, 2, 28), LocalDate.of(2024, 3, 1)));
    }
    @Test void annualMarkersDoNotAppearBeforeTheirOriginalDate() {
        assertTrue(DateRules.occursOn(LocalDate.of(2020, 5, 20), LocalDate.of(2026, 5, 20)));
        assertFalse(DateRules.occursOn(LocalDate.of(2027, 5, 20), LocalDate.of(2026, 5, 20)));
    }
    @Test void leapDayIsOnlyMarkedOnAnActualFebruary29() {
        LocalDate original = LocalDate.of(2024, 2, 29);
        assertFalse(DateRules.occursOn(original, LocalDate.of(2025, 2, 28)));
        assertFalse(DateRules.occursOn(original, LocalDate.of(2025, 3, 1)));
        assertTrue(DateRules.occursOn(original, LocalDate.of(2028, 2, 29)));
    }
}
