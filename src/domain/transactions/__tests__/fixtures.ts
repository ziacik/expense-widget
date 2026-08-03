import type { BankNotificationEnvelope } from "../types";

export const CSOB_CARD_BODY = [
	"Suma: 123,45 EUR",
	"BILLA 140",
	"02.08.2026 19:21",
	"Karta **** 8794",
	"Disponibilný zostatok 2345,67 EUR",
	"Vlastné prostriedky -1234,56 EUR",
].join("\n");

export function makeBankNotificationEnvelope(overrides: Partial<BankNotificationEnvelope> = {}): BankNotificationEnvelope {
	return {
		inboxId: "inbox-1",
		notificationKey: "0|com.zentity.sbank.csobsk|42|null|10001",
		packageName: "com.zentity.sbank.csobsk",
		postedAtMs: 1785691260000,
		capturedAtMs: 1785691261234,
		title: "Transakcia kartou",
		text: "Suma: 123,45 EUR",
		bigText: CSOB_CARD_BODY,
		textLines: [],
		...overrides,
	};
}
