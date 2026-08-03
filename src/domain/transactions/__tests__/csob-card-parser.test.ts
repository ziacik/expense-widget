import { describe, expect, it } from "vitest";

import { parseCsobCardNotification } from "../csob-card-parser";
import { buildSourceEventKey } from "../keys";
import type { BankNotificationEnvelope, ParserErrorCode } from "../types";
import { CSOB_CARD_BODY, makeBankNotificationEnvelope } from "./fixtures";

describe("parseCsobCardNotification", () => {
	it("parses the supplied ČSOB card notification", () => {
		const envelope = makeBankNotificationEnvelope();
		const sourceEventKey = buildSourceEventKey(envelope);

		expect(parseCsobCardNotification(envelope)).toEqual({
			outcome: "parsed",
			expense: {
				source: "csob-sk-smartbanking",
				kind: "card-expense",
				sourceNotificationKey: envelope.notificationKey,
				sourceEventKey,
				semanticCandidateKey: JSON.stringify([
					"semantic-candidate-v1",
					"csob-sk-smartbanking",
					"card-expense",
					"2026-08-02T19:21:00",
					12345,
					"EUR",
					"BILLA 140",
					"8794",
				]),
				amountMinor: 12345,
				currency: "EUR",
				merchant: "BILLA 140",
				occurredAtLocal: "2026-08-02T19:21:00",
				timeZone: "Europe/Bratislava",
				monthKey: "2026-08",
				cardLast4: "8794",
			},
		});
	});

	it.each([
		[CSOB_CARD_BODY.split("\n").slice(0, 4)],
		[CSOB_CARD_BODY.split("\n").slice(0, 5)],
		[[...CSOB_CARD_BODY.split("\n").slice(0, 4), "Vlastné prostriedky -1234,56 EUR"]],
	])("accepts an allowed optional balance tail", (lines) => {
		expect(parseCsobCardNotification(makeBankNotificationEnvelope({ bigText: lines.join("\n") })).outcome).toBe("parsed");
	});

	it("returns unsupported outcomes for another package or title", () => {
		expectFailure(makeBankNotificationEnvelope({ packageName: "com.example.bank" }), "unsupported_package", "unsupported");
		expectFailure(makeBankNotificationEnvelope({ title: "Platba kartou" }), "unsupported_title", "unsupported");
	});

	it("reports a missing body when no representation is usable", () => {
		expectFailure(
			makeBankNotificationEnvelope({
				text: null,
				bigText: " \n\t",
				textLines: [],
			}),
			"missing_body",
		);
	});

	it.each([
		["", "invalid_body_shape"],
		[["Suma: 123,45 EUR", "BILLA 140", "", "02.08.2026 19:21", "Karta **** 8794"].join("\n"), "invalid_body_shape"],
		[[...CSOB_CARD_BODY.split("\n").slice(0, 4), "Vlastné prostriedky -1234,56 EUR", "Disponibilný zostatok 2345,67 EUR"].join("\n"), "invalid_body_shape"],
		[`${CSOB_CARD_BODY}\nNeznámy údaj`, "invalid_body_shape"],
		[["Karta **** 8794", "BILLA 140", "02.08.2026 19:21", "Suma: 123,45 EUR"].join("\n"), "invalid_body_shape"],
		[CSOB_CARD_BODY.replace("123,45 EUR", "123.45 EUR"), "invalid_amount"],
		[CSOB_CARD_BODY.replace("123,45 EUR", "-123,45 EUR"), "invalid_amount"],
		[CSOB_CARD_BODY.replace("123,45 EUR", "123,45 USD"), "invalid_amount"],
		[CSOB_CARD_BODY.replace("02.08.2026", "31.02.2026"), "invalid_datetime"],
		[CSOB_CARD_BODY.replace("Karta **** 8794", "Karta *** 8794"), "invalid_card"],
		[CSOB_CARD_BODY.replace("Disponibilný zostatok 2345,67 EUR", "Disponibilný zostatok 2345.67 EUR"), "invalid_balance"],
	] as const)("classifies an invalid body deterministically", (body, code) => {
		expectFailure(makeBankNotificationEnvelope({ bigText: body }), code);
	});

	it("rejects a syntactically valid zero amount as unsupported", () => {
		expectFailure(
			makeBankNotificationEnvelope({
				bigText: CSOB_CARD_BODY.replace("123,45 EUR", "0,00 EUR"),
			}),
			"unsupported_amount",
		);
	});
});

function expectFailure(envelope: BankNotificationEnvelope, code: ParserErrorCode, outcome: "unsupported" | "unparsed" = "unparsed"): void {
	expect(parseCsobCardNotification(envelope)).toEqual({ outcome, code });
}
