# ČSOB Card Expense Widget Design

Date: 2026-08-02

Status: Design approved; awaiting written-spec review

## Summary

Build an Android-only expense tracker named `Výdavky` on Expo SDK 57 and TypeScript. The first version captures ČSOB SmartBanking card transaction notifications while the application UI is closed, parses them with one production TypeScript parser, stores both the raw notification and normalized expense locally in SQLite, and updates a 2 × 2 Android home-screen widget with the current monthly total.

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
- Before the first source-code change, make that formatting rule explicit with `indent_size = 4` and `tab_width = 4` in `.editorconfig`, and `tabWidth: 4` with `useTabs: true` in Prettier. YAML keeps its existing two-space exception.
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
- hard source-event and semantic-candidate key construction,
- parser-version reprocessing eligibility,
- month selection, and
- monthly sum calculations.

This is the only production implementation of parsing, normalization, and key construction. The same functions exercised by Vitest run inside the Android Headless JS task. The application also uses the Vitest-covered monthly summarizer; the native widget implements the same small aggregation contract directly in SQLite because it must render without a JavaScript runtime.

### Background TypeScript Orchestration

`src/background/` registers and implements the `ExpenseInboxDrain` Headless JS task. A custom project entry point registers the task before importing `expo-router/entry` last. The task:

1. Requests eligible inbox records from the native module.
2. Runs the pure TypeScript parser and normalization pipeline.
3. Sends a typed result to the native module.
4. Resolves only after the native transaction and widget update request complete.

Each Headless invocation processes at most five sequential batches of 50 rows. Additional rows remain eligible for the next notification, listener reconnection, or application resume; while the UI is alive, it starts another invocation only after the previous drain promise settles.

After committing a raw row, the already system-bound notification listener calls `applicationContext.startService()` with an explicit intent for an unexported `ExpenseHeadlessJsTaskService : HeadlessJsTaskService`. It must not call `startForegroundService()`: React Native 0.86.2's service does not promote itself with `startForeground()`, while Android 8+ requires that promotion within five seconds. Android's background-execution rules treat an app with a bound notification listener as foreground for ordinary background-service limits, but this path is still a release-build feasibility gate before the full feature is built.

The Headless configuration uses a 60-second timeout and `isAllowedInForeground = true`, because a notification can arrive while the application is open. Its manifest entry is `exported="false"`; the module declares `android.permission.WAKE_LOCK` for React Native's bounded task wake lock and `android.permission.RECEIVE_BOOT_COMPLETED` for rollover recovery, and it does not declare a foreground-service permission. The raw inbox, rather than intent payload, remains the source of truth. A failed `startService()` leaves rows pending for application startup, listener reconnection, or the next notification. `onListenerConnected()` also inspects currently active matching notifications so a still-visible notification can recover after a listener gap; a notification that appeared and disappeared while disconnected cannot be recovered.

### Local Expo Module

`modules/expense-notifications/` is a tracked local Expo module. Its Android implementation owns:

- `NotificationListenerService`,
- the native SQLite database and migrations,
- the unexported Headless JS task service and drain trigger,
- the Expo Modules bridge,
- `AppWidgetProvider` and `RemoteViews` resources,
- the month-rollover alarm and receiver,
- notification-access status and settings navigation, and
- native events that tell an open UI to refresh.

The module's library `AndroidManifest.xml` and `android/src/main/res` resources are merged by Android Gradle through Expo autolinking. The first version does not add a config plugin because no generated main-application code must be patched. App identity stays in `app.json`, and generated `android/` and `ios/` directories remain ignored rather than becoming the source of truth.

The custom native module requires an Android development or release build; Expo Go is not a supported runtime for this application.

### Application UI

`src/app/` becomes one primary summary screen plus a diagnostics modal or sheet, rather than the starter tab demo. It reads typed projections through the local module and does not open SQLite directly.

## Notification Input Contract

The native listener captures this structured envelope:

```ts
type InboxId = string;

type BankNotificationEnvelope = {
    inboxId: InboxId;
    notificationKey: string;
    packageName: string;
    postedAtMs: number;
    capturedAtMs: number;
    title: string | null;
    text: string | null;
    bigText: string | null;
    textLines: string[];
};
```

The listener stores all available representations because Android notifications may place the full content in `text`, `bigText`, or `textLines`. TypeScript builds normalized body candidates from `bigText`, `textLines.join("\n")`, and `text`. For each candidate it converts CRLF and bare CR to LF, removes only leading and trailing whitespace-only lines, trims every remaining line, and collapses each run of spaces or tabs to one ASCII space. Interior blank lines remain significant.

