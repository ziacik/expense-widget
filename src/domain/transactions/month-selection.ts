const BRATISLAVA_STANDARD_OFFSET_MS = 60 * 60 * 1000;
const BRATISLAVA_SUMMER_OFFSET_MS = 2 * BRATISLAVA_STANDARD_OFFSET_MS;

export function getBratislavaMonthKey(epochMs: number): string {
	if (!Number.isFinite(epochMs)) {
		throw new RangeError("Timestamp must be finite.");
	}

	const utcDate = new Date(epochMs);
	const utcYear = utcDate.getUTCFullYear();
	if (Number.isNaN(utcDate.getTime()) || utcYear < 1 || utcYear > 9999) {
		throw new RangeError("Timestamp is outside the supported date range.");
	}

	const offsetMs = isBratislavaSummerTime(epochMs, utcYear) ? BRATISLAVA_SUMMER_OFFSET_MS : BRATISLAVA_STANDARD_OFFSET_MS;
	const localDate = new Date(epochMs + offsetMs);

	return formatMonthKey(localDate.getUTCFullYear(), localDate.getUTCMonth() + 1);
}

export function previousMonthKey(monthKey: string): string {
	return shiftMonthKey(monthKey, -1);
}

export function nextMonthKey(monthKey: string): string {
	return shiftMonthKey(monthKey, 1);
}

export function compareMonthKeys(left: string, right: string): number {
	const leftMonth = parseMonthKey(left);
	const rightMonth = parseMonthKey(right);
	return leftMonth.year * 12 + leftMonth.month - (rightMonth.year * 12 + rightMonth.month);
}

function shiftMonthKey(monthKey: string, delta: -1 | 1): string {
	const { year, month } = parseMonthKey(monthKey);
	const shiftedIndex = year * 12 + (month - 1) + delta;
	const shiftedYear = Math.floor(shiftedIndex / 12);
	const shiftedMonth = (shiftedIndex % 12) + 1;

	if (shiftedYear < 1 || shiftedYear > 9999) {
		throw new RangeError("Shifted month key is outside the supported range.");
	}

	return formatMonthKey(shiftedYear, shiftedMonth);
}

function parseMonthKey(monthKey: string): { year: number; month: number } {
	const match = /^([0-9]{4})-(0[1-9]|1[0-2])$/.exec(monthKey);
	if (match === null || match[1] === "0000") {
		throw new RangeError("Expected a canonical month key.");
	}

	return { year: Number(match[1]), month: Number(match[2]) };
}

function formatMonthKey(year: number, month: number): string {
	return `${year.toString().padStart(4, "0")}-${month.toString().padStart(2, "0")}`;
}

function isBratislavaSummerTime(epochMs: number, year: number): boolean {
	const startsAt = Date.UTC(year, 2, lastSundayOfMonth(year, 2), 1);
	const endsAt = Date.UTC(year, 9, lastSundayOfMonth(year, 9), 1);
	return epochMs >= startsAt && epochMs < endsAt;
}

function lastSundayOfMonth(year: number, zeroBasedMonth: number): number {
	const lastDate = new Date(Date.UTC(year, zeroBasedMonth + 1, 0));
	return lastDate.getUTCDate() - lastDate.getUTCDay();
}
