import { buildSemanticCandidateKey, buildSourceEventKey } from "./keys";
import { normalizeBankLocalDateTime, normalizeMerchant, parseEuroMinor } from "./normalization";
import { selectNotificationBody } from "./notification-body";
import type { BankNotificationEnvelope, CardExpenseParseResult, UnparsedParserErrorCode, UnsupportedParserErrorCode } from "./types";

const CSOB_PACKAGE_NAME = "com.zentity.sbank.csobsk";
const CSOB_CARD_TITLE = "Transakcia kartou";
const AVAILABLE_BALANCE_PREFIX = "Disponibilný zostatok";
const OWN_BALANCE_PREFIX = "Vlastné prostriedky";

export function parseCsobCardNotification(envelope: BankNotificationEnvelope): CardExpenseParseResult {
	if (envelope.packageName !== CSOB_PACKAGE_NAME) {
		return unsupportedFailure("unsupported_package");
	}
	if (envelope.title !== CSOB_CARD_TITLE) {
		return unsupportedFailure("unsupported_title");
	}

	const body = selectNotificationBody(envelope);
	if (body === null) {
		return unparsedFailure("missing_body");
	}

	const lines = body.split("\n");
	if (!hasValidBodyShape(lines)) {
		return unparsedFailure("invalid_body_shape");
	}

	const amountMatch = /^Suma: ([0-9]+,[0-9]{2}) EUR$/.exec(lines[0]);
	if (amountMatch === null) {
		return unparsedFailure("invalid_amount");
	}
	const amountMinor = parseEuroMinor(amountMatch[1]);
	if (amountMinor === null) {
		return unparsedFailure("invalid_amount");
	}
	if (amountMinor === 0) {
		return unparsedFailure("unsupported_amount");
	}

	const merchant = normalizeMerchant(lines[1]);
	if (merchant === "") {
		return unparsedFailure("invalid_body_shape");
	}

	const normalizedDateTime = normalizeBankLocalDateTime(lines[2]);
	if (normalizedDateTime === null) {
		return unparsedFailure("invalid_datetime");
	}

	const cardMatch = /^Karta \*\*\*\* ([0-9]{4})$/.exec(lines[3]);
	if (cardMatch === null) {
		return unparsedFailure("invalid_card");
	}

	if (!lines.slice(4).every(isValidBalanceLine)) {
		return unparsedFailure("invalid_balance");
	}

	const sourceEventKey = buildSourceEventKey(envelope);
	const semanticFields = {
		source: "csob-sk-smartbanking" as const,
		kind: "card-expense" as const,
		occurredAtLocal: normalizedDateTime.occurredAtLocal,
		amountMinor,
		currency: "EUR" as const,
		merchant,
		cardLast4: cardMatch[1],
	};

	return {
		outcome: "parsed",
		expense: {
			...semanticFields,
			sourceNotificationKey: envelope.notificationKey,
			sourceEventKey,
			semanticCandidateKey: buildSemanticCandidateKey(semanticFields),
			timeZone: "Europe/Bratislava",
			monthKey: normalizedDateTime.monthKey,
		},
	};
}

function hasValidBodyShape(lines: string[]): boolean {
	if (lines.length < 4 || lines.length > 6 || lines.some((line) => line === "")) {
		return false;
	}
	if (isReservedNonAmountLine(lines[0])) {
		return false;
	}
	if (isReservedLine(lines[1])) {
		return false;
	}
	if (isReservedNonDateLine(lines[2])) {
		return false;
	}
	if (isReservedNonCardLine(lines[3])) {
		return false;
	}

	const balanceLines = lines.slice(4);
	if (balanceLines.length === 0) {
		return true;
	}
	if (balanceLines.length === 1) {
		return balanceLines[0].startsWith(AVAILABLE_BALANCE_PREFIX) || balanceLines[0].startsWith(OWN_BALANCE_PREFIX);
	}

	return balanceLines[0].startsWith(AVAILABLE_BALANCE_PREFIX) && balanceLines[1].startsWith(OWN_BALANCE_PREFIX);
}

function isReservedLine(line: string): boolean {
	return (
		line.startsWith("Suma:") ||
		looksLikeDateTime(line) ||
		line.startsWith("Karta ") ||
		line.startsWith(AVAILABLE_BALANCE_PREFIX) ||
		line.startsWith(OWN_BALANCE_PREFIX)
	);
}

function isReservedNonAmountLine(line: string): boolean {
	return isReservedLine(line) && !line.startsWith("Suma:");
}

function isReservedNonDateLine(line: string): boolean {
	return isReservedLine(line) && !looksLikeDateTime(line);
}

function isReservedNonCardLine(line: string): boolean {
	return isReservedLine(line) && !line.startsWith("Karta ");
}

function looksLikeDateTime(line: string): boolean {
	return /^[0-9]{2}\.[0-9]{2}\.[0-9]{4} /.test(line);
}

function isValidBalanceLine(line: string): boolean {
	return /^(Disponibilný zostatok|Vlastné prostriedky) -?(?:[0-9]+|[0-9]{1,3}(?: [0-9]{3})+),[0-9]{2} EUR$/.test(line);
}

function unsupportedFailure(code: UnsupportedParserErrorCode): CardExpenseParseResult {
	return { outcome: "unsupported", code };
}

function unparsedFailure(code: UnparsedParserErrorCode): CardExpenseParseResult {
	return { outcome: "unparsed", code };
}
