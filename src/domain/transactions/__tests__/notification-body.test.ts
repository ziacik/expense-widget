import { describe, expect, it } from "vitest";

import { normalizeNotificationBody, selectNotificationBody } from "../notification-body";

describe("normalizeNotificationBody", () => {
	it("normalizes line endings and insignificant whitespace", () => {
		expect(normalizeNotificationBody(" \t\r\n  Suma:\t 123,45 EUR \rBILLA   140\r\n\t \r\n")).toBe("Suma: 123,45 EUR\nBILLA 140");
	});

	it("preserves blank lines inside the body", () => {
		expect(normalizeNotificationBody("first\n \t\nthird")).toBe("first\n\nthird");
	});
});

describe("selectNotificationBody", () => {
	it("prefers more non-empty lines, then normalized length", () => {
		expect(
			selectNotificationBody({
				bigText: "one\ntwo\nthree",
				textLines: ["short", "body"],
				text: "single line with more characters",
			}),
		).toBe("one\ntwo\nthree");

		expect(
			selectNotificationBody({
				bigText: "one\nx",
				textLines: ["one", "longer"],
				text: null,
			}),
		).toBe("one\nlonger");
	});

	it("uses big text, text lines, and text as the fixed tie order", () => {
		expect(
			selectNotificationBody({
				bigText: "AA",
				textLines: ["BB"],
				text: "CC",
			}),
		).toBe("AA");

		expect(
			selectNotificationBody({
				bigText: null,
				textLines: ["BB"],
				text: "CC",
			}),
		).toBe("BB");
	});

	it("returns null when every representation is absent or empty", () => {
		expect(
			selectNotificationBody({
				bigText: " \n\t",
				textLines: [],
				text: null,
			}),
		).toBeNull();
	});
});