The selected body is the candidate with the greatest number of non-empty lines, then the greatest normalized string length, then the fixed tie order `bigText`, `textLines`, `text`. If every candidate is absent or empty, parsing returns `missing_body`. The three representations are alternative views of one notification, never three source events.

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

After candidate normalization, the parser requires package `com.zentity.sbank.csobsk`, exact title `Transakcia kartou`, and this complete grammar:

```text
BODY      := AMOUNT LF MERCHANT LF DATETIME LF CARD TAIL
TAIL      := empty | LF AVAILABLE | LF OWN | LF AVAILABLE LF OWN
AMOUNT    := "Suma: " DIGITS "," DIGIT DIGIT " EUR"
MERCHANT  := a non-empty line containing at least one non-whitespace character
DATETIME  := DD "." MM "." YYYY " " HH ":" mm
CARD      := "Karta **** " DIGIT DIGIT DIGIT DIGIT
AVAILABLE := "Disponibilný zostatok " ["-"] DIGITS "," DIGIT DIGIT " EUR"
OWN       := "Vlastné prostriedky " ["-"] DIGITS "," DIGIT DIGIT " EUR"
```

All digits are ASCII. Numeric fields have no thousands separator because the supplied fixture does not establish one. Date and time widths are exact; the date must exist in the Gregorian calendar, hours are `00` through `23`, and minutes are `00` through `59`. Either balance line may be absent; when both exist, their order is fixed. Balance lines are syntax-checked but remain only in the raw inbox record.

Outcome mapping is deterministic. Package and title mismatches return `unsupported_package` and `unsupported_title`; no usable candidate returns `missing_body`. Wrong line count, an empty merchant after normalization, an interior blank line, reversed balances, reordered fields, or an unknown trailing line returns `invalid_body_shape`. A line in the correct position that fails its field grammar returns `invalid_amount`, `invalid_datetime`, `invalid_card`, or `invalid_balance`. A syntactically valid zero amount returns `unsupported_amount`; a negative amount fails the unsigned amount grammar as `invalid_amount`.

Only a positive, non-zero EUR card expense is supported. Unknown currencies, negative or zero amounts, refunds, and reversal formats remain unparsed until a real fixture defines their semantics.

## Normalized Transaction

```ts
type CardExpense = {
    source: "csob-sk-smartbanking";
    kind: "card-expense";
    sourceNotificationKey: string;
    sourceEventKey: string;
    semanticCandidateKey: string;
    amountMinor: number;
    currency: "EUR";
    merchant: string;
    occurredAtLocal: string;
    timeZone: "Europe/Bratislava";
    monthKey: string;
    cardLast4: string;
};
```

- `123,45 EUR` becomes the integer `12345`; floating-point currency arithmetic is forbidden.
- `occurredAtLocal` uses `YYYY-MM-DDTHH:mm:00` without pretending that the notification includes an offset.
- `timeZone` explicitly records `Europe/Bratislava`.
- `monthKey` is `YYYY-MM` and comes from the validated bank timestamp, not the Android capture timestamp.
- `merchant` preserves display case after trimming and collapsing internal whitespace.
- All minor-unit values must be non-negative safe integers. Sum functions fail rather than silently exceed `Number.MAX_SAFE_INTEGER`.

## Deduplication

The supplied notification has no bank transaction ID. Two Android callbacks with identical parsed transaction fields can therefore be either a redelivery or two legitimate equal purchases. No deterministic rule over the available fields can always distinguish them, so the first version hard-deduplicates only an identical Android source event.

Pure TypeScript constructs `sourceEventKey` as this fixed-order canonical JSON array:

```text
["source-event-v1", packageName, notificationKey, postedAtMs,
 title, text, bigText, textLines]
```

Exact raw field values and ordered line duplicates are significant, and JSON preserves the distinction between `null` and an empty string. `capturedAtMs` and `inboxId` are deliberately absent so a later observation of the same source event stays idempotent. The canonical string is stored directly rather than hashed, avoiding a crypto dependency and eliminating hash-collision handling. SQLite enforces a unique constraint on `transactions.source_event_key`.

Every different `sourceEventKey` is counted, even if all parsed fields are equal. A changed `postedAtMs`, notification key, or raw field is a separate event. This policy can retain a duplicate when the bank reposts a notification, but it cannot silently discard a legitimate expense.

