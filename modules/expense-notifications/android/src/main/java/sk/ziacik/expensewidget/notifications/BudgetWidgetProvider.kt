package sk.ziacik.expensewidget.notifications

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

class BudgetWidgetProvider : AppWidgetProvider() {
	override fun onUpdate(
		context: Context,
		appWidgetManager: AppWidgetManager,
		appWidgetIds: IntArray,
	) {
		requestAsyncUpdate(context, appWidgetIds)
	}

	override fun onAppWidgetOptionsChanged(
		context: Context,
		appWidgetManager: AppWidgetManager,
		appWidgetId: Int,
		newOptions: Bundle,
	) {
		super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
		requestAsyncUpdate(context, intArrayOf(appWidgetId))
	}

	override fun onEnabled(context: Context) {
		super.onEnabled(context)
		ExpenseWidgetRolloverScheduler.scheduleNextIfNeeded(context)
	}

	override fun onDisabled(context: Context) {
		ExpenseWidgetRolloverScheduler.scheduleNextIfNeeded(context)
		super.onDisabled(context)
	}

	private fun requestAsyncUpdate(context: Context, appWidgetIds: IntArray) {
		val pendingResult = goAsync()
		ExpenseWidgetRenderer.requestUpdate(
			context = context,
			appWidgetIds = appWidgetIds,
			widgetKind = ExpenseWidgetKind.BUDGET,
			onComplete = pendingResult::finish,
		)
	}
}
