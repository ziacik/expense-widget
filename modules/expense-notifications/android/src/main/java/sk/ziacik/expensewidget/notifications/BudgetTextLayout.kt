package sk.ziacik.expensewidget.notifications

import kotlin.math.min
import kotlin.math.roundToInt

internal data class WidgetTextMetrics(
	val widthPx: Float,
	val ascentPx: Float,
	val descentPx: Float,
)

internal data class WidgetTextPosition(
	val xPx: Float,
	val baselinePx: Float,
)

internal data class BudgetTextPositions(
	val amount: WidgetTextPosition,
	val percentage: WidgetTextPosition,
)

internal fun calculateBudgetArtworkHeightPx(
	rootHeightPx: Float,
	verticalPaddingPx: Float,
	hasBudget: Boolean,
	usesResponsiveProgressSpacing: Boolean,
	density: Float,
): Int {
	val progressSpacePx =
		when {
			!hasBudget -> 0f
			usesResponsiveProgressSpacing ->
				rootHeightPx *
					(BUDGET_PROGRESS_HEIGHT_FRACTION + BUDGET_PROGRESS_MARGIN_FRACTION)
			else ->
				(
					(BUDGET_FIXED_PROGRESS_HEIGHT_DP * density).roundToInt() +
						(BUDGET_FIXED_PROGRESS_MARGIN_DP * density).roundToInt()
				).toFloat()
		}
	return (rootHeightPx - 2f * verticalPaddingPx - progressSpacePx)
		.roundToInt()
		.coerceAtLeast(1)
}

internal fun calculateConfiguredBudgetWholeSizePx(
	artworkHeightPx: Float,
	widthLimitedSizePx: Float,
): Float {
	return min(
		artworkHeightPx * BUDGET_CONFIGURED_TEXT_HEIGHT_FRACTION,
		widthLimitedSizePx,
	)
}

internal fun calculateBudgetTextPositions(
	canvasWidthPx: Float,
	canvasHeightPx: Float,
	amount: WidgetTextMetrics,
	percentage: WidgetTextMetrics,
	percentageBottomInsetPx: Float,
): BudgetTextPositions {
	return BudgetTextPositions(
		amount =
			WidgetTextPosition(
				xPx = ((canvasWidthPx - amount.widthPx) / 2f).coerceAtLeast(0f),
				baselinePx = (canvasHeightPx - amount.ascentPx - amount.descentPx) / 2f,
			),
		percentage =
			WidgetTextPosition(
				xPx = (canvasWidthPx - percentage.widthPx).coerceAtLeast(0f),
				baselinePx = canvasHeightPx - percentageBottomInsetPx - percentage.descentPx,
			),
	)
}

internal const val BUDGET_PROGRESS_HEIGHT_FRACTION = 0.034f
internal const val BUDGET_PROGRESS_MARGIN_FRACTION = 0.05f

private const val BUDGET_FIXED_PROGRESS_HEIGHT_DP = 3f
private const val BUDGET_FIXED_PROGRESS_MARGIN_DP = 5f
private const val BUDGET_CONFIGURED_TEXT_HEIGHT_FRACTION = 0.4f
