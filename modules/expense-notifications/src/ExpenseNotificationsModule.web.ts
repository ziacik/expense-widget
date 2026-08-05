import { NativeModule, registerWebModule } from "expo";

class ExpenseNotificationsModule extends NativeModule {
	async getNotificationAccessStatusAsync(): Promise<never> {
		throw new Error("Expense notifications are available only on Android.");
	}

	async openNotificationAccessSettingsAsync(): Promise<never> {
		throw new Error("Expense notifications are available only on Android.");
	}

	async getEligibleInboxBatchAsync(): Promise<never> {
		throw new Error("Expense notifications are available only on Android.");
	}

	async completeInboxItemsAsync(): Promise<never> {
		throw new Error("Expense notifications are available only on Android.");
	}

	async requestInboxDrainAsync(): Promise<never> {
		throw new Error("Expense notifications are available only on Android.");
	}

	async getMonthlyBudgetAsync(): Promise<never> {
		throw new Error("Expense notifications are available only on Android.");
	}

	async setMonthlyBudgetAsync(): Promise<never> {
		throw new Error("Expense notifications are available only on Android.");
	}

	async getMonthTransactionsAsync(): Promise<never> {
		throw new Error("Expense notifications are available only on Android.");
	}

	async getDiagnosticsAsync(): Promise<never> {
		throw new Error("Expense notifications are available only on Android.");
	}
}

export default registerWebModule(ExpenseNotificationsModule, "ExpenseNotifications");
