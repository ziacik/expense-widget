package sk.ziacik.expensewidget.notifications

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

internal enum class ExpenseWidgetKind {
	TOTAL,
	BUDGET,
}

internal object ExpenseWidgetRenderer {
	private val updateExecutor = Executors.newSingleThreadExecutor()

	fun requestUpdate(
		context: Context,
		appWidgetIds: IntArray? = null,
		widgetKind: ExpenseWidgetKind? = null,
		onComplete: () -> Unit = {},
	) {
		val applicationContext = context.applicationContext
		try {
			updateExecutor.execute {
				try {
					updateNow(applicationContext, appWidgetIds, widgetKind)
				} catch (error: Exception) {
					Log.e(TAG, "Could not update the expense widgets.", error)
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

	suspend fun awaitUpdate(context: Context) {
		suspendCancellableCoroutine { continuation ->
			requestUpdate(context) {
				if (continuation.isActive) {
					continuation.resume(Unit)
				}
			}
		}
	}

	private fun updateNow(
		context: Context,
		requestedWidgetIds: IntArray?,
		requestedWidgetKind: ExpenseWidgetKind?,
	) {
		require(requestedWidgetIds == null || requestedWidgetKind != null) {
			"Requested widget IDs must identify a widget kind."
		}

		val appWidgetManager = AppWidgetManager.getInstance(context)
		val totalWidgetIds =
			resolveWidgetIds(
				context,
				appWidgetManager,
				requestedWidgetIds,
				requestedWidgetKind,
				ExpenseWidgetKind.TOTAL,
				ExpenseWidgetProvider::class.java,
			)
		val budgetWidgetIds =
			resolveWidgetIds(
				context,
				appWidgetManager,
				requestedWidgetIds,
				requestedWidgetKind,
				ExpenseWidgetKind.BUDGET,
				BudgetWidgetProvider::class.java,
			)
		if (totalWidgetIds.isEmpty() && budgetWidgetIds.isEmpty()) {
			return
		}

		val month = ExpenseWidgetRolloverScheduler.currentMonth()
		val monthName = context.resources.getStringArray(R.array.expense_widget_months)[month.zeroBasedMonth]
		val monthLabel = context.getString(R.string.expense_widget_month_label, monthName, month.year)
		val state = loadState(context, month.key)
		val openAppIntent = openAppPendingIntent(context)
		val typography = WidgetTypography(context)

		if (totalWidgetIds.isNotEmpty()) {
			updateWidgetInstances(
				context,
				appWidgetManager,
				totalWidgetIds,
			) { size ->
				renderTotalWidget(context, monthLabel, state, openAppIntent, typography, size)
			}
		}
		if (budgetWidgetIds.isNotEmpty()) {
			updateWidgetInstances(
				context,
				appWidgetManager,
				budgetWidgetIds,
			) { size ->
				renderBudgetWidget(context, monthLabel, state, openAppIntent, typography, size)
			}
		}
	}

	private fun updateWidgetInstances(
		context: Context,
		appWidgetManager: AppWidgetManager,
		appWidgetIds: IntArray,
		render: (WidgetRenderSize) -> RemoteViews,
	) {
		appWidgetIds.forEach { appWidgetId ->
			val exactSizes = exactWidgetSizes(appWidgetManager, appWidgetId)
			val views =
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && exactSizes.isNotEmpty()) {
					RemoteViews(
						exactSizes.associateWith { size -> render(WidgetTypography.from(size)) },
					)
				} else {
					render(fallbackWidgetSize(context, appWidgetManager, appWidgetId))
				}
			appWidgetManager.updateAppWidget(appWidgetId, views)
		}
	}

	@Suppress("DEPRECATION")
	private fun exactWidgetSizes(
		appWidgetManager: AppWidgetManager,
		appWidgetId: Int,
	): List<SizeF> {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
			return emptyList()
		}
		return appWidgetManager
			.getAppWidgetOptions(appWidgetId)
			.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
			.orEmpty()
			.filter { size -> size.width > 0f && size.height > 0f }
			.distinct()
			.take(MAX_RESPONSIVE_SIZES)
	}

	private fun fallbackWidgetSize(
		context: Context,
		appWidgetManager: AppWidgetManager,
		appWidgetId: Int,
	): WidgetRenderSize {
		val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
		val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
		val widthKey =
			if (isLandscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
			else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
		val heightKey =
			if (isLandscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
			else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
		val density = context.resources.displayMetrics.density
		val providerInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
		val fallbackWidthDp = providerInfo?.minWidth?.div(density) ?: 1f
		val fallbackHeightDp = providerInfo?.minHeight?.div(density) ?: 1f

		return WidgetRenderSize(
			widthDp = options.getInt(widthKey).takeIf { it > 0 }?.toFloat() ?: fallbackWidthDp,
			heightDp = options.getInt(heightKey).takeIf { it > 0 }?.toFloat() ?: fallbackHeightDp,
		)
	}

	private fun resolveWidgetIds(
		context: Context,
		appWidgetManager: AppWidgetManager,
		requestedWidgetIds: IntArray?,
		requestedWidgetKind: ExpenseWidgetKind?,
		widgetKind: ExpenseWidgetKind,
		providerClass: Class<*>,
	): IntArray {
		if (requestedWidgetKind != null && requestedWidgetKind != widgetKind) {
			return intArrayOf()
		}
		return requestedWidgetIds
			?: appWidgetManager.getAppWidgetIds(ComponentName(context, providerClass))
	}

	private fun renderTotalWidget(
		context: Context,
		monthLabel: String,
		state: WidgetState,
		openAppIntent: PendingIntent?,
		typography: WidgetTypography,
		size: WidgetRenderSize,
	): RemoteViews {
		val views = RemoteViews(context.packageName, R.layout.expense_widget)
		val paddingPx = typography.totalPaddingPx(size)
		views.setViewPadding(android.R.id.background, paddingPx, paddingPx, paddingPx, paddingPx)
		openAppIntent?.let { pendingIntent ->
			views.setOnClickPendingIntent(android.R.id.background, pendingIntent)
		}

		when (state) {
			is WidgetState.Ready -> {
				val totalAmount = formatAmount(context, state.projection.totalMinor)
				applyTotalAmount(context, views, totalAmount, typography, size)
				views.setContentDescription(
					android.R.id.background,
					context.getString(
						R.string.expense_widget_content_description,
						monthLabel,
						totalAmount.display,
					),
				)
			}

			WidgetState.AccessDisabled ->
				applyTotalStatus(
					context,
					views,
					monthLabel,
					context.getString(R.string.expense_widget_access_disabled),
					typography,
					size,
				)

			WidgetState.ProjectionError ->
				applyTotalStatus(
					context,
					views,
					monthLabel,
					context.getString(R.string.expense_widget_projection_error),
					typography,
					size,
				)
		}
		return views
	}

	private fun renderBudgetWidget(
		context: Context,
		monthLabel: String,
		state: WidgetState,
		openAppIntent: PendingIntent?,
		typography: WidgetTypography,
		size: WidgetRenderSize,
	): RemoteViews {
		val views = RemoteViews(context.packageName, R.layout.budget_widget)
		val paddingPx = typography.budgetPaddingPx(size)
		views.setViewPadding(android.R.id.background, paddingPx, paddingPx, paddingPx, paddingPx)
		applyBudgetResponsiveSpacing(views, typography, size)
		openAppIntent?.let { pendingIntent ->
			views.setOnClickPendingIntent(android.R.id.background, pendingIntent)
		}

		when (state) {
			is WidgetState.Ready ->
				applyBudgetReadyState(context, views, monthLabel, state, typography, size)
			WidgetState.AccessDisabled ->
				applyBudgetStatus(
					context,
					views,
					monthLabel,
					context.getString(R.string.expense_widget_access_disabled),
					typography,
					size,
				)

			WidgetState.ProjectionError ->
				applyBudgetStatus(
					context,
					views,
					monthLabel,
					context.getString(R.string.expense_widget_projection_error),
					typography,
					size,
				)
		}
		return views
	}

	private fun applyBudgetReadyState(
		context: Context,
		views: RemoteViews,
		monthLabel: String,
		state: WidgetState.Ready,
		typography: WidgetTypography,
		size: WidgetRenderSize,
	) {
		views.setViewVisibility(R.id.budget_widget_content, View.VISIBLE)
		views.setViewVisibility(R.id.budget_widget_status, View.GONE)
		views.setViewVisibility(R.id.budget_widget_artwork, View.VISIBLE)
		views.setViewVisibility(R.id.budget_widget_preview_row, View.GONE)

		val totalAmount = formatAmount(context, state.projection.totalMinor)
		val currency = context.getString(R.string.expense_widget_currency_symbol)
		val monthlyBudgetMinor = state.monthlyBudgetMinor
		if (monthlyBudgetMinor == null) {
			val prompt = context.getString(R.string.budget_widget_set_budget)
			views.setImageViewBitmap(
				R.id.budget_widget_artwork,
				typography.budgetBitmap(
					size,
					totalAmount.whole,
					totalAmount.cents,
					currency,
					percentage = null,
					prompt = prompt,
					amountColor = context.getColor(R.color.expense_widget_primary),
					trailingColor = context.getColor(R.color.expense_widget_primary),
				),
			)
			views.setViewVisibility(R.id.budget_widget_progress, View.GONE)
			views.setContentDescription(
				android.R.id.background,
				context.getString(
					R.string.budget_widget_unset_content_description,
					monthLabel,
					totalAmount.display,
				),
			)
			return
		}

		val budgetAmount = formatAmount(context, monthlyBudgetMinor)
		val percentage = calculateBudgetPercentage(state.projection.totalMinor, monthlyBudgetMinor)
		val progress = percentage.coerceAtMost(100L).toInt()
		val percentageColor =
			when {
				percentage < 75 -> R.color.expense_widget_success
				percentage < 100 -> R.color.expense_widget_warning
				else -> R.color.expense_widget_expense
			}

		val percentageText = percentage.toString()
		views.setImageViewBitmap(
			R.id.budget_widget_artwork,
			typography.budgetBitmap(
				size,
				totalAmount.whole,
				totalAmount.cents,
				currency,
				percentage = percentageText,
				prompt = context.getString(R.string.budget_widget_set_budget),
				amountColor = context.getColor(R.color.expense_widget_primary),
				trailingColor = context.getColor(percentageColor),
			),
		)
		views.setViewVisibility(R.id.budget_widget_progress, View.VISIBLE)
		views.setProgressBar(R.id.budget_widget_progress, 100, progress, false)
		views.setContentDescription(
			android.R.id.background,
			context.getString(
				R.string.budget_widget_content_description,
				monthLabel,
				totalAmount.display,
				budgetAmount.display,
				percentage,
			),
		)
	}

	private fun applyTotalAmount(
		context: Context,
		views: RemoteViews,
		amount: FormattedAmount,
		typography: WidgetTypography,
		size: WidgetRenderSize,
	) {
		views.setImageViewBitmap(
			R.id.expense_widget_artwork,
			typography.totalAmountBitmap(
				size,
				amount.whole,
				amount.cents,
				context.getString(R.string.expense_widget_currency_symbol),
				context.getColor(R.color.expense_widget_expense),
			),
		)
		views.setViewVisibility(R.id.expense_widget_artwork, View.VISIBLE)
		views.setViewVisibility(R.id.expense_widget_amount_group, View.GONE)
		views.setViewVisibility(R.id.expense_widget_status, View.GONE)
	}

	private fun applyTotalStatus(
		context: Context,
		views: RemoteViews,
		monthLabel: String,
		statusText: String,
		typography: WidgetTypography,
		size: WidgetRenderSize,
	) {
		views.setViewVisibility(R.id.expense_widget_artwork, View.GONE)
		views.setViewVisibility(R.id.expense_widget_amount_group, View.GONE)
		views.setViewVisibility(R.id.expense_widget_status, View.VISIBLE)
		views.setTextViewText(R.id.expense_widget_status, statusText)
		views.setTextViewTextSize(
			R.id.expense_widget_status,
			TypedValue.COMPLEX_UNIT_PX,
			typography.statusTextSizePx(size),
		)
		views.setTextColor(
			R.id.expense_widget_status,
			context.getColor(R.color.expense_widget_warning),
		)
		views.setContentDescription(
			android.R.id.background,
			context.getString(
				R.string.expense_widget_status_content_description,
				monthLabel,
				statusText,
			),
		)
	}

	private fun applyBudgetStatus(
		context: Context,
		views: RemoteViews,
		monthLabel: String,
		statusText: String,
		typography: WidgetTypography,
		size: WidgetRenderSize,
	) {
		views.setViewVisibility(R.id.budget_widget_content, View.GONE)
		views.setViewVisibility(R.id.budget_widget_status, View.VISIBLE)
		views.setTextViewText(R.id.budget_widget_status, statusText)
		views.setTextViewTextSize(
			R.id.budget_widget_status,
			TypedValue.COMPLEX_UNIT_PX,
			typography.statusTextSizePx(size),
		)
		views.setTextColor(
			R.id.budget_widget_status,
			context.getColor(R.color.expense_widget_warning),
		)
		views.setContentDescription(
			android.R.id.background,
			context.getString(
				R.string.expense_widget_status_content_description,
				monthLabel,
				statusText,
			),
		)
	}

	private fun loadState(context: Context, monthKey: String): WidgetState {
		return try {
			if (!NotificationAccess.isGranted(context)) {
				WidgetState.AccessDisabled
			} else {
				WidgetState.Ready(
					projection = ExpenseRepository.getInstance(context).getWidgetProjection(monthKey),
					monthlyBudgetMinor = MonthlyBudgetStore(context).get(),
				)
			}
		} catch (error: Exception) {
			Log.e(TAG, "Could not read the expense widget projection.", error)
			WidgetState.ProjectionError
		}
	}

	private fun formatAmount(context: Context, amountMinor: Long): FormattedAmount {
		check(amountMinor in 0..ExpenseDatabase.MAX_SAFE_INTEGER) {
			"Widget amount is outside the safe-integer range."
		}
		val wholeUnits = amountMinor / 100
		val minorUnits = amountMinor % 100
		val wholeUnitsText = NumberFormat.getIntegerInstance(SLOVAK_LOCALE).format(wholeUnits)
		return FormattedAmount(
			whole = wholeUnitsText,
			cents = String.format(Locale.ROOT, ",%02d", minorUnits),
			display =
				context.getString(
					R.string.expense_widget_amount,
					wholeUnitsText,
					minorUnits,
				),
		)
	}

	private fun calculateBudgetPercentage(totalMinor: Long, monthlyBudgetMinor: Long): Long {
		check(totalMinor in 0..ExpenseDatabase.MAX_SAFE_INTEGER)
		check(monthlyBudgetMinor in 1..ExpenseDatabase.MAX_SAFE_INTEGER)
		return Math.round(totalMinor.toDouble() / monthlyBudgetMinor.toDouble() * 100.0)
			.coerceAtMost(ExpenseDatabase.MAX_SAFE_INTEGER)
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
		data class Ready(
			val projection: WidgetProjectionData,
			val monthlyBudgetMinor: Long?,
		) : WidgetState

		data object AccessDisabled : WidgetState
		data object ProjectionError : WidgetState
	}

	private data class FormattedAmount(
		val whole: String,
		val cents: String,
		val display: String,
	)

	private fun applyBudgetResponsiveSpacing(
		views: RemoteViews,
		typography: WidgetTypography,
		size: WidgetRenderSize,
	) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
			return
		}
		views.setViewLayoutHeight(
			R.id.budget_widget_progress,
			typography.budgetProgressHeightDp(size),
			TypedValue.COMPLEX_UNIT_DIP,
		)
		views.setViewLayoutMargin(
			R.id.budget_widget_progress,
			RemoteViews.MARGIN_TOP,
			typography.budgetProgressMarginDp(size),
			TypedValue.COMPLEX_UNIT_DIP,
		)
	}

	private val SLOVAK_LOCALE = Locale.forLanguageTag("sk-SK")
	private const val OPEN_APP_REQUEST_CODE = 4002
	private const val MAX_RESPONSIVE_SIZES = 16
	private const val TAG = "ExpenseWidget"
}
