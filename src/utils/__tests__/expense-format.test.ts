import { describe, expect, it } from "vitest";

import { formatEuroInput, formatEuroMinor, formatExpenseMinor, formatMonthLabel, formatTransactionCount, formatTransactionTimestamp } from "../expense-format";

describe("expense formatting", () => {
	it("formats integer minor units without floating-point arithmetic", () => {
		expect(formatEuroMinor(0)).toBe("0,00 €");
		expect(formatEuroMinor(5)).toBe("0,05 €");
		expect(formatEuroMinor(12345)).toBe("123,45 €");
		expect(formatExpenseMinor(12345)).toBe("−123,45 €");
	});

	it("formats an existing amount for localized euro input", () => {
		expect(formatEuroInput(50000)).toBe("500,00");
		expect(formatEuroInput(120005)).toBe("1200,05");
	});

	it("rejects invalid minor-unit values", () => {
		expect(() => formatEuroMinor(-1)).toThrow("non-negative safe integer");
		expect(() => formatEuroMinor(1.5)).toThrow("non-negative safe integer");
	});

	it("formats canonical month and bank timestamp values", () => {
		expect(formatMonthLabel("2026-08")).toBe("August 2026");
		expect(formatTransactionTimestamp("2026-08-02T19:21:00")).toBe("02. 08. 2026 · 19:21");
	});

	it.each([
		[0, "0 transakcií"],
		[1, "1 transakcia"],
		[2, "2 transakcie"],
		[4, "4 transakcie"],
		[5, "5 transakcií"],
		[21, "21 transakcií"],
	] as const)("formats the transaction count %s", (count, expected) => {
		expect(formatTransactionCount(count)).toBe(expected);
	});
});
