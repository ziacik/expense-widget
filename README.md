# Expense Widget

An Android expense tracker that turns ČSOB SmartBanking card-payment notifications into monthly spending insights, budgets, and home screen widgets.

> [!NOTE]
> Expense Widget currently supports positive EUR card transactions from Slovak-language ČSOB SmartBanking notifications on Android 7.0 and newer.

## Features

- Automatically records ČSOB card expenses from Android notifications.
- Shows monthly totals, transaction history, merchants, and payment details.
- Tracks progress against a configurable monthly budget.
- Provides a compact 1×1 monthly-spending widget and a 2×1 budget widget.
- Stores expense data and settings locally on the device.
- Supports light and dark system themes.

## How it works

1. The user grants Android notification access to Expense Widget.
2. A native notification listener filters events from the ČSOB SmartBanking app.
3. Supported card-transaction notifications are parsed, normalized, and stored in a local SQLite database.
4. The app and its widgets update with the current month's spending and budget progress.

Expense Widget does not connect to a bank API and does not require ČSOB credentials.

## Privacy

Captured ČSOB notifications, parsed transactions, and the monthly budget remain in the app's private on-device storage, with Android backups disabled. The project contains no backend, analytics, upload, or cloud-synchronization code.

Android notification access is a powerful permission. Expense Widget discards events from other apps before extracting or storing their notification contents. Captured ČSOB notification records currently have no automatic retention limit or deletion UI.

## Development

### Prerequisites

- Node.js and npm
- Android Studio with the Android SDK
- An Android emulator or physical device

Install dependencies and create a development build:

```bash
npm install
npm run android
```

This project contains a native Android module, so it cannot run in Expo Go. To exercise automatic tracking, enable notification access in the app and use a device with ČSOB SmartBanking installed.

Run the checks:

```bash
npm test
npm run lint
```

## Tech stack

- Expo 57, React Native, React, and TypeScript
- Kotlin native module with `NotificationListenerService`
- SQLite and Android `RemoteViews` widgets
- Vitest and JUnit

## Limitations

- Android only
- ČSOB SmartBanking for Slovakia only
- Positive EUR card transactions only
- Dependent on the bank's current Slovak notification format
