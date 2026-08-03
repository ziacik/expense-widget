export type SummarizableTransaction = {
	kind: string;
	monthKey: string;
	currency: string;
	amountMinor: number;
};

export type MonthlySummary = {
	totalMinor: number;
	transactionCount: number;
};

export function summarizeMonth(transactions: readonly SummarizableTransaction[], monthKey: string): MonthlySummary {
	let totalMinor = 0;
	let transactionCount = 0;

	for (const transaction of transactions) {
		if (transaction.kind !== "card-expense" || transaction.monthKey !== monthKey || transaction.currency !== "EUR") {
			continue;
		}
		if (!Number.isSafeInteger(transaction.amountMinor) || transaction.amountMinor < 0) {
			throw new RangeError("Amount must be a non-negative safe integer.");
		}
		if (totalMinor > Number.MAX_SAFE_INTEGER - transaction.amountMinor) {
			throw new RangeError("Monthly total overflow.");
		}

		totalMinor += transaction.amountMinor;
		transactionCount += 1;
	}

	return { totalMinor, transactionCount };
}
