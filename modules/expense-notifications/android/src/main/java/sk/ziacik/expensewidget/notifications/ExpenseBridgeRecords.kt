package sk.ziacik.expensewidget.notifications

import expo.modules.kotlin.records.Field
import expo.modules.kotlin.records.Record
import expo.modules.kotlin.types.OptimizedRecord

@OptimizedRecord
data class CardExpenseRecord(
	@Field val source: String,
	@Field val kind: String,
	@Field val sourceNotificationKey: String,
	@Field val sourceEventKey: String,
	@Field val semanticCandidateKey: String,
	@Field val amountMinor: Double,
	@Field val currency: String,
	@Field val merchant: String,
	@Field val occurredAtLocal: String,
	@Field val timeZone: String,
	@Field val monthKey: String,
	@Field val cardLast4: String,
) : Record

@OptimizedRecord
data class InboxCompletionRecord(
	@Field val outcome: String,
	@Field val inboxIds: List<String>,
	@Field val parserVersion: Double,
	@Field val sourceEventKey: String? = null,
	@Field val errorCode: String? = null,
	@Field val expense: CardExpenseRecord? = null,
) : Record

@OptimizedRecord
data class NotificationAccessStatusRecord(
	@Field val granted: Boolean,
) : Record

@OptimizedRecord
data class InboxEnvelopeRecord(
	@Field val inboxId: String,
	@Field val notificationKey: String,
	@Field val packageName: String,
	@Field val postedAtMs: Double,
	@Field val capturedAtMs: Double,
	@Field val title: String?,
	@Field val text: String?,
	@Field val bigText: String?,
	@Field val textLines: List<String>,
) : Record

@OptimizedRecord
data class EligibleInboxBatchRecord(
	@Field val items: List<InboxEnvelopeRecord>,
	@Field val hasMore: Boolean,
) : Record

@OptimizedRecord
data class CompletionResultRecord(
	@Field val outcome: String,
	@Field val transactionId: String?,
	@Field val inserted: Boolean,
) : Record

@OptimizedRecord
data class DrainRequestResultRecord(
	@Field val disposition: String,
) : Record

@OptimizedRecord
data class MonthTransactionRecord(
	@Field val transactionId: String,
	@Field val source: String,
	@Field val kind: String,
	@Field val sourceNotificationKey: String,
	@Field val sourceEventKey: String,
	@Field val semanticCandidateKey: String,
	@Field val amountMinor: Double,
	@Field val currency: String,
	@Field val merchant: String,
	@Field val occurredAtLocal: String,
	@Field val timeZone: String,
	@Field val monthKey: String,
	@Field val cardLast4: String,
) : Record

@OptimizedRecord
data class MonthTransactionsRecord(
	@Field val monthKey: String,
	@Field val items: List<MonthTransactionRecord>,
) : Record

@OptimizedRecord
data class DiagnosticsRecord(
	@Field val pending: Double,
	@Field val unsupported: Double,
	@Field val unparsed: Double,
	@Field val possibleDuplicateGroups: Double,
	@Field val reusedNotificationKeys: Double,
	@Field val projectionError: Boolean,
) : Record
