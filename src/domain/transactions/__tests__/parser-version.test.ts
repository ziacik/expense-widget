import { describe, expect, it } from "vitest";

import { CURRENT_PARSER_VERSION, isEligibleForParserVersion } from "../parser-version";

describe("parser version eligibility", () => {
	it("starts with a positive parser version", () => {
		expect(CURRENT_PARSER_VERSION).toBe(2);
	});

	it("always accepts pending rows and never automatically reparses processed rows", () => {
		expect(isEligibleForParserVersion({ status: "pending", lastParserVersion: 1 }, 1)).toBe(true);
		expect(isEligibleForParserVersion({ status: "processed", lastParserVersion: 0 }, 2)).toBe(false);
	});

	it.each(["unsupported", "unparsed"] as const)("retries %s rows only with a newer parser", (status) => {
		expect(isEligibleForParserVersion({ status, lastParserVersion: null }, 1)).toBe(true);
		expect(isEligibleForParserVersion({ status, lastParserVersion: 1 }, 1)).toBe(false);
		expect(isEligibleForParserVersion({ status, lastParserVersion: 1 }, 2)).toBe(true);
	});

	it("rejects invalid current parser versions", () => {
		expect(() => isEligibleForParserVersion({ status: "pending", lastParserVersion: null }, 0)).toThrow("positive integer");
	});
});
