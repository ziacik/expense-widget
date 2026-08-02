# ČSOB Card Expense Widget Design

Date: 2026-08-02

Status: Approved in the design conversation

## Summary

Build an Android-only expense tracker on Expo SDK 57 and TypeScript. The first version captures ČSOB SmartBanking card transaction notifications while the application UI is closed, parses them with one production TypeScript parser, stores both the raw notification and normalized expense locally in SQLite, and updates a 2 × 2 Android home-screen widget with the current monthly total.

The Android application ID is `sk.ziacik.expensewidget`. The ČSOB source package is `com.zentity.sbank.csobsk`.

## Scope

### Included

- Android only.
- ČSOB Slovensko SmartBanking package `com.zentity.sbank.csobsk`.
- Notification title `Transakcia kartou`.
- The supplied Slovak card-transaction body format.
- Capture through `NotificationListenerService` while the UI is closed.
- Durable native SQLite inbox before JavaScript processing begins.
- Headless JS execution of the production TypeScript domain logic.
- Raw notification retention in SQLite without an automatic retention limit.
- Normalized card expenses, conservative deduplication, parser versioning, monthly sums, and historical month queries.
- A single summary-first application screen and a 2 × 2 Android widget.
- Automatic widget rollover at the start of a new Bratislava month.

### Excluded

- Other ČSOB notification titles or formats until anonymized samples are available.
- Other banks.
- Refunds, reversals, cash withdrawals, transfers, income, categorization, budgets, cloud sync, export, and editing.
- iOS and web behavior.
- Displaying or editing raw notifications in the first UI.
- Jest.
- Vitest tests for React Native components, Expo modules, SQLite integration, or Kotlin code.

## Project Constraints

- Keep the Git branch as `master`.
- Use tabs with a width of 4 in source and configuration files.
- Keep Expo SDK, React Native, React, TypeScript, ESLint, and their current versions unchanged unless a separate explanation and approval precede a version change.
- Do not introduce Nx.
- Do not add a dependency unless the implementation cannot reasonably use the existing Expo, React Native, Android, or Java/Kotlin standard APIs.
- Use Vitest only for pure TypeScript domain logic.
- Add new domain behavior test-first: failing test, minimal implementation, then refactor only if needed.
- After every project change, run:

```text
npx expo lint
npx tsc --noEmit
npm run test
```

## Architecture

### Pure TypeScript Domain

`src/domain/transactions/` contains no React Native, Expo, native-module, or SQLite imports. It owns:

- source notification types,
- the ČSOB card parser,
- strict money parsing to integer minor units,
- local date validation and normalization,
- merchant normalization,
- transaction dedupe-key construction,
- parser-version reprocessing eligibility,
- month selection, and
- monthly sum calculations.

This is the only production parser implementation. The same functions exercised by Vitest run inside the Android Headless JS task.

### Background TypeScript Orchestration

`src/background/` registers and implements a Headless JS task. A custom project entry point registers this task before importing `expo-router/entry` last. The task:

1. Requests eligible inbox records from the native module.
2. Runs the pure TypeScript parser and normalization pipeline.
3. Sends a typed result to the native module.
4. Resolves only after the native transaction and widget update request complete.

The task processes a bounded batch and can be invoked again when more eligible rows remain.

### Local Expo Module

`modules/expense-notifications/` is a tracked local Expo module. Its Android implementation owns:

- `NotificationListenerService`,
- the native SQLite database and migrations,
- the Headless JS task service trigger,
- the Expo Modules bridge,
- `AppWidgetProvider` and `RemoteViews` resources,
- the month-rollover alarm and receiver,
- notification-access status and settings navigation, and
- native events that tell an open UI to refresh.

The module includes a config plugin that applies durable manifest and resource configuration during Expo prebuild. Generated `android/` and `ios/` directories remain ignored and are not the source of truth.

### Application UI

`src/app/` becomes a single-screen application rather than the starter tab demo. It reads typed projections through the local module and does not open SQLite directly.

## Notification Input Contract

The native listener captures this structured envelope:

```ts
type BankNotificationEnvelope = {
    inboxId: number;
    notificationKey: string;
    packageName: string;
    postedAtMs: number;
    title: string | null;
    text: string | null;
    bigText: string | null;
    textLines: string[];
};
```

