package sk.ziacik.expensewidget.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetTextLayoutTest {
	@Test
	fun `matches artwork height to fixed and responsive progress spacing`() {
		assertEquals(
			26,
			calculateBudgetArtworkHeightPx(
				rootHeightPx = 40f,
				verticalPaddingPx = 3f,
				hasBudget = true,
				usesResponsiveProgressSpacing = false,
				density = 1f,
			),
		)
		assertEquals(
			31,
			calculateBudgetArtworkHeightPx(
				rootHeightPx = 40f,
				verticalPaddingPx = 3f,
				hasBudget = true,
				usesResponsiveProgressSpacing = true,
				density = 1f,
			),
		)
		assertEquals(
			34,
			calculateBudgetArtworkHeightPx(
				rootHeightPx = 40f,
				verticalPaddingPx = 3f,
				hasBudget = false,
				usesResponsiveProgressSpacing = false,
				density = 1f,
			),
		)
		assertEquals(
			37,
			calculateBudgetArtworkHeightPx(
				rootHeightPx = 60f,
				verticalPaddingPx = 5f,
				hasBudget = true,
				usesResponsiveProgressSpacing = false,
				density = 1.5f,
			),
		)
	}

	@Test
	fun `centers the amount independently from the percentage width`() {
		val amount = WidgetTextMetrics(widthPx = 52f, ascentPx = -10f, descentPx = 2f)
		val compactPercentage = WidgetTextMetrics(widthPx = 14f, ascentPx = -4f, descentPx = 1f)
		val widePercentage = WidgetTextMetrics(widthPx = 30f, ascentPx = -4f, descentPx = 1f)

		val compactLayout =
			calculateBudgetTextPositions(
				canvasWidthPx = 100f,
				canvasHeightPx = 28f,
				amount = amount,
				percentage = compactPercentage,
				percentageBottomInsetPx = 1f,
			)
		val wideLayout =
			calculateBudgetTextPositions(
				canvasWidthPx = 100f,
				canvasHeightPx = 28f,
				amount = amount,
				percentage = widePercentage,
				percentageBottomInsetPx = 1f,
			)

		assertEquals(24f, compactLayout.amount.xPx, 0.001f)
		assertEquals(18f, compactLayout.amount.baselinePx, 0.001f)
		assertEquals(compactLayout.amount, wideLayout.amount)
	}

	@Test
	fun `keeps bundled Sora metric bands separate at supported widget sizes`() {
		val cases =
			listOf(
				ProductionGeometryCase(
					rootWidthPx = 110f,
					rootHeightPx = 40f,
					paddingPx = 3f,
					density = 1f,
					usesResponsiveProgressSpacing = false,
				),
				ProductionGeometryCase(
					rootWidthPx = 110f,
					rootHeightPx = 40f,
					paddingPx = 3f,
					density = 1f,
					usesResponsiveProgressSpacing = true,
				),
				ProductionGeometryCase(
					rootWidthPx = 180f,
					rootHeightPx = 56f,
					paddingPx = 5f,
					density = 1f,
					usesResponsiveProgressSpacing = false,
				),
				ProductionGeometryCase(
					rootWidthPx = 180f,
					rootHeightPx = 56f,
					paddingPx = 5f,
					density = 1f,
					usesResponsiveProgressSpacing = true,
				),
			)

		cases.forEach { case ->
			val canvasWidthPx = case.rootWidthPx - 2f * case.paddingPx
			val canvasHeightPx =
				calculateBudgetArtworkHeightPx(
					rootHeightPx = case.rootHeightPx,
					verticalPaddingPx = case.paddingPx,
					hasBudget = true,
					usesResponsiveProgressSpacing = case.usesResponsiveProgressSpacing,
					density = case.density,
				).toFloat()
			val wholeSizePx =
				calculateConfiguredBudgetWholeSizePx(
					artworkHeightPx = canvasHeightPx,
					widthLimitedSizePx = Float.POSITIVE_INFINITY,
				)
			val amount =
				WidgetTextMetrics(
					widthPx = canvasWidthPx * 0.6f,
					ascentPx = wholeSizePx * SORA_ASCENT_PER_EM,
					descentPx = wholeSizePx * SORA_DESCENT_PER_EM,
				)
			val percentageNumberSizePx = wholeSizePx * PERCENTAGE_NUMBER_SIZE_RATIO
			val percentageSymbolSizePx = wholeSizePx * PERCENTAGE_SYMBOL_SIZE_RATIO
			val percentageSymbolRaisePx =
				percentageSymbolSizePx * PERCENTAGE_SYMBOL_RAISE_RATIO
			val percentage =
				WidgetTextMetrics(
					widthPx = canvasWidthPx * 0.12f,
					ascentPx =
						minOf(
							percentageNumberSizePx * SORA_ASCENT_PER_EM,
							percentageSymbolSizePx * SORA_ASCENT_PER_EM -
								percentageSymbolRaisePx,
						),
					descentPx =
						maxOf(
							percentageNumberSizePx * SORA_DESCENT_PER_EM,
							percentageSymbolSizePx * SORA_DESCENT_PER_EM -
								percentageSymbolRaisePx,
						),
				)
			val layout =
				calculateBudgetTextPositions(
					canvasWidthPx = canvasWidthPx,
					canvasHeightPx = canvasHeightPx,
					amount = amount,
					percentage = percentage,
					percentageBottomInsetPx =
						case.rootHeightPx * PERCENTAGE_BOTTOM_INSET_RATIO,
				)
			val amountTopPx = layout.amount.baselinePx + amount.ascentPx
			val amountBottomPx = layout.amount.baselinePx + amount.descentPx
			val percentageTopPx = layout.percentage.baselinePx + percentage.ascentPx
			val percentageBottomPx = layout.percentage.baselinePx + percentage.descentPx

			assertTrue("amount clips at $case", amountTopPx >= 0f)
			assertTrue("percentage clips at $case", percentageBottomPx <= canvasHeightPx)
			assertTrue(
				"text bands overlap at $case",
				amountBottomPx <= percentageTopPx,
			)
			assertEquals(
				canvasWidthPx,
				layout.percentage.xPx + percentage.widthPx,
				0.001f,
			)
		}
	}

	@Test
	fun `right aligns the percentage below the amount without overlap`() {
		val cases =
			listOf(
				LayoutCase(
					canvasWidthPx = 100f,
					canvasHeightPx = 28f,
					amount = WidgetTextMetrics(widthPx = 52f, ascentPx = -10f, descentPx = 2f),
					percentage = WidgetTextMetrics(widthPx = 14f, ascentPx = -4f, descentPx = 1f),
					bottomInsetPx = 1f,
					expectedPercentageX = 86f,
					expectedPercentageBaseline = 26f,
				),
				LayoutCase(
					canvasWidthPx = 220f,
					canvasHeightPx = 56f,
					amount = WidgetTextMetrics(widthPx = 100f, ascentPx = -20f, descentPx = 4f),
					percentage = WidgetTextMetrics(widthPx = 24f, ascentPx = -7f, descentPx = 2f),
					bottomInsetPx = 2f,
					expectedPercentageX = 196f,
					expectedPercentageBaseline = 52f,
				),
			)

		cases.forEach { case ->
			val layout =
				calculateBudgetTextPositions(
					canvasWidthPx = case.canvasWidthPx,
					canvasHeightPx = case.canvasHeightPx,
					amount = case.amount,
					percentage = case.percentage,
					percentageBottomInsetPx = case.bottomInsetPx,
				)

			assertEquals(case.expectedPercentageX, layout.percentage.xPx, 0.001f)
			assertEquals(
				case.expectedPercentageBaseline,
				layout.percentage.baselinePx,
				0.001f,
			)
			assertEquals(
				case.canvasWidthPx,
				layout.percentage.xPx + case.percentage.widthPx,
				0.001f,
			)
			assertTrue(
				layout.amount.baselinePx + case.amount.descentPx <=
					layout.percentage.baselinePx + case.percentage.ascentPx,
			)
		}
	}

	private data class LayoutCase(
		val canvasWidthPx: Float,
		val canvasHeightPx: Float,
		val amount: WidgetTextMetrics,
		val percentage: WidgetTextMetrics,
		val bottomInsetPx: Float,
		val expectedPercentageX: Float,
		val expectedPercentageBaseline: Float,
	)

	private data class ProductionGeometryCase(
		val rootWidthPx: Float,
		val rootHeightPx: Float,
		val paddingPx: Float,
		val density: Float,
		val usesResponsiveProgressSpacing: Boolean,
	)

	private companion object {
		const val SORA_ASCENT_PER_EM = -0.97f
		const val SORA_DESCENT_PER_EM = 0.29f
		const val PERCENTAGE_NUMBER_SIZE_RATIO = 0.34f
		const val PERCENTAGE_SYMBOL_SIZE_RATIO = 0.28f
		const val PERCENTAGE_SYMBOL_RAISE_RATIO = 0f
		const val PERCENTAGE_BOTTOM_INSET_RATIO = 0f
	}
}
