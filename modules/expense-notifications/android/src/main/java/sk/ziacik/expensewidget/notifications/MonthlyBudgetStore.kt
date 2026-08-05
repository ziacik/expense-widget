package sk.ziacik.expensewidget.notifications

import android.content.Context

internal class MonthlyBudgetStore(context: Context) {
	private val preferences =
		context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

	fun get(): Long? {
		if (!preferences.contains(MONTHLY_BUDGET_KEY)) {
			return null
		}

		return preferences.getLong(MONTHLY_BUDGET_KEY, 0L).takeIf { amountMinor ->
			amountMinor in 1..ExpenseDatabase.MAX_SAFE_INTEGER
		}
	}

	fun set(amountMinor: Long): Long {
		require(amountMinor in 1..ExpenseDatabase.MAX_SAFE_INTEGER) {
			"Monthly budget must be a positive safe integer."
		}
		check(preferences.edit().putLong(MONTHLY_BUDGET_KEY, amountMinor).commit()) {
			"Could not persist the monthly budget."
		}
		return amountMinor
	}

	companion object {
		private const val PREFERENCES_NAME = "expense_widget_preferences"
		private const val MONTHLY_BUDGET_KEY = "monthly_budget_minor"
	}
}