The listener stores all available representations because Android notifications may place the full content in `text`, `bigText`, or `textLines`. TypeScript selects the fullest non-empty body through a deterministic normalization function.

Native filtering accepts only `com.zentity.sbank.csobsk`. Title and body interpretation belong to the TypeScript parser so previously unknown ČSOB formats remain available for later parser versions.

## ČSOB Parser Contract

The first supported fixture is:

```text
Title: Transakcia kartou

Suma: 123,45 EUR
BILLA 140
02.08.2026 19:21
Karta **** 8794
Disponibilný zostatok 2345,67 EUR
Vlastné prostriedky -1234,56 EUR
```

The parser requires:

- package name `com.zentity.sbank.csobsk`,
- exact title `Transakcia kartou`,
- a first line in the form `Suma: <digits>,<two digits> EUR`,
- a non-empty merchant line,
- a valid calendar date and time in `DD.MM.YYYY HH:mm`, and
- a card line in the form `Karta **** <four digits>`.

Trailing balance lines are preserved in the raw inbox record but are neither required nor normalized as expense data. Line endings may be LF or CRLF. Outer whitespace and repeated horizontal whitespace are normalized; field order remains strict to prevent false positives.

Only a positive, non-zero EUR card expense is supported. Unknown currencies, negative or zero amounts, refunds, and reversal formats remain unparsed until a real fixture defines their semantics.

## Normalized Transaction

```ts
type CardExpense = {
    source: "csob-sk-smartbanking";
    kind: "card-expense";
    sourceNotificationKey: string;
    amountMinor: number;
    currency: "EUR";
    merchant: string;
    occurredAtLocal: string;
    timeZone: "Europe/Bratislava";
    monthKey: string;
    cardLast4: string;
    dedupeKey: string;
};
```

- `123,45 EUR` becomes the integer `12345`; floating-point currency arithmetic is forbidden.
- `occurredAtLocal` uses `YYYY-MM-DDTHH:mm:00` without pretending that the notification includes an offset.
- `timeZone` explicitly records `Europe/Bratislava`.
- `monthKey` is `YYYY-MM` and comes from the validated bank timestamp, not the Android capture timestamp.
- `merchant` preserves display case after trimming and collapsing internal whitespace.
- All minor-unit values must be non-negative safe integers. Sum functions fail rather than silently exceed `Number.MAX_SAFE_INTEGER`.

## Deduplication

The TypeScript dedupe key is a canonical JSON tuple containing:

1. source package,
2. Android notification key,
3. normalized bank transaction time,
4. amount in minor units,
5. currency,
6. normalized merchant, and
7. card last four digits.

The key is stored directly rather than hashed, avoiding an additional crypto dependency and hash-collision behavior. SQLite enforces a unique constraint on it.

This is deliberately conservative. Updates of the same Android notification with the same transaction fields are idempotent. Two different Android notification keys remain two expenses because the supplied bank payload has no stable bank transaction ID. The design prefers preserving a possible legitimate expense over silently deleting it as a false duplicate.

## SQLite Model

SQLite is owned by the local Android module and uses WAL mode, foreign keys, bound parameters, and `PRAGMA user_version` migrations. No Room or `expo-sqlite` dependency is required for the first version.

### `notification_inbox`

Stores:

- native capture ID,
- Android notification key and capture fingerprint,
- package name and Android post time,
- title, text, big text, and serialized text lines,
- capture timestamp,
- status,
- last parser version,
- attempt count,
- last error code, and
- processing timestamp.

Allowed statuses are:

- `pending`: eligible for immediate processing,
- `processed`: recognized and transactionally completed, including a deduplicated completion,
- `unsupported`: package captured but title is not supported by that parser version,
- `unparsed`: supported title but invalid or unknown body format.

Raw fields are retained after every outcome. The first version has no automatic deletion or retention window.

### `transactions`

Stores the normalized `CardExpense` fields, a unique dedupe key, the originating inbox ID, and creation timestamp. Money is stored as SQLite `INTEGER` minor units.

Monthly overview queries filter by `month_key` and return integer minor-unit totals, transaction count, and ordered transaction rows.

## Parser Versioning And Reprocessing

The TypeScript domain exports an integer `CURRENT_PARSER_VERSION`. Every parse attempt records that version on the inbox row.