Pure TypeScript separately constructs a non-unique `semanticCandidateKey` with the same fixed-order JSON-array encoding from `source`, `kind`, normalized bank time, amount, currency, normalized merchant, and card suffix. It is used only to count possible-duplicate groups in diagnostics. It never suppresses a row or removes it from a monthly sum. Reuse of the same Android notification key with a different source-event key is likewise diagnostic information, not a merge rule.

## SQLite Model

SQLite is owned by the local Android module and uses WAL mode, foreign keys, bound parameters, and `PRAGMA user_version` migrations. No Room or `expo-sqlite` dependency is required for the first version.

### `notification_inbox`

Stores:

- native capture ID,
- Android notification key,
- package name and Android post time,
- title, text, big text, and serialized text lines,
- capture timestamp,
- the TypeScript-produced source-event key after any parser completion,
- a nullable link to the materialized transaction,
- status,
- last parser version,
- attempt count,
- last error code, and
- processing timestamp.

Allowed statuses are:

- `pending`: eligible for immediate processing,
- `processed`: recognized and transactionally completed, including a deduplicated completion,
- `unsupported`: the ČSOB title is not supported by that parser version,
- `unparsed`: supported title but invalid or unknown body format.

Every observed callback has an inbox row, including repeated observations of the same source event. Raw fields are retained after every outcome. The first version has no automatic deletion or retention window.

### `transactions`

Stores the normalized `CardExpense` fields, a unique `source_event_key`, a non-unique indexed `semantic_candidate_key`, and creation timestamp. Each processed inbox row links to the inserted or already existing transaction. Money is stored as SQLite `INTEGER` minor units.

There is no cached `monthly_totals` table. Materialized transaction rows after the unique source-event constraint are the sole source of truth.

### Monthly Projection Contract

For month `M`, include only persisted `card-expense` rows where `month_key = M` and `currency = EUR`. `transactionCount` is the row count, `totalMinor` is the checked integer sum of `amount_minor`, and an empty result is `{ totalMinor: 0, transactionCount: 0 }`. Raw inbox values and both balance lines never participate.

The application fetches the selected month's ordered transaction rows and calls the pure TypeScript `summarizeMonth` function, so mandatory Vitest sum tests exercise a production path. The widget independently applies the same contract without starting JavaScript:

```sql
SELECT COALESCE(SUM(amount_minor), 0), COUNT(*)
FROM transactions
WHERE kind = 'card-expense' AND month_key = ? AND currency = 'EUR';
```

The query uses SQLite integer `SUM`, never floating-point `total()`. Native code also rejects a result outside `0..Number.MAX_SAFE_INTEGER`, even though SQLite's signed 64-bit range is larger. SQLite integer overflow and TypeScript safe-integer overflow are explicit failures; neither runtime displays a wrapped or rounded total.

## Parser Versioning And Reprocessing

The TypeScript domain starts with `CURRENT_PARSER_VERSION = 1`. Every parse attempt records that integer on the inbox row. Released parser versions are strictly increasing positive integers; a value is never decreased or reused, including after a rollback release.

An inbox row is eligible when:

- its state is `pending`, or
- its state is `unsupported` or `unparsed` and `last_parser_version < CURRENT_PARSER_VERSION`.

Adding a newly supported title or format requires increasing `CURRENT_PARSER_VERSION`. On the first application launch or Headless JS task invocation after the update, older unsupported and unparsed raw records become eligible and the bounded drain starts retrying them. Further triggers continue a backlog larger than one invocation. Newly recognized records create historical expenses through the same transaction and dedupe path. If they affect the current month, the widget is recomputed immediately.

Already processed rows are not automatically reparsed. A future parser change that must rewrite successful historical transactions requires an explicit data migration so totals cannot change silently.

## End-To-End Data Flow

1. Android binds `NotificationListenerService` after the user grants notification access.
2. `onNotificationPosted` quickly copies supported Android extras and dispatches persistence to an IO executor.
3. The native repository inserts the raw ČSOB envelope into `notification_inbox` before attempting to start JavaScript.
4. After commit, the listener starts the ordinary local Headless JS service with an explicit intent; the service runs the registered task.
5. The task requests a bounded eligible batch and runs the TypeScript domain pipeline.
6. The task groups identical source-event keys within its batch. For a recognized expense, the bridge performs one SQLite transaction: insert-or-ignore by unique source-event key, link every corresponding inbox observation to that transaction, mark them processed, and read the current-month widget projection.
7. For an unsupported title or malformed body, the bridge persists the parser outcome and version without deleting raw content.
8. After a successful database commit, native code updates every widget instance and emits a refresh event if the application UI is alive.
9. Application startup invokes the same drain path so retained pending or newly reprocessable records recover without a special migration script.

