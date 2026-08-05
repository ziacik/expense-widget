package sk.ziacik.expensewidget.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

internal data class BratislavaMonth(
	val key: String,
	val year: Int,
	val zeroBasedMonth: Int,
)

internal object ExpenseWidgetRolloverScheduler {
	const val ACTION_MONTH_ROLLOVER =
		"sk.ziacik.expensewidget.notifications.action.MONTH_ROLLOVER"

	fun currentMonth(nowMs: Long = System.currentTimeMillis()): BratislavaMonth {
		val calendar = Calendar.getInstance(bratislavaTimeZone()).apply { timeInMillis = nowMs }
		val year = calendar.get(Calendar.YEAR)
		val zeroBasedMonth = calendar.get(Calendar.MONTH)
		return BratislavaMonth(
			key = String.format(Locale.ROOT, "%04d-%02d", year, zeroBasedMonth + 1),
			year = year,
			zeroBasedMonth = zeroBasedMonth,
		)
	}

	fun bratislavaTimeZone(): TimeZone = TimeZone.getTimeZone(BRATISLAVA_TIME_ZONE_ID)

	fun scheduleNextIfNeeded(context: Context, nowMs: Long = System.currentTimeMillis()) {
		val applicationContext = context.applicationContext
		val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
		val hasExpenseWidget =
			appWidgetManager
				.getAppWidgetIds(ComponentName(applicationContext, ExpenseWidgetProvider::class.java))
				.isNotEmpty()
		val hasBudgetWidget =
			appWidgetManager
				.getAppWidgetIds(ComponentName(applicationContext, BudgetWidgetProvider::class.java))
				.isNotEmpty()
		if (!hasExpenseWidget && !hasBudgetWidget) {
			cancel(applicationContext)
			return
		}

		val boundary = Calendar.getInstance(bratislavaTimeZone()).apply {
			timeInMillis = nowMs
			set(Calendar.DAY_OF_MONTH, 1)
			set(Calendar.HOUR_OF_DAY, 0)
			set(Calendar.MINUTE, 0)
			set(Calendar.SECOND, 0)
			set(Calendar.MILLISECOND, 0)
			add(Calendar.MONTH, 1)
		}
		alarmManager(applicationContext).setAndAllowWhileIdle(
			AlarmManager.RTC_WAKEUP,
			boundary.timeInMillis,
			createRolloverPendingIntent(applicationContext),
		)
	}

	fun cancel(context: Context) {
		val applicationContext = context.applicationContext
		val pendingIntent =
			findRolloverPendingIntent(applicationContext) ?: return
		alarmManager(applicationContext).cancel(pendingIntent)
	}

	private fun alarmManager(context: Context): AlarmManager {
		return context.getSystemService(AlarmManager::class.java)
	}

	private fun createRolloverPendingIntent(context: Context): PendingIntent {
		return PendingIntent.getBroadcast(
			context,
			ROLLOVER_REQUEST_CODE,
			rolloverIntent(context),
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
		)
	}

	private fun findRolloverPendingIntent(context: Context): PendingIntent? {
		return PendingIntent.getBroadcast(
			context,
			ROLLOVER_REQUEST_CODE,
			rolloverIntent(context),
			PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
		)
	}

	private fun rolloverIntent(context: Context): Intent {
		val intent =
			Intent(context, ExpenseWidgetProvider::class.java).setAction(ACTION_MONTH_ROLLOVER)
		return intent
	}

	private const val BRATISLAVA_TIME_ZONE_ID = "Europe/Bratislava"
	private const val ROLLOVER_REQUEST_CODE = 4001
}
