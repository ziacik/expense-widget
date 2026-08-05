import { NativeModule, requireNativeModule } from "expo";

import type { BankNotificationEnvelope, CardExpense, InboxId } from "../../../src/domain/transactions/types";

export type NotificationAccessStatus = { granted: boolean };

export type OpenSettingsResult = { opened: true } | { opened: false; reason: "settings_unavailable" };

export type EligibleInboxBatch = {
	items: BankNotificationEnvelope[];
	hasMore: boolean;
};

export type InboxCompletion =
	| {
			outcome: "processed";
			inboxIds: [InboxId, ...InboxId[]];
			parserVersion: number;
			expense: CardExpense;
	  }
	| {
			outcome: "unsupported";
			inboxIds: [InboxId, ...InboxId[]];
			sourceEventKey: string;
			parserVersion: number;
			errorCode: "unsupported_package" | "unsupported_title";
	  }
	| {
			outcome: "unparsed";
			inboxIds: [InboxId, ...InboxId[]];
			sourceEventKey: string;
			parserVersion: number;
			errorCode:
				"missing_body" | "invalid_body_shape" | "invalid_amount" | "unsupported_amount" | "invalid_datetime" | "invalid_card" | "invalid_balance";
	  };

export type CompletionResult =
	{ outcome: "processed"; transactionId: string; inserted: boolean } | { outcome: "unsupported" | "unparsed"; transactionId: null; inserted: false };

export type DrainRequestResult = {
	disposition: "started" | "pending_after_start_failure";
};

export type MonthlyBudget = {
	amountMinor: number | null;
};

export type MonthTransaction = CardExpense & { transactionId: string };

export type MonthTransactions = {
	monthKey: string;
	items: MonthTransaction[];
};

export type ExpenseDiagnostics = {
	pending: number;
	unsupported: number;
	unparsed: number;
	possibleDuplicateGroups: number;
	reusedNotificationKeys: number;
	projectionError: boolean;
};

type ExpenseNotificationsEvents = {
	onExpenseDataChanged(event: { monthKeys: string[] }): void;
	onNotificationAccessChanged(event: NotificationAccessStatus): void;
};

declare class ExpenseNotificationsModule extends NativeModule<ExpenseNotificationsEvents> {
	getNotificationAccessStatusAsync(): Promise<NotificationAccessStatus>;
	openNotificationAccessSettingsAsync(): Promise<OpenSettingsResult>;
	getEligibleInboxBatchAsync(parserVersion: number, limit: number): Promise<EligibleInboxBatch>;
	completeInboxItemsAsync(completion: InboxCompletion): Promise<CompletionResult>;
	requestInboxDrainAsync(): Promise<DrainRequestResult>;
	getMonthlyBudgetAsync(): Promise<MonthlyBudget>;
	setMonthlyBudgetAsync(amountMinor: number): Promise<MonthlyBudget>;
	getMonthTransactionsAsync(monthKey: string): Promise<MonthTransactions>;
	getDiagnosticsAsync(): Promise<ExpenseDiagnostics>;
}

export default requireNativeModule<ExpenseNotificationsModule>("ExpenseNotifications");
