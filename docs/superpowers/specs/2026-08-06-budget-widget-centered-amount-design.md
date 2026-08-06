# Centered Budget Widget Amount Design

**Date:** 2026-08-06

## Goal

Make the configured Android budget widget easier to scan by giving the spent amount sole visual priority in the center. Show the budget percentage as a small, right-aligned status label immediately above the progress bar.

## Current State

The runtime widget uses `WidgetTypography.budgetBitmap` to draw the amount and percentage on one horizontal baseline. This forces the amount to the left even though it is the primary value. The progress bar remains a separate Android `ProgressBar` below that bitmap. The static launcher preview in `budget_widget.xml` mirrors the same side-by-side arrangement.

## Chosen Design

Keep the existing native Android `RemoteViews` architecture and responsive bitmap rendering.

When a monthly budget is configured:

- Draw the complete amount group (whole units, cents, and euro symbol) horizontally centered, independent of the percentage label.
- Preserve the amount's existing optical vertical centering and Sora typography.
- Draw the percentage in a compact lower-right position inside the typography bitmap, directly above the separate progress bar.
- Render the percentage number at 34% of the whole-amount font size in Sora Semibold. Render the percent symbol at 28% in Sora Medium, retaining the existing small raised-symbol treatment.
- Keep the percentage label right-aligned with the progress bar's right edge.
- Preserve the existing percentage colors: green below 75%, amber from 75% through 99%, and red at 100% or above.
- Size the amount from its own available width rather than reserving horizontal room for the percentage. Long amounts must still shrink to fit without clipping.
- Keep the progress calculation, clamping, height, margin, and colors unchanged.

Update the static launcher preview layout to show the same hierarchy: centered amount, small percentage at the lower right, and the progress bar beneath it.

## Unchanged Behavior

- The state without a configured monthly budget remains unchanged, including its “set budget” prompt and hidden progress bar.
- Error and notification-access states remain unchanged.
- Tapping the widget still opens the application.
- Existing content descriptions continue to announce the month, spent amount, budget amount, and percentage.
- The total-expense widget is outside this change.

## Layout Safety

The percentage occupies a distinct lower band in the bitmap and must not affect the amount's horizontal center. At the provider's minimum 110 × 40 dp size, the amount and percentage bounds must not overlap or clip. The same constraints apply to a representative larger launcher size.

## Testing and Verification

- Add a pure layout calculation that accepts measured text bounds and returns amount and percentage positions.
- Add Android unit tests first to prove that the amount center matches the bitmap center, the percentage is right-aligned below the amount, and the two bounds do not overlap at minimum and representative widget sizes.
- Compile Android resources to validate the updated static preview layout.
- Run the Android unit tests, the existing Vitest suite, lint, and an Android debug build.
- If a connected emulator or device is available, render the configured widget and visually confirm the alignment at its supported size.