An inbox row is eligible when:

- its state is `pending`, or
- its state is `unsupported` or `unparsed` and `last_parser_version < CURRENT_PARSER_VERSION`.

Adding a newly supported title or format requires increasing `CURRENT_PARSER_VERSION`. On the first application launch or Headless JS task invocation after the update, older unsupported and unparsed raw records are retried. Newly recognized records create historical expenses through the same transaction and dedupe path. If they affect the current month, the widget is recomputed immediately.

Already processed rows are not automatically reparsed. A future parser change that must rewrite successful historical transactions requires an explicit data migration so totals cannot change silently.

## End-To-End Data Flow

1. Android binds `NotificationListenerService` after the user grants notification access.
2. `onNotificationPosted` quickly copies supported Android extras and dispatches persistence to an IO executor.
3. The native repository inserts the raw ČSOB envelope into `notification_inbox` before attempting to start JavaScript.
4. After commit, the listener triggers the registered Headless JS task.
5. The task requests a bounded eligible batch and runs the TypeScript domain pipeline.
6. For a recognized expense, the bridge performs one SQLite transaction: insert-or-ignore by dedupe key, mark the inbox row processed, and read the current-month widget projection.
7. For an unsupported title or malformed body, the bridge persists the parser outcome and version without deleting raw content.
8. After a successful database commit, native code updates every widget instance and emits a refresh event if the application UI is alive.
9. Application startup invokes the same drain path so retained pending or newly reprocessable records recover without a special migration script.

## Failure And Recovery

- If JavaScript or the bridge fails before completion, the inbox row remains `pending`.
- Headless JS uses a bounded retry policy. Exhausted work is retried on the next ČSOB notification or application launch.
- Completion is idempotent. Concurrent Headless tasks cannot create two transactions because the native transaction and unique dedupe constraint are authoritative.
- A supported title with malformed content becomes `unparsed` with a stable error code, not an unbounded retry loop.
- An unsupported title becomes `unsupported` with the current parser version.
- Database failures are logged locally and do not update the widget. No telemetry or raw financial data leaves the device.
- A later parser version can reprocess retained `unsupported` and `unparsed` rows.
- The first native milestone must prove the Expo SDK 57 / React Native 0.86 Headless JS path on a real Android build before the full listener and widget implementation expands around it. A failed feasibility checkpoint returns to architecture review rather than silently moving parsing into Kotlin.

## Notification Access

The application does not use `POST_NOTIFICATIONS` to read bank notifications. It declares a native listener service protected by `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` and sends the user to Android notification-listener settings.

The local module exposes notification-access status through `NotificationManager.isNotificationListenerAccessGranted`. On supported versions it opens the component-specific settings screen, with the general notification-listener settings screen as fallback. Returning to the app triggers a fresh status check because the settings activity does not return a permission result.

## Widget

The first widget uses `AppWidgetProvider` and XML `RemoteViews`, not Glance or `expo-widgets`.

The 2 × 2 layout shows:

- current Bratislava month,
- current monthly expense total,
- transaction count, and
- last successful update time.

Tapping the widget opens the application. With no current-month expenses it shows `0,00 €`. If notification access is revoked, it shows `Prístup vypnutý` instead of a potentially stale total.

The widget updates after every completed transaction and after reprocessing changes the current month. A one-shot inexact `AlarmManager.setAndAllowWhileIdle` alarm targets the next `Europe/Bratislava` month boundary, refreshes the projection, and schedules the following boundary. It is rescheduled when the widget is enabled, after boot, and after relevant system time or time-zone changes.

The application does not request exact-alarm permission. Android may delay the month reset in deep idle, typically by up to roughly 15 minutes. This is acceptable for the first version.

## Application UI

The selected design is summary-first.

- Remove the Expo starter tabs and sample content.
- Use one work-focused screen titled `Výdavky`.
- Provide previous/next month controls; disable navigation into a future month.
- Show a full-width monthly summary with total, transaction count, and textual ČSOB connection state.
- Show selected-month transactions as compact rows with separators, merchant, local time, masked card, and amount.
- When access is missing, replace the connected state with a clear `Povoliť prístup` command.
- Put diagnostics behind the app-bar menu. Diagnostics show counts for `pending`, `unsupported`, and `unparsed`; raw payload display and editing are out of scope.
- Use automatic light and dark modes, neutral surfaces, red for expense totals, green plus text for connected state, and a restrained yellow accent.
- Avoid decorative page cards, nested cards, gradients, and oversized hero typography.

