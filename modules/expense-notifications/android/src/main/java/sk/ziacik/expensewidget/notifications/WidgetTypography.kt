package sk.ziacik.expensewidget.notifications

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.SizeF
import androidx.core.content.res.ResourcesCompat
import kotlin.math.min
import kotlin.math.roundToInt

internal data class WidgetRenderSize(
	val widthDp: Float,
	val heightDp: Float,
)

internal class WidgetTypography(private val context: Context) {
	private val displayMetrics = context.resources.displayMetrics
	private val density = displayMetrics.density
	private val primaryTypeface = loadFont(R.font.sora_semibold)
	private val supportingTypeface = loadFont(R.font.sora_medium)
	private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)

	fun totalAmountBitmap(
		size: WidgetRenderSize,
		whole: String,
		cents: String,
		currency: String,
		color: Int,
	): Bitmap {
		val rootWidthPx = size.widthDp * density
		val rootHeightPx = size.heightDp * density
		val paddingPx = totalPaddingPx(size).toFloat()
		val widthPx = (rootWidthPx - 2f * paddingPx).roundToInt().coerceAtLeast(1)
		val heightPx = (rootHeightPx - 2f * paddingPx).roundToInt().coerceAtLeast(1)
		val centsMarginPx = rootHeightPx * TOTAL_CENTS_MARGIN_FRACTION
		val currencyMarginPx = rootHeightPx * TOTAL_CURRENCY_MARGIN_FRACTION
		val widthAtOnePx =
			measureAtOnePx(whole, primaryTypeface, tabular = true) +
				measureAtOnePx(cents, supportingTypeface, tabular = true) * CENTS_RATIO +
				measureAtOnePx(currency, supportingTypeface, tabular = false) * TOTAL_SYMBOL_RATIO
		val wholeSizePx =
			min(
				heightPx * TOTAL_TEXT_HEIGHT_FRACTION,
				(widthPx - centsMarginPx - currencyMarginPx).coerceAtLeast(1f) / widthAtOnePx,
			)
		val centsSizePx = wholeSizePx * CENTS_RATIO
		val symbolSizePx = wholeSizePx * TOTAL_SYMBOL_RATIO
		val amountWidthPx =
			measure(whole, primaryTypeface, wholeSizePx, tabular = true) +
				centsMarginPx +
				measure(cents, supportingTypeface, centsSizePx, tabular = true) +
				currencyMarginPx +
				measure(currency, supportingTypeface, symbolSizePx, tabular = false)
		val baselinePx = centeredBaseline(heightPx.toFloat(), wholeSizePx)
		var x = ((widthPx - amountWidthPx) / 2f).coerceAtLeast(0f)
		val bitmap = createBitmap(widthPx, heightPx)
		val canvas = Canvas(bitmap)

		x = drawText(canvas, whole, x, baselinePx, primaryTypeface, wholeSizePx, color, tabular = true)
		x += centsMarginPx
		x = drawText(canvas, cents, x, baselinePx, supportingTypeface, centsSizePx, color, tabular = true)
		x += currencyMarginPx
		drawText(canvas, currency, x, baselinePx, supportingTypeface, symbolSizePx, color)
		return bitmap
	}

	fun budgetBitmap(
		size: WidgetRenderSize,
		whole: String,
		cents: String,
		currency: String,
		percentage: String?,
		prompt: String,
		amountColor: Int,
		trailingColor: Int,
	): Bitmap {
		val rootWidthPx = size.widthDp * density
		val rootHeightPx = size.heightDp * density
		val paddingPx = budgetPaddingPx(size).toFloat()
		val hasBudget = percentage != null
		val progressSpacePx =
			if (hasBudget) {
				rootHeightPx * (BUDGET_PROGRESS_HEIGHT_FRACTION + BUDGET_PROGRESS_MARGIN_FRACTION)
			} else {
				0f
			}
		val widthPx = (rootWidthPx - 2f * paddingPx).roundToInt().coerceAtLeast(1)
		val heightPx =
			(rootHeightPx - 2f * paddingPx - progressSpacePx).roundToInt().coerceAtLeast(1)
		val centsMarginPx = rootHeightPx * BUDGET_CENTS_MARGIN_FRACTION
		val currencyMarginPx = rootHeightPx * BUDGET_CURRENCY_MARGIN_FRACTION
		val trailingMarginPx =
			rootHeightPx *
				if (hasBudget) BUDGET_PERCENTAGE_MARGIN_FRACTION else BUDGET_PROMPT_MARGIN_FRACTION
		val trailingSymbolMarginPx =
			if (hasBudget) rootHeightPx * BUDGET_PERCENTAGE_SYMBOL_MARGIN_FRACTION else 0f
		val amountWidthAtOnePx =
			measureAtOnePx(whole, primaryTypeface, tabular = true) +
				measureAtOnePx(cents, supportingTypeface, tabular = true) * CENTS_RATIO +
				measureAtOnePx(currency, supportingTypeface, tabular = false) * BUDGET_SYMBOL_RATIO
		val trailingWidthAtOnePx =
			if (percentage != null) {
				measureAtOnePx(percentage, primaryTypeface, tabular = true) * PERCENTAGE_RATIO +
					measureAtOnePx(PERCENTAGE_SYMBOL, supportingTypeface, tabular = false) *
					PERCENTAGE_SYMBOL_RATIO
			} else {
				measureAtOnePx(prompt, supportingTypeface, tabular = false) * PROMPT_RATIO
			}
		val fixedSpacingPx = centsMarginPx + currencyMarginPx + trailingMarginPx + trailingSymbolMarginPx
		val wholeSizePx =
			min(
				heightPx * BUDGET_TEXT_HEIGHT_FRACTION,
				(widthPx - fixedSpacingPx).coerceAtLeast(1f) /
					(amountWidthAtOnePx + trailingWidthAtOnePx),
			)
		val centsSizePx = wholeSizePx * CENTS_RATIO
		val symbolSizePx = wholeSizePx * BUDGET_SYMBOL_RATIO
		val amountWidthPx =
			measure(whole, primaryTypeface, wholeSizePx, tabular = true) +
				centsMarginPx +
				measure(cents, supportingTypeface, centsSizePx, tabular = true) +
				currencyMarginPx +
				measure(currency, supportingTypeface, symbolSizePx, tabular = false)
		val baselinePx = centeredBaseline(heightPx.toFloat(), wholeSizePx)
		val bitmap = createBitmap(widthPx, heightPx)
		val canvas = Canvas(bitmap)
		var x = 0f

		x = drawText(canvas, whole, x, baselinePx, primaryTypeface, wholeSizePx, amountColor, tabular = true)
		x += centsMarginPx
		x = drawText(canvas, cents, x, baselinePx, supportingTypeface, centsSizePx, amountColor, tabular = true)
		x += currencyMarginPx
		drawText(canvas, currency, x, baselinePx, supportingTypeface, symbolSizePx, amountColor)

		if (percentage != null) {
			val numberSizePx = wholeSizePx * PERCENTAGE_RATIO
			val percentageSymbolSizePx = wholeSizePx * PERCENTAGE_SYMBOL_RATIO
			val numberWidthPx = measure(percentage, primaryTypeface, numberSizePx, tabular = true)
			val percentWidthPx =
				measure(PERCENTAGE_SYMBOL, supportingTypeface, percentageSymbolSizePx, tabular = false)
			val trailingWidthPx = numberWidthPx + trailingSymbolMarginPx + percentWidthPx
			var trailingX = (widthPx - trailingWidthPx).coerceAtLeast(amountWidthPx + trailingMarginPx)
			trailingX =
				drawText(
					canvas,
					percentage,
					trailingX,
					baselinePx,
					primaryTypeface,
					numberSizePx,
					trailingColor,
					tabular = true,
				)
			trailingX += trailingSymbolMarginPx
			drawText(
				canvas,
				PERCENTAGE_SYMBOL,
				trailingX,
				baselinePx - percentageSymbolSizePx * PERCENTAGE_SYMBOL_RAISE_RATIO,
				supportingTypeface,
				percentageSymbolSizePx,
				trailingColor,
			)
		} else {
			val promptSizePx = wholeSizePx * PROMPT_RATIO
			val promptWidthPx = measure(prompt, supportingTypeface, promptSizePx, tabular = false)
			val promptX = (widthPx - promptWidthPx).coerceAtLeast(amountWidthPx + trailingMarginPx)
			drawText(
				canvas,
				prompt,
				promptX,
				baselinePx,
				supportingTypeface,
				promptSizePx,
				trailingColor,
			)
		}
		return bitmap
	}

	fun statusTextSizePx(size: WidgetRenderSize): Float {
		return size.heightDp * density * STATUS_TEXT_HEIGHT_FRACTION
	}

	fun totalPaddingPx(size: WidgetRenderSize): Int {
		return (size.heightDp * density * TOTAL_PADDING_FRACTION).roundToInt()
	}

	fun budgetPaddingPx(size: WidgetRenderSize): Int {
		return (size.heightDp * density * BUDGET_PADDING_FRACTION).roundToInt()
	}

	fun budgetProgressHeightDp(size: WidgetRenderSize): Float {
		return size.heightDp * BUDGET_PROGRESS_HEIGHT_FRACTION
	}

	fun budgetProgressMarginDp(size: WidgetRenderSize): Float {
		return size.heightDp * BUDGET_PROGRESS_MARGIN_FRACTION
	}

	private fun createBitmap(widthPx: Int, heightPx: Int): Bitmap {
		return Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).apply {
			density = displayMetrics.densityDpi
		}
	}

	private fun centeredBaseline(heightPx: Float, textSizePx: Float): Float {
		configurePaint(primaryTypeface, textSizePx, color = 0, tabular = true)
		val metrics = paint.fontMetrics
		return (heightPx - metrics.ascent - metrics.descent) / 2f
	}

	private fun drawText(
		canvas: Canvas,
		text: String,
		x: Float,
		baseline: Float,
		typeface: Typeface,
		textSizePx: Float,
		color: Int,
		tabular: Boolean = false,
	): Float {
		configurePaint(typeface, textSizePx, color, tabular)
		canvas.drawText(text, x, baseline, paint)
		return x + paint.measureText(text)
	}

	private fun measure(
		text: String,
		typeface: Typeface,
		textSizePx: Float,
		tabular: Boolean,
	): Float {
		configurePaint(typeface, textSizePx, color = 0, tabular = tabular)
		return paint.measureText(text)
	}

	private fun measureAtOnePx(
		text: String,
		typeface: Typeface,
		tabular: Boolean,
	): Float {
		return measure(text, typeface, MEASUREMENT_TEXT_SIZE_PX, tabular) /
			MEASUREMENT_TEXT_SIZE_PX
	}

	private fun configurePaint(
		typeface: Typeface,
		textSizePx: Float,
		color: Int,
		tabular: Boolean,
	) {
		paint.typeface = typeface
		paint.textSize = textSizePx
		paint.color = color
		paint.fontFeatureSettings = if (tabular) TABULAR_NUMBERS else null
	}

	private fun loadFont(fontResourceId: Int): Typeface {
		return ResourcesCompat.getFont(context, fontResourceId)
			?: error("Bundled Sora font could not be loaded.")
	}

	companion object {
		fun from(size: SizeF): WidgetRenderSize {
			return WidgetRenderSize(widthDp = size.width, heightDp = size.height)
		}

		private const val MEASUREMENT_TEXT_SIZE_PX = 100f
		private const val TABULAR_NUMBERS = "tnum"
		private const val PERCENTAGE_SYMBOL = "%"

		private const val CENTS_RATIO = 0.64f
		private const val TOTAL_SYMBOL_RATIO = 0.53f
		private const val BUDGET_SYMBOL_RATIO = 0.46f
		private const val PERCENTAGE_RATIO = 0.72f
		private const val PERCENTAGE_SYMBOL_RATIO = 0.41f
		private const val PROMPT_RATIO = 0.5f
		private const val PERCENTAGE_SYMBOL_RAISE_RATIO = 0.45f

		private const val TOTAL_PADDING_FRACTION = 0.067f
		private const val TOTAL_CENTS_MARGIN_FRACTION = 0.009f
		private const val TOTAL_CURRENCY_MARGIN_FRACTION = 0.018f
		private const val TOTAL_TEXT_HEIGHT_FRACTION = 0.327f

		private const val BUDGET_PADDING_FRACTION = 0.086f
		private const val BUDGET_PROGRESS_HEIGHT_FRACTION = 0.034f
		private const val BUDGET_PROGRESS_MARGIN_FRACTION = 0.067f
		private const val BUDGET_TEXT_HEIGHT_FRACTION = 0.453f
		private const val BUDGET_CENTS_MARGIN_FRACTION = 0.01f
		private const val BUDGET_CURRENCY_MARGIN_FRACTION = 0.019f
		private const val BUDGET_PERCENTAGE_MARGIN_FRACTION = 0.057f
		private const val BUDGET_PROMPT_MARGIN_FRACTION = 0.057f
		private const val BUDGET_PERCENTAGE_SYMBOL_MARGIN_FRACTION = 0.019f

		private const val STATUS_TEXT_HEIGHT_FRACTION = 0.153f
	}
}
