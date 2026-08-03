import { describe, expect, it } from "vitest";

import { compareMonthKeys, getBratislavaMonthKey, nextMonthKey, previousMonthKey } from "../month-selection";

describe("getBratislavaMonthKey", () => {
	it("uses Bratislava summer time at a UTC month boundary", () => {
		expect(getBratislavaMonthKey(Date.UTC(2026, 6, 31, 22, 30))).toBe("2026-08");
	});

	it("uses Bratislava winter time at a UTC year boundary", () => {
		expect(getBratislavaMonthKey(Date.UTC(2026, 11, 31, 23, 30))).toBe("2027-01");
	});
});

describe("month navigation", () => {
	it("moves across year boundaries", () => {
		expect(previousMonthKey("2026-01")).toBe("2025-12");
		expect(nextMonthKey("2026-12")).toBe("2027-01");
	});

	it("compares canonical month keys chronologically", () => {
		expect(compareMonthKeys("2026-07", "2026-08")).toBeLessThan(0);
		expect(compareMonthKeys("2026-08", "2026-08")).toBe(0);
		expect(compareMonthKeys("2027-01", "2026-12")).toBeGreaterThan(0);
	});

	it("rejects non-canonical month keys", () => {
		expect(() => nextMonthKey("2026-8")).toThrow("month key");
		expect(() => previousMonthKey("2026-13")).toThrow("month key");
	});
});
