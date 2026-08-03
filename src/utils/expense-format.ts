const SLOVAK_MONTH_NAMES = ["Január", "Február", "Marec", "Apríl", "Máj", "Jún", "Júl", "August", "September", "Október", "November", "December"] as const;

export function formatEuroMinor(amountMinor: number): string {
	assertNonNegativeSafeInteger(amountMinor, "Amount");
	const major = Math.floor(amountMinor / 100);
	const minor = (amountMinor % 100).toString().padStart(2, "0");
	return `${major},${minor} €`;
}

export function formatExpenseMinor(amountMinor: number): string {
	return `−${formatEuroMinor(amountMinor)}`;
}

export function formatMonthLabel(monthKey: string): string {
	const match = /^([0-9]{4})-(0[1-9]|1[0-2])$/.exec(monthKey);
	if (match === null || match[1] === "0000") {
		throw new RangeError("Expected a canonical month key.");
	}

	return `${SLOVAK_MONTH_NAMES[Number(match[2]) - 1]} ${match[1]}`;
}

export function formatTransactionTimestamp(occurredAtLocal: string): string {
	const match = /^([0-9]{4})-([0-9]{2})-([0-9]{2})T([0-9]{2}):([0-9]{2}):00$/.exec(occurredAtLocal);
	if (match === null) {
		throw new RangeError("Expected a normalized transaction timestamp.");
	}

	return `${match[3]}. ${match[2]}. ${match[1]} · ${match[4]}:${match[5]}`;
}

export function formatTransactionCount(count: number): string {
	assertNonNegativeSafeInteger(count, "Transaction count");
	if (count === 1) {
		return "1 transakcia";
	}
	if (count >= 2 && count <= 4) {
		return `${count} transakcie`;
	}
	return `${count} transakcií`;
}

function assertNonNegativeSafeInteger(value: number, label: string): void {
	if (!Number.isSafeInteger(value) || value < 0) {
		throw new RangeError(`${label} must be a non-negative safe integer.`);
	}
}
