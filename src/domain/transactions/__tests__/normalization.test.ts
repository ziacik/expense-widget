import { describe, expect, it } from "vitest";

import { normalizeBankLocalDateTime, normalizeMerchant, parseEuroMinor } from "../normalization";

describe("parseEuroMinor", () => {
	it("parses decimal euro values directly into integer minor units", () => {
		expect(parseEuroMinor("123,45")).toBe(12345);
		expect(parseEuroMinor("0001,05")).toBe(105);
		expect(parseEuroMinor("0,00")).toBe(0);
	});

	it("rejects unsupported syntax and unsafe integer results", () => {
		expect(parseEuroMinor("-1,00")).toBeNull();
		expect(parseEuroMinor("1 234,56")).toBeNull();
		expect(parseEuroMinor("1,5")).toBeNull();
		expect(parseEuroMinor("١,٠٠")).toBeNull();
		expect(parseEuroMinor("90071992547410,00")).toBeNull();
	});
});

describe("normalizeBankLocalDateTime", () => {
	it("normalizes a valid bank timestamp and derives its month", () => {
		expect(normalizeBankLocalDateTime("02.08.2026 19:21")).toEqual({
			occurredAtLocal: "2026-08-02T19:21:00",
			monthKey: "2026-08",
		});
		expect(normalizeBankLocalDateTime("29.02.2024 00:00")).toEqual({
			occurredAtLocal: "2024-02-29T00:00:00",
			monthKey: "2024-02",
		});
	});

	it("rejects invalid Gregorian dates, times, and field widths", () => {
		expect(normalizeBankLocalDateTime("29.02.2026 19:21")).toBeNull();
		expect(normalizeBankLocalDateTime("31.04.2026 19:21")).toBeNull();
		expect(normalizeBankLocalDateTime("02.08.2026 24:00")).toBeNull();
		expect(normalizeBankLocalDateTime("2.08.2026 09:00")).toBeNull();
		expect(normalizeBankLocalDateTime("02.08.26 09:00")).toBeNull();
	});
});

describe("normalizeMerchant", () => {
	it("trims and collapses spaces and tabs while preserving display case", () => {
		expect(normalizeMerchant(" \tBilla\t 140  ")).toBe("Billa 140");
	});
});