## Failure And Recovery

- If JavaScript or the bridge fails before completion, the inbox row remains `pending`.
- Headless JS has a 60-second task timeout and bounded work. Unfinished rows are retried on the next ČSOB notification, listener reconnection, application launch, or explicitly enqueued follow-up drain.
- Completion is idempotent. Concurrent Headless tasks cannot create two transactions for one source event because the native transaction and unique `source_event_key` constraint are authoritative.
- Parser outcomes use stable error codes: `unsupported_package`, `unsupported_title`, `missing_body`, `invalid_body_shape`, `invalid_amount`, `unsupported_amount`, `invalid_datetime`, `invalid_card`, and `invalid_balance`.
- A supported title with malformed content becomes `unparsed`, not an unbounded retry loop. An unknown title becomes `unsupported` with the current parser version.
- Database failures are logged locally and do not update the widget. No telemetry or raw financial data leaves the device.
- A later parser version can reprocess retained `unsupported` and `unparsed` rows.
- The first native milestone must prove the Expo SDK 57 / React Native 0.86 ordinary-service Headless JS path in a release build before the full database, parser integration, and widget expand around it. The matrix covers API 26, 31, 34, 35, and 36 with the UI open, backgrounded, process killed without Force stop, and screen off or Doze. Passing means the listener commits a probe inbox row, cold-starts JavaScript, and receives at least one completion before the 60-second timeout, while the durable materialized effect occurs exactly once and no ANR or service-start exception occurs. Force stop is an expected negative case: Android does not resume capture until the user launches the app again. A failed feasibility checkpoint returns to architecture review rather than silently moving parsing into Kotlin.

## Notification Access

The application does not use `POST_NOTIFICATIONS` to read bank notifications. It declares a native listener service protected by `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` and sends the user to Android notification-listener settings.

Notification-access checks use exact API branches:

- API 27 and newer call `NotificationManager.isNotificationListenerAccessGranted(listenerComponent)`.
- API 24 through 26 call `NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)`. This package-level check is sufficient because the first version declares exactly one listener. AndroidX Core is already exposed by `expo-modules-core`, so this adds no dependency.

On API 30 and newer, settings navigation first tries `Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS` with `Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME = listenerComponent.flattenToString()`. If that intent is unavailable or throws, and on API 24 through 29, it uses `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`. Every intent is checked with `resolveActivity`; failure returns `settings_unavailable` rather than crashing.

The application rechecks access on every `onResume`, and the widget rechecks it on every render. `onListenerConnected()` and `onListenerDisconnected()` request a refresh but are not themselves treated as the authoritative grant state.

## Widget

The first widget uses `AppWidgetProvider` and XML `RemoteViews`, not Glance or `expo-widgets`.

The 2 × 2 layout shows:

- current Bratislava month,
- current monthly expense total,
- transaction count, and
- last successful update time.

Tapping the widget opens the application. With no current-month expenses it shows `0,00 €`. If notification access is revoked, it shows `Prístup vypnutý` instead of a potentially stale total.

If the native projection overflows or otherwise fails, the widget shows `Chyba súčtu` rather than the previous total. The application uses the same label for a TypeScript projection failure, and diagnostics sets `projectionError: true`.

The widget updates after every completed transaction and after reprocessing changes the current month. A one-shot inexact `AlarmManager.setAndAllowWhileIdle` alarm targets the next `Europe/Bratislava` month boundary, refreshes the projection, and schedules the following boundary. It is rescheduled when the widget is enabled, after boot, after `ACTION_MY_PACKAGE_REPLACED`, and after relevant system time or time-zone changes; it is cancelled after the last widget instance is removed.

The application does not request exact-alarm permission. The rollover is best effort: Doze and manufacturer scheduling may delay it with no strict upper bound. Any subsequent notification, application start, widget update, boot, time change, or time-zone change recomputes the current Bratislava month and corrects the display.

## Application UI

The selected design is summary-first.

- Remove the Expo starter tabs and sample content.
- Use one work-focused screen titled `Výdavky`.
- Provide previous/next month controls; disable navigation into a future month.
- Show a full-width monthly summary with total, transaction count, and textual ČSOB connection state.
- Show selected-month transactions as compact rows with separators, merchant, local time, masked card, and amount.
- When access is missing, replace the connected state with a clear `Povoliť prístup` command.
- Put a diagnostics modal or sheet behind the app-bar menu. Diagnostics show counts for `pending`, `unsupported`, `unparsed`, semantic possible-duplicate groups, and reused Android notification keys; raw payload display and editing are out of scope.
- Use automatic light and dark modes, neutral surfaces, red for expense totals, green plus text for connected state, and a restrained yellow accent.
- Avoid decorative page cards, nested cards, gradients, and oversized hero typography.

