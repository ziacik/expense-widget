import type { BankNotificationEnvelope, SemanticCandidateFields } from "./types";

export function buildSourceEventKey(envelope: BankNotificationEnvelope): string {
	return JSON.stringify([
		"source-event-v1",
		envelope.packageName,
		envelope.notificationKey,
		envelope.postedAtMs,
		envelope.title,
		envelope.text,
		envelope.bigText,
		envelope.textLines,
	]);
}

export function buildSemanticCandidateKey(fields: SemanticCandidateFields): string {
	return JSON.stringify([
		"semantic-candidate-v1",
		fields.source,
		fields.kind,
		fields.occurredAtLocal,
		fields.amountMinor,
		fields.currency,
		fields.merchant,
		fields.cardLast4,
	]);
}
