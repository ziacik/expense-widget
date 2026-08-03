export const CURRENT_PARSER_VERSION = 1;

export type InboxProcessingStatus = "pending" | "processed" | "unsupported" | "unparsed";

export type ParserVersionState = {
	status: InboxProcessingStatus;
	lastParserVersion: number | null;
};

export function isEligibleForParserVersion(state: ParserVersionState, currentParserVersion: number = CURRENT_PARSER_VERSION): boolean {
	if (!Number.isSafeInteger(currentParserVersion) || currentParserVersion <= 0) {
		throw new RangeError("Current parser version must be a positive integer.");
	}
	if (state.status === "pending") {
		return true;
	}
	if (state.status === "processed") {
		return false;
	}

	return state.lastParserVersion === null || state.lastParserVersion < currentParserVersion;
}