## Bridge Surface

The Expo module name is `ExpenseNotifications`. Its TypeScript wrapper exposes these exact asynchronous methods:

- `getNotificationAccessStatusAsync()`
- `openNotificationAccessSettingsAsync()`
- `getEligibleInboxBatchAsync(parserVersion, limit)`
- `completeInboxItemsAsync(completion)`
- `requestInboxDrainAsync()`
- `getMonthTransactionsAsync(monthKey)`
- `getDiagnosticsAsync()`

Bridge-facing SQLite IDs are base-10 strings because SQLite uses signed 64-bit IDs while JavaScript numbers stop being exact above `Number.MAX_SAFE_INTEGER`. Timestamps remain numbers after a safe-integer check. The method DTOs are:

```ts
type NotificationAccessStatus = { granted: boolean };

type UnsupportedErrorCode = "unsupported_package" | "unsupported_title";

type UnparsedErrorCode =
    | "missing_body"
    | "invalid_body_shape"
    | "invalid_amount"
    | "unsupported_amount"
    | "invalid_datetime"
    | "invalid_card"
    | "invalid_balance";

type OpenSettingsResult =
    | { opened: true }
    | { opened: false; reason: "settings_unavailable" };

type EligibleInboxBatch = {
    items: BankNotificationEnvelope[];
    hasMore: boolean;
};

type InboxCompletion =
    | {
          outcome: "processed";
          inboxIds: [InboxId, ...InboxId[]];
          parserVersion: number;
          expense: CardExpense;
      }
    | {
          outcome: "unsupported";
          inboxIds: [InboxId, ...InboxId[]];
          sourceEventKey: string;
          parserVersion: number;
          errorCode: UnsupportedErrorCode;
      }
    | {
          outcome: "unparsed";
          inboxIds: [InboxId, ...InboxId[]];
          sourceEventKey: string;
          parserVersion: number;
          errorCode: UnparsedErrorCode;
      };

type CompletionResult =
    | { outcome: "processed"; transactionId: string; inserted: boolean }
    | { outcome: "unsupported" | "unparsed"; transactionId: null; inserted: false };

type DrainRequestResult = {
    disposition: "started" | "pending_after_start_failure";
};

type MonthTransactions = {
    monthKey: string;
    items: Array<CardExpense & { transactionId: string }>;
};

type Diagnostics = {
    pending: number;
    unsupported: number;
    unparsed: number;
    possibleDuplicateGroups: number;
    reusedNotificationKeys: number;
    projectionError: boolean;
};
```

The signatures return `Promise<NotificationAccessStatus>`, `Promise<OpenSettingsResult>`, `Promise<EligibleInboxBatch>`, `Promise<CompletionResult>`, `Promise<DrainRequestResult>`, `Promise<MonthTransactions>`, and `Promise<Diagnostics>` in the method order listed above. The parser version and batch limit are positive safe integers, `monthKey` must match `YYYY-MM`, and invalid bridge input rejects before reaching SQL. Stable native rejection codes are `ERR_INVALID_ARGUMENT`, `ERR_DATABASE`, and `ERR_PROJECTION_OVERFLOW`.

`completeInboxItemsAsync` commits one completion plus every inbox ID represented by it atomically. `onExpenseDataChanged` emits `{ monthKeys: string[] }`; `onNotificationAccessChanged` emits `{ granted: boolean }`. Native-only widget projection and refresh functions are not exported.

JavaScript never receives a database path and never opens the SQLite file directly.

## Testing

Vitest runs in a Node environment against pure TypeScript only. Tests are written before each domain implementation.

Required parser coverage includes:

- the exact anonymized ČSOB fixture,
- LF, CRLF, and bare-CR bodies,
- no balance lines, either valid balance line, and both valid balance lines in fixed order,
- normalized outer and repeated horizontal whitespace,
- wrong package and wrong title,
- missing merchant, date, card, or amount,
- invalid calendar dates and times,
- invalid decimal shape, currency, card suffix, zero, and negative values, and
- malformed or reversed balances, interior blank lines, extra lines, and reordered required lines.

Required normalization coverage includes:

