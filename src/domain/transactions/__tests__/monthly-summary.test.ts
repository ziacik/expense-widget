import { describe, expect, it } from "vitest";

import { summarizeMonth } from "../monthly-summary";

describe("summarizeMonth", () => {
	it("sums only EUR card expenses in the selected month", () => {
		expect(
			summarizeMonth(
				[
					{
						kind: "card-expense",
						monthKey: "2026-08",
						currency: "EUR",
						amountMinor: 12345,
					},
					{
						kind: "card-expense",
						monthKey: "2026-08",
						currency: "EUR",
						amountMinor: 55,
					},
					{
						kind: "card-expense",
						monthKey: "2026-07",
						currency: "EUR",
						amountMinor: 99999,
					},
					{
						kind: "transfer",
						monthKey: "2026-08",
						currency: "EUR",
						amountMinor: 4000,
					},
					{
						kind: "card-expense",
						monthKey: "2026-08",
						currency: "USD",
						amountMinor: 7000,
					},
				],
				"2026-08",
			),
		).toEqual({ totalMinor: 12400, transactionCount: 2 });
	});

	it("returns zero values for an empty month", () => {
		expect(summarizeMonth([], "2026-08")).toEqual({
			totalMinor: 0,
			transactionCount: 0,
		});
	});

	it.each([-1, 12.5, Number.MAX_SAFE_INTEGER + 1])("rejects an invalid included amount: %s", (amountMinor) => {
		expect(() =>
			summarizeMonth(
				[
					{
						kind: "card-expense",
						monthKey: "2026-08",
						currency: "EUR",
						amountMinor,
					},
				],
				"2026-08",
			),
		).toThrow("non-negative safe integer");
	});

	it("throws rather than overflowing the monthly total", () => {
		expect(() =>
			summarizeMonth(
				[
					{
						kind: "card-expense",
						monthKey: "2026-08",
						currency: "EUR",
						amountMinor: Number.MAX_SAFE_INTEGER,
					},
					{
						kind: "card-expense",
						monthKey: "2026-08",
						currency: "EUR",
						amountMinor: 1,
					},
				],
				"2026-08",
			),
		).toThrow("overflow");
	});
});
