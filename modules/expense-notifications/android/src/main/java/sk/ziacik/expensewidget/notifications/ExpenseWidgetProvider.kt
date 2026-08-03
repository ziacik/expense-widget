package sk.ziacik.expensewidget.notifications

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

class ExpenseWidgetProvider : AppWidgetProvider() {
	override fun onReceive(context: Context, intent: Intent) {
		when (intent.action) {
			ExpenseWidgetRolloverScheduler.ACTION_MONTH_ROLLOVER,
			Intent.ACTION_BOOT_COMPLETED,
			Intent.ACTION_MY_PACKAGE_REPLACED,
			Intent.ACTION_TIME_CHANGED,
			Intent.ACTION_TIMEZONE_CHANGED -> {
				requestAsyncUpdate(context)
				return
			}
		}

		super.onReceive(context, intent)
	}

	override fun onUpdate(
		context: Context,
		appWidgetManager: AppWidgetManager,
		appWidgetIds: IntArray,
	) {
		requestAsyncUpdate(context, appWidgetIds)
	}

	override fun onEnabled(context: Context) {
		super.onEnabled(context)
		ExpenseWidgetRolloverScheduler.scheduleNextIfNeeded(context)
	}

	override fun onDisabled(context: Context) {
		ExpenseWidgetRolloverScheduler.cancel(context)
		super.onDisabled(context)
	}

	private fun requestAsyncUpdate(context: Context, appWidgetIds: IntArray? = null) {
		val pendingResult = goAsync()
		ExpenseWidgetRenderer.requestUpdate(context, appWidgetIds) {
			pendingResult.finish()
		}
	}
}
