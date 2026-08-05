export type MonthlyBudgetProgress = {
	spentMinor: number;
	budgetMinor: number;
	remainingMinor: number;
	percentage: number;
	progress: number;
};

export function parseMonthlyBudgetInput(rawValue: string): number | null {
	const normalizedValue = rawValue.trim().replace(/[\u00a0\u202f]/g, " ");
	const match = /^([0-9]+|[0-9]{1,3}(?: [0-9]{3})+)(?:[,.]([0-9]{1,2}))?$/.exec(normalizedValue);
	if (match === null) {
		return null;
	}

	const major = Number(match[1].replaceAll(" ", ""));
	const minor = Number((match[2] ?? "").padEnd(2, "0"));
	const amountMinor = major * 100 + minor;

	return Number.isSafeInteger(amountMinor) && amountMinor > 0 ? amountMinor : null;
}

export function calculateMonthlyBudgetProgress(spentMinor: number, budgetMinor: number): MonthlyBudgetProgress {
	assertNonNegativeSafeInteger(spentMinor, "Spent amount");
	assertPositiveSafeInteger(budgetMinor, "Monthly budget");

	const ratio = spentMinor / budgetMinor;
	return {
		spentMinor,
		budgetMinor,
		remainingMinor: budgetMinor - spentMinor,
		percentage: Math.min(Math.round(ratio * 100), Number.MAX_SAFE_INTEGER),
		progress: Math.min(ratio, 1),
	};
}

function assertNonNegativeSafeInteger(value: number, label: string): void {
	if (!Number.isSafeInteger(value) || value < 0) {
		throw new RangeError(`${label} must be a non-negative safe integer.`);
	}
}

function assertPositiveSafeInteger(value: number, label: string): void {
	if (!Number.isSafeInteger(value) || value <= 0) {
		throw new RangeError(`${label} must be a positive safe integer.`);
	}
}
