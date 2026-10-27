import { describe, it, expect } from 'vitest'
import { calendarCells, dateLabel, monthLabel, occursOn, parts, shiftMonth, weekLabel } from './dates'

describe('calendar dates without UTC/local-time date drift', () => {
  it('places October 2026 on the correct weekdays with complete weeks', () => {
    const cells = calendarCells('2026-10')
    expect(cells).toHaveLength(35)
    expect(cells[0]?.date).toBe('2026-09-28')
    expect(cells[3]?.date).toBe('2026-10-01')
    expect(cells.findIndex(cell => cell.date === '2026-10-20') % 7).toBe(1)
    expect(cells.filter(cell => cell.current)).toHaveLength(31)
    expect(cells.at(-1)?.date).toBe('2026-11-01')
  })
  it('handles a leap month and the year boundary', () => {
    expect(calendarCells('2024-02').filter(cell => cell.current)).toHaveLength(29)
    expect(calendarCells('2025-02').filter(cell => cell.current)).toHaveLength(28)
    expect(shiftMonth('2026-01', -1)).toBe('2025-12')
    expect(shiftMonth('2026-12', 1)).toBe('2027-01')
    expect(shiftMonth('1000-01', -1)).toBe('1000-01')
    expect(shiftMonth('9999-12', 1)).toBe('9999-12')
    expect(calendarCells('9999-12').filter(cell => !cell.valid).length).toBeGreaterThan(0)
    expect(weekLabel('2026-10-20')).toBe('星期二')
  })
  it('marks annual dates only after the original date and keeps February 29 exact', () => {
    expect(occursOn('2020-05-20', '2026-05-20')).toBe(true)
    expect(occursOn('2027-05-20', '2026-05-20')).toBe(false)
    expect(occursOn('2024-02-29', '2025-02-28')).toBe(false)
    expect(occursOn('2024-02-29', '2025-03-01')).toBe(false)
    expect(occursOn('2024-02-29', '2028-02-29')).toBe(true)
  })
  it('rejects impossible dates instead of silently rolling to the next month', () => {
    expect(() => parts('2025-02-29')).toThrow()
    expect(() => parts('2026-13-01')).toThrow()
    expect(dateLabel('2026-10-01')).toBe('2026年10月1日')
    expect(monthLabel('2026-01')).toBe('2026年1月')
  })
})
