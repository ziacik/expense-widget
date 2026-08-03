export type InboxId = string;

export type BankNotificationEnvelope = {
	inboxId: InboxId;
	notificationKey: string;
	packageName: string;
	postedAtMs: number;
	capturedAtMs: number;
	title: string | null;
	text: string | null;
	bigText: string | null;
	textLines: string[];
};

export type CardExpense = {
	source: "csob-sk-smartbanking";
	kind: "card-expense";
	sourceNotificationKey: string;
	sourceEventKey: string;
	semanticCandidateKey: string;
	amountMinor: number;
	currency: "EUR";
	merchant: string;
	occurredAtLocal: string;
	timeZone: "Europe/Bratislava";
	monthKey: string;
	cardLast4: string;
};

export type SemanticCandidateFields = Pick<CardExpense, "source" | "kind" | "occurredAtLocal" | "amountMinor" | "currency" | "merchant" | "cardLast4">;
