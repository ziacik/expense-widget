import { AppRegistry, Platform } from "react-native";

import { drainExpenseInbox } from "./drain-expense-inbox";

const EXPENSE_INBOX_DRAIN_TASK = "ExpenseInboxDrain";

if (Platform.OS === "android") {
	AppRegistry.registerHeadlessTask(EXPENSE_INBOX_DRAIN_TASK, () => drainExpenseInbox);
}
