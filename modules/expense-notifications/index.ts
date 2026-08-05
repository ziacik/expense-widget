// Re-export the native module. On web, it will be resolved to ExpenseNotificationsModule.web.ts
// and on native platforms to ExpenseNotificationsModule.ts
export { default } from "./src/ExpenseNotificationsModule";
export type {
	CompletionResult,
	DrainRequestResult,
	EligibleInboxBatch,
	ExpenseDiagnostics,
	InboxCompletion,
	MonthlyBudget,
	MonthTransaction,
	MonthTransactions,
	NotificationAccessStatus,
	OpenSettingsResult,
} from "./src/ExpenseNotificationsModule";
