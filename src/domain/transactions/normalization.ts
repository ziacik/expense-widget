export type NormalizedBankLocalDateTime = {
	occurredAtLocal: string;
	monthKey: string;
};

export function parseEuroMinor(rawAmount: string): number | null {
	const match = /^([0-9]+),([0-9]{2})$/.exec(rawAmount);
	if (match === null) {
		return null;
	}

	const major = Number(match[1]);
	const minor = Number(match[2]);
	const amountMinor = major * 100 + minor;

	return Number.isSafeInteger(amountMinor) ? amountMinor : null;
}

export function normalizeBankLocalDateTime(rawDateTime: string): NormalizedBankLocalDateTime | null {
	const match = /^([0-9]{2})\.([0-9]{2})\.([0-9]{4}) ([0-9]{2}):([0-9]{2})$/.exec(rawDateTime);
	if (match === null) {
		return null;
	}

	const [, dayText, monthText, yearText, hourText, minuteText] = match;
	const day = Number(dayText);
	const month = Number(monthText);
	const year = Number(yearText);
	const hour = Number(hourText);
	const minute = Number(minuteText);

	if (year < 1 || month < 1 || month > 12 || hour > 23 || minute > 59 || day < 1 || day > daysInMonth(year, month)) {
		return null;
	}

	return {
		occurredAtLocal: `${yearText}-${monthText}-${dayText}T${hourText}:${minuteText}:00`,
		monthKey: `${yearText}-${monthText}`,
	};
}

export function normalizeMerchant(rawMerchant: string): string {
	return rawMerchant.trim().replace(/[ \t]+/g, " ");
}

function daysInMonth(year: number, month: number): number {
	if (month === 2) {
		return isLeapYear(year) ? 29 : 28;
	}

	return [4, 6, 9, 11].includes(month) ? 30 : 31;
}

function isLeapYear(year: number): boolean {
	return year % 400 === 0 || (year % 4 === 0 && year % 100 !== 0);
}
