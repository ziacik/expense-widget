export type NotificationBodyFields = {
	bigText: string | null;
	textLines: readonly string[];
	text: string | null;
};

export function normalizeNotificationBody(rawBody: string): string {
	const lines = rawBody
		.replace(/\r\n?/g, "\n")
		.split("\n")
		.map((line) => line.trim().replace(/[ \t]+/g, " "));

	while (lines[0] === "") {
		lines.shift();
	}
	while (lines.at(-1) === "") {
		lines.pop();
	}

	return lines.join("\n");
}

export function selectNotificationBody(fields: NotificationBodyFields): string | null {
	const rawCandidates = [fields.bigText, fields.textLines.join("\n"), fields.text];
	let selectedBody: string | null = null;
	let selectedNonEmptyLineCount = -1;

	for (const rawCandidate of rawCandidates) {
		if (rawCandidate === null) {
			continue;
		}

		const body = normalizeNotificationBody(rawCandidate);
		if (body === "") {
			continue;
		}

		const nonEmptyLineCount = body.split("\n").filter((line) => line !== "").length;
		const isBetterCandidate =
			nonEmptyLineCount > selectedNonEmptyLineCount || (nonEmptyLineCount === selectedNonEmptyLineCount && body.length > (selectedBody?.length ?? -1));

		if (isBetterCandidate) {
			selectedBody = body;
			selectedNonEmptyLineCount = nonEmptyLineCount;
		}
	}

	return selectedBody;
}
