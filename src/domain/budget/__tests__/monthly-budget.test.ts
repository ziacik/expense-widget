import { describe, expect, it } from "vitest";

import { calculateMonthlyBudgetProgress, parseMonthlyBudgetInput } from "../monthly-budget";

describe("parseMonthlyBudgetInput", () => {
	it("parses localized euro input into integer minor units", () => {
		expect(parseMonthlyBudgetInput("500")).toBe(50000);
		expect(parseMonthlyBudgetInput("500,50")).toBe(50050);
		expect(parseMonthlyBudgetInput("500.5")).toBe(50050);
		expect(parseMonthlyBudgetInput(" 1 200,05 ")).toBe(120005);
		expect(parseMonthlyBudgetInput("1\u00a0200,05")).toBe(120005);
	});

	it("rejects zero, malformed, negative, and unsafe values", () => {
		expect(parseMonthlyBudgetInput("")).toBeNull();
		expect(parseMonthlyBudgetInput("0")).toBeNull();
		expect(parseMonthlyBudgetInput("0,00")).toBeNull();
		expect(parseMonthlyBudgetInput("-100")).toBeNull();
		expect(parseMonthlyBudgetInput("1 20,00")).toBeNull();
		expect(parseMonthlyBudgetInput("12,345")).toBeNull();
		expect(parseMonthlyBudgetInput("500 EUR")).toBeNull();
		expect(parseMonthlyBudgetInput("90071992547410,00")).toBeNull();
	});
});

describe("calculateMonthlyBudgetProgress", () => {
	it("calculates the rounded percentage and remaining amount", () => {
		expect(calculateMonthlyBudgetProgress(3434, 50000)).toEqual({
			spentMinor: 3434,
			budgetMinor: 50000,
			remainingMinor: 46566,
			percentage: 7,
			progress: 0.06868,
		});
	});

	it("keeps the displayed percentage above 100 while capping progress", () => {
		expect(calculateMonthlyBudgetProgress(60000, 50000)).toEqual({
			spentMinor: 60000,
			budgetMinor: 50000,
			remainingMinor: -10000,
			percentage: 120,
			progress: 1,
		});
	});

	it("rejects invalid minor-unit values", () => {
		expect(() => calculateMonthlyBudgetProgress(-1, 50000)).toThrow("Spent amount");
		expect(() => calculateMonthlyBudgetProgress(1000, 0)).toThrow("Monthly budget");
		expect(() => calculateMonthlyBudgetProgress(1.5, 50000)).toThrow("Spent amount");
	});
});