- exact integer-cent conversion,
- merchant display and canonical normalization,
- ISO local time and `Europe/Bratislava` month key,
- safe-integer guards, and
- deterministic body selection by non-empty line count, normalized length, and fixed representation tie order.

Required dedupe coverage includes:

- identical raw envelopes with different capture IDs or capture times create the same source-event key,
- changed Android post time, notification key, or any exact raw field creates a distinct source-event key,
- canonical encoding distinguishes `null` from empty strings and preserves text-line order and duplicates,
- repeated processing and parser-version retry of one source event materialize one pure projection,
- distinct source events with equal semantic candidate keys both remain in the sum and form a possible-duplicate group, and
- changed normalized transaction identity creates a different semantic candidate key.

Required sum coverage includes:

- an empty month,
- one and multiple expenses,
- filtering by month,
- ignoring non-expense values in generic fixtures,
- source-event-deduplicated input while preserving distinct semantic candidates, and
- safe-integer overflow rejection.

Required parser-version coverage includes pending eligibility, no repeated attempt at the same version, and re-eligibility after a version increase.

React Native components, Expo modules, native SQLite, and Kotlin are not imported into Vitest. Native verification consists of Android compilation plus manual device acceptance scenarios.

## Manual Acceptance Scenarios

1. Fresh install shows notification access disabled and opens the correct Android settings screen.
2. After access is granted, a matching ČSOB card notification is captured while the application UI is closed.
3. The supplied fixture creates exactly one `123,45 €` expense for August 2026 and retains the complete raw envelope.
4. Redelivery of the exact same source event does not increase the total twice.
5. Two distinct source events with equal parsed fields are both counted and appear as a possible-duplicate diagnostic group.
6. A malformed matching notification remains visible in diagnostic counts and does not affect the total.
7. A previously unsupported raw title is processed after a release increases the parser version and adds support.
8. The widget updates after processing without opening the UI.
9. UI and widget totals and counts agree for empty, single-expense, exact-redelivery, and cross-month fixtures.
10. On a new month with no transaction, the widget changes to `0,00 €` when Android delivers the scheduled inexact rollover or on the next refresh trigger.
11. Revoking notification access changes the widget to `Prístup vypnutý` on its next native refresh.
12. Reboot and ordinary process death do not lose captured inbox rows; Force stop prevents capture until the user launches the app again.

## Implementation Sequence

The implementation plan will divide work into independently verified small steps:

1. Make tab width four explicit, set the Android identity and display name, scaffold the local Expo module without a config plugin, and prove a release Android build.
2. Add a minimal raw SQLite probe and pass the ordinary-service Headless JS feasibility matrix. Stop for architecture review if it fails.
3. Build fixture types, body selection, the strict ČSOB parser, normalization, source-event and semantic keys, parser versioning, and monthly calculations through Vitest red-green cycles.
4. Expand the native SQLite schema/repository and implement the exact typed bridge.
5. Connect the production Headless drain, transactional completions, retries, and application-start recovery.
6. Complete the filtered notification listener, active-notification recovery, and notification-access onboarding.
7. Add the native widget and best-effort month-rollover scheduling.
8. Replace the starter UI with the summary screen and diagnostics.
9. Run the API-level feasibility regression and end-to-end device acceptance scenarios.

Every step keeps `master`, avoids unrelated refactoring, and runs lint, TypeScript, and `npm run test` before completion.

## Authoritative References

- [Expo SDK 57 reference](https://docs.expo.dev/versions/v57.0.0/)
- [Expo custom native code and local modules](https://docs.expo.dev/workflow/customizing/)
- [Expo Continuous Native Generation](https://docs.expo.dev/workflow/continuous-native-generation/)
- [Expo Router custom entry point](https://docs.expo.dev/router/installation/)
- [React Native 0.86 Headless JS for Android](https://reactnative.dev/docs/0.86/headless-js-android)
- [Android 8 background execution limits](https://developer.android.com/about/versions/oreo/background)
- [Android foreground-service start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Android NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
- [Android NotificationManager access check](https://developer.android.com/reference/android/app/NotificationManager#isNotificationListenerAccessGranted(android.content.ComponentName))
- [AndroidX NotificationManagerCompat access check](https://developer.android.com/reference/androidx/core/app/NotificationManagerCompat#getEnabledListenerPackages(android.content.Context))
- [Android app widgets](https://developer.android.com/develop/ui/views/appwidgets)
- [Android AlarmManager](https://developer.android.com/reference/android/app/AlarmManager)
