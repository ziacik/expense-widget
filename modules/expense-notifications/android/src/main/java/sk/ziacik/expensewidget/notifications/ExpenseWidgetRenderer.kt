package sk.ziacik.expensewidget.notifications

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

internal object ExpenseWidgetRenderer {
	private val updateExecutor = Executors.newSingleThreadExecutor()

	fun requestUpdate(
		context: Context,
		appWidgetIds: IntArray? = null,
		onComplete: () -> Unit = {},
	) {
		val applicationContext = context.applicationContext
		try {
			updateExecutor.execute {
				try {
					updateNow(applicationContext, appWidgetIds)
				} catch (error: Exception) {
					Log.e(TAG, "Could not update the expense widget.", error)
				} finally {
					try {
						ExpenseWidgetRolloverScheduler.scheduleNextIfNeeded(applicationContext)
					} catch (error: Exception) {
						Log.e(TAG, "Could not schedule the expense widget rollover.", error)
					} finally {
						onComplete()
					}
				}
			}
		} catch (error: RuntimeException) {
			Log.e(TAG, "Could not enqueue an expense widget update.", error)
			onComplete()
		}
	}

	suspend fun awaitUpdate(context: Context, appWidgetIds: IntArray? = null) {
		suspendCancellableCoroutine { continuation ->
			requestUpdate(context, appWidgetIds) {
				if (continuation.isActive) {
					continuation.resume(Unit)
				}
			}
		}
	}

	private fun updateNow(context: Context, requestedWidgetIds: IntArray?) {
		val appWidgetManager = AppWidgetManager.getInstance(context)
		val widgetIds =
			requestedWidgetIds
				?: appWidgetManager.getAppWidgetIds(
					ComponentName(context, ExpenseWidgetProvider::class.java),
				)
		if (widgetIds.isEmpty()) {
			return
		}

		val nowMs = System.currentTimeMillis()
		val month = ExpenseWidgetRolloverScheduler.currentMonth(nowMs)
		val monthName = context.resources.getStringArray(R.array.expense_widget_months)[month.zeroBasedMonth]
		val monthLabel =
			context.getString(R.string.expense_widget_month_label, monthName, month.year)
		val views = RemoteViews(context.packageName, R.layout.expense_widget)
		views.setTextViewText(R.id.expense_widget_month, monthLabel)
		views.setTextViewText(
			R.id.expense_widget_updated,
			context.getString(R.string.expense_widget_updated, formatUpdateTime(nowMs)),
		)
		openAppPendingIntent(context)?.let { pendingIntent ->
			views.setOnClickPendingIntent(android.R.id.background, pendingIntent)
		}

		when (val state = loadState(context, month.key)) {
			is WidgetState.Ready -> applyReadyState(context, views, monthLabel, state.projection)
			WidgetState.AccessDisabled ->
				applyStatusState(
					context,
					views,
					monthLabel,
					context.getString(R.string.expense_widget_access_disabled),
				)
			WidgetState.ProjectionError ->
				applyStatusState(
					context,
					views,
					monthLabel,
					context.getString(R.string.expense_widget_projection_error),
				)
		}

		appWidgetManager.updateAppWidget(widgetIds, views)
	}

	private fun loadState(context: Context, monthKey: String): WidgetState {
		return try {
			if (!NotificationAccess.isGranted(context)) {
				WidgetState.AccessDisabled
			} else {
				WidgetState.Ready(
					ExpenseRepository.getInstance(context).getWidgetProjection(monthKey),
				)
			}
		} catch (error: Exception) {
			Log.e(TAG, "Could not read the expense widget projection.", error)
			WidgetState.ProjectionError
		}
	}

	private fun applyReadyState(
		context: Context,
		views: RemoteViews,
		monthLabel: String,
		projection: WidgetProjectionData,
	) {
		val totalText = formatAmount(context, projection.totalMinor)
		val quantity = projection.transactionCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
		val countText =
			context.resources.getQuantityString(
				R.plurals.expense_widget_transaction_count,
				quantity,
				projection.transactionCount,
			)
		views.setTextViewText(R.id.expense_widget_total, totalText)
		views.setTextViewTextSize(
			R.id.expense_widget_total,
			TypedValue.COMPLEX_UNIT_SP,
			totalTextSize(totalText),
		)
		views.setTextColor(
			R.id.expense_widget_total,
			context.getColor(R.color.expense_widget_expense),
		)
		views.setViewVisibility(R.id.expense_widget_count, View.VISIBLE)
		views.setTextViewText(R.id.expense_widget_count, countText)
		views.setContentDescription(
			android.R.id.background,
			context.getString(
				R.string.expense_widget_content_description,
				monthLabel,
				totalText,
				countText,
			),
		)
	}

	private fun applyStatusState(
		context: Context,
		views: RemoteViews,
		monthLabel: String,
		statusText: String,
	) {
		views.setTextViewText(R.id.expense_widget_total, statusText)
		views.setTextViewTextSize(
			R.id.expense_widget_total,
			TypedValue.COMPLEX_UNIT_SP,
			18f,
		)
		views.setTextColor(
			R.id.expense_widget_total,
			context.getColor(R.color.expense_widget_warning),
		)
		views.setViewVisibility(R.id.expense_widget_count, View.GONE)
		views.setContentDescription(
			android.R.id.background,
			context.getString(
				R.string.expense_widget_status_content_description,
				monthLabel,
				statusText,
			),
		)
	}

	private fun formatAmount(context: Context, amountMinor: Long): String {
		check(amountMinor in 0..ExpenseDatabase.MAX_SAFE_INTEGER) {
			"Widget amount is outside the safe-integer range."
		}
		val wholeUnits = amountMinor / 100
		val minorUnits = amountMinor % 100
		val wholeUnitsText = NumberFormat.getIntegerInstance(SLOVAK_LOCALE).format(wholeUnits)
		return context.getString(
			R.string.expense_widget_amount,
			wholeUnitsText,
			minorUnits,
		)
	}

	private fun formatUpdateTime(nowMs: Long): String {
		return SimpleDateFormat("HH:mm", SLOVAK_LOCALE).apply {
			timeZone = ExpenseWidgetRolloverScheduler.bratislavaTimeZone()
		}.format(Date(nowMs))
	}

	private fun totalTextSize(totalText: String): Float {
		return when {
			totalText.length <= 11 -> 26f
			totalText.length <= 16 -> 21f
			totalText.length <= 22 -> 17f
			else -> 14f
		}
	}

	private fun openAppPendingIntent(context: Context): PendingIntent? {
		val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
		return PendingIntent.getActivity(
			context,
			OPEN_APP_REQUEST_CODE,
			intent,
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
		)
	}

	private sealed interface WidgetState {
		data class Ready(val projection: WidgetProjectionData) : WidgetState
		data object AccessDisabled : WidgetState
		data object ProjectionError : WidgetState
	}

	private val SLOVAK_LOCALE = Locale.forLanguageTag("sk-SK")
	private const val OPEN_APP_REQUEST_CODE = 4002
	private const val TAG = "ExpenseWidget"
}
