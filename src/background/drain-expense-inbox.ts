import ExpenseNotifications from "../../modules/expense-notifications";
import type { InboxCompletion } from "../../modules/expense-notifications";
import { parseCsobCardNotification } from "@/domain/transactions/csob-card-parser";
import { buildSourceEventKey } from "@/domain/transactions/keys";
import { CURRENT_PARSER_VERSION } from "@/domain/transactions/parser-version";
import type { BankNotificationEnvelope } from "@/domain/transactions/types";
import { createDrainCoordinator, drainAllBatches } from "./drain-coordinator";

const BATCH_LIMIT = 50;

const coordinatedDrain = createDrainCoordinator(drainEligibleBatches);

export function drainExpenseInbox(): Promise<void> {
	return coordinatedDrain();
}

async function drainEligibleBatches(): Promise<void> {
	await drainAllBatches(
		() => ExpenseNotifications.getEligibleInboxBatchAsync(CURRENT_PARSER_VERSION, BATCH_LIMIT),
		async (items) => {
			for (const group of groupBySourceEvent(items)) {
				await ExpenseNotifications.completeInboxItemsAsync(createCompletion(group));
			}
		},
	);
}

function groupBySourceEvent(items: BankNotificationEnvelope[]): BankNotificationEnvelope[][] {
	const groups = new Map<string, BankNotificationEnvelope[]>();

	for (const item of items) {
		const sourceEventKey = buildSourceEventKey(item);
		const group = groups.get(sourceEventKey);
		if (group === undefined) {
			groups.set(sourceEventKey, [item]);
		} else {
			group.push(item);
		}
	}

	return [...groups.values()];
}

function createCompletion(group: BankNotificationEnvelope[]): InboxCompletion {
	const firstItem = group[0];
	const inboxIds = group.map((item) => item.inboxId) as [string, ...string[]];
	const result = parseCsobCardNotification(firstItem);

	if (result.outcome === "parsed") {
		return {
			outcome: "processed",
			inboxIds,
			parserVersion: CURRENT_PARSER_VERSION,
			expense: result.expense,
		};
	}

	const failedCompletion = {
		inboxIds,
		sourceEventKey: buildSourceEventKey(firstItem),
		parserVersion: CURRENT_PARSER_VERSION,
	};

	if (result.outcome === "unsupported") {
		return { ...failedCompletion, outcome: "unsupported", errorCode: result.code };
	}

	return { ...failedCompletion, outcome: "unparsed", errorCode: result.code };
}
