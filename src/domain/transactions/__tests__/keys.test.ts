import { describe, expect, it } from "vitest";

import { buildSemanticCandidateKey, buildSourceEventKey } from "../keys";
import type { BankNotificationEnvelope } from "../types";

const envelope: BankNotificationEnvelope = {
	inboxId: "capture-1",
	notificationKey: "0|com.zentity.sbank.csobsk|42|null|10001",
	packageName: "com.zentity.sbank.csobsk",
	postedAtMs: 1785691260000,
	capturedAtMs: 1785691261234,
	title: "Transakcia kartou",
	text: null,
	bigText: "",
	textLines: ["Suma: 123,45 EUR", "BILLA 140", "BILLA 140"],
};

describe("buildSourceEventKey", () => {
	it("encodes the exact source event fields in fixed order", () => {
		expect(buildSourceEventKey(envelope)).toBe(
			JSON.stringify([
				"source-event-v1",
				"com.zentity.sbank.csobsk",
				"0|com.zentity.sbank.csobsk|42|null|10001",
				1785691260000,
				"Transakcia kartou",
				null,
				"",
				["Suma: 123,45 EUR", "BILLA 140", "BILLA 140"],
			]),
		);
	});

	it("ignores capture identity and capture time", () => {
		expect(
			buildSourceEventKey({
				...envelope,
				inboxId: "capture-2",
				capturedAtMs: envelope.capturedAtMs + 5000,
			}),
		).toBe(buildSourceEventKey(envelope));
	});

	it("keeps null, empty strings, line order, and duplicate lines significant", () => {
		expect(buildSourceEventKey({ ...envelope, text: "" })).not.toBe(buildSourceEventKey(envelope));
		expect(
			buildSourceEventKey({
				...envelope,
				textLines: ["BILLA 140", "Suma: 123,45 EUR", "BILLA 140"],
			}),
		).not.toBe(buildSourceEventKey(envelope));
	});
});

describe("buildSemanticCandidateKey", () => {
	it("encodes normalized transaction fields without becoming a hard dedupe key", () => {
		expect(
			buildSemanticCandidateKey({
				source: "csob-sk-smartbanking",
				kind: "card-expense",
				occurredAtLocal: "2026-08-02T19:21:00",
				amountMinor: 12345,
				currency: "EUR",
				merchant: "BILLA 140",
				cardLast4: "8794",
			}),
		).toBe(JSON.stringify(["semantic-candidate-v1", "csob-sk-smartbanking", "card-expense", "2026-08-02T19:21:00", 12345, "EUR", "BILLA 140", "8794"]));
	});
});
