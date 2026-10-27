export function parts(date: string): [number, number, number] {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date)
  if (!match) throw new Error('Invalid date')
  const values: [number, number, number] = [Number(match[1]), Number(match[2]), Number(match[3])]
  const check = utcDate(...values)
  if (values[0] < 1 || check.getUTCFullYear() !== values[0] || check.getUTCMonth() + 1 !== values[1] || check.getUTCDate() !== values[2]) throw new Error('Invalid date')
  return values
}

function utcDate(year: number, month: number, day: number) {
  const result = new Date(0)
  result.setUTCFullYear(year, month - 1, day)
  result.setUTCHours(0, 0, 0, 0)
  return result
}

function iso(date: Date) { return `${String(date.getUTCFullYear()).padStart(4, '0')}-${String(date.getUTCMonth() + 1).padStart(2, '0')}-${String(date.getUTCDate()).padStart(2, '0')}` }
export function monthLabel(month: string) { const [year, number] = month.split('-'); return `${Number(year)}年${Number(number)}月` }
export function dateLabel(date: string) { const [y, m, d] = parts(date); return `${y}年${m}月${d}日` }
export function weekLabel(date: string) { return `星期${'日一二三四五六'[utcDate(...parts(date)).getUTCDay()]}` }
export function shiftMonth(month: string, amount: number) {
  const [year, number] = month.split('-').map(Number)
  const date = utcDate(year!, number! + amount, 1)
  if (date.getUTCFullYear() < 1000 || date.getUTCFullYear() > 9999) return month
  return iso(date).slice(0, 7)
}
export function calendarCells(month: string) {
  const [year, number] = month.split('-').map(Number)
  const first = utcDate(year!, number!, 1)
  const offset = (first.getUTCDay() + 6) % 7
  const length = utcDate(year!, number! + 1, 0).getUTCDate()
  const cells = Math.ceil((offset + length) / 7) * 7
  return Array.from({ length: cells }, (_, index) => {
    const date = utcDate(year!, number!, index - offset + 1)
    return { date: iso(date), number: date.getUTCDate(), current: date.getUTCMonth() + 1 === number,
      valid: date.getUTCFullYear() >= 1000 && date.getUTCFullYear() <= 9999 }
  })
}
export function occursOn(original: string, day: string) { return day >= original && original.slice(5) === day.slice(5) }