## Bridge Surface

The exact names may be refined during the implementation plan, but the ownership boundary is fixed. The native module must provide typed equivalents of:

- notification-access status,
- open notification-access settings,
- fetch eligible inbox records for a parser version and batch limit,
- atomically complete a recognized, unsupported, or unparsed result,
- query a month overview and transaction rows,
- query diagnostic counts, and
- subscribe to data/access changes while the UI is alive.

JavaScript never receives a database path and never opens the SQLite file directly.

## Testing

Vitest runs in a Node environment against pure TypeScript only. Tests are written before each domain implementation.

Required parser coverage includes:

- the exact anonymized ČSOB fixture,
- LF and CRLF bodies,
- optional trailing balance lines,
- normalized outer and repeated horizontal whitespace,
- wrong package and wrong title,
- missing merchant, date, card, or amount,
- invalid calendar dates and times,
- invalid decimal shape, currency, card suffix, zero, and negative values, and
- extra or reordered required lines.

Required normalization coverage includes:

- exact integer-cent conversion,
- merchant display and canonical normalization,
- ISO local time and `Europe/Bratislava` month key,
- safe-integer guards, and
- deterministic selection of the fullest notification body.

Required dedupe coverage includes:

- identical input creates an identical key,
- Android updates with the same essential fields remain identical,
- changes to notification key or any normalized transaction identity field remain distinct, and
- SQLite-facing duplicate outcomes do not change pure monthly projections.

Required sum coverage includes:

- an empty month,
- one and multiple expenses,
- filtering by month,
- ignoring non-expense values in generic fixtures,
- duplicate-eliminated input, and
- safe-integer overflow rejection.

Required parser-version coverage includes pending eligibility, no repeated attempt at the same version, and re-eligibility after a version increase.

React Native components, Expo modules, native SQLite, and Kotlin are not imported into Vitest. Native verification consists of Android compilation plus manual device acceptance scenarios.

## Manual Acceptance Scenarios

1. Fresh install shows notification access disabled and opens the correct Android settings screen.
2. After access is granted, a matching ČSOB card notification is captured while the application UI is closed.
3. The supplied fixture creates exactly one `123,45 €` expense for August 2026 and retains the complete raw envelope.
4. Reposting or updating the same Android notification does not increase the total twice.
5. A malformed matching notification remains visible in diagnostic counts and does not affect the total.
6. A previously unsupported raw title is processed after a release increases the parser version and adds support.
7. The widget updates after processing without opening the UI.
8. On a new month with no transaction, the widget changes to `0,00 €` after the scheduled inexact rollover.
9. Revoking notification access changes the widget to `Prístup vypnutý` on its next native refresh.
10. Reboot and process death do not lose already captured inbox rows.

## Implementation Sequence

The implementation plan will divide work into independently verified small steps:

1. Pure TypeScript fixture types and strict ČSOB parser.
2. Normalization, dedupe key, parser versioning, and monthly calculations.
3. Local Expo module scaffold, CNG config plugin, and Android build proof.
4. Native SQLite inbox/repository and typed bridge.
5. Headless JS feasibility checkpoint and durable drain orchestration.
6. Notification listener and notification-access onboarding.
7. Native widget and month-rollover scheduling.
8. Summary-first application UI and diagnostics.
9. Real-device end-to-end acceptance pass.

Every step keeps `master`, avoids unrelated refactoring, and runs lint, TypeScript, and `npm run test` before completion.

## Authoritative References

- [Expo SDK 57 reference](https://docs.expo.dev/versions/v57.0.0/)
- [Expo custom native code and local modules](https://docs.expo.dev/workflow/customizing/)
- [Expo Continuous Native Generation](https://docs.expo.dev/workflow/continuous-native-generation/)
- [Expo Router custom entry point](https://docs.expo.dev/router/installation/)
- [React Native Headless JS for Android](https://reactnative.dev/docs/headless-js-android)
- [Android NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
- [Android app widgets](https://developer.android.com/develop/ui/views/appwidgets)
- [Android AlarmManager](https://developer.android.com/reference/android/app/AlarmManager)
