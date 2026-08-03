package sk.ziacik.expensewidget.notifications

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray

internal data class InboxEnvelopeData(
	val inboxId: Long,
	val notificationKey: String,
	val packageName: String,
	val postedAtMs: Long,
	val capturedAtMs: Long,
	val title: String?,
	val text: String?,
	val bigText: String?,
	val textLines: List<String>,
)

internal data class EligibleInboxBatchData(
	val items: List<InboxEnvelopeData>,
	val hasMore: Boolean,
)

internal data class CardExpenseData(
	val source: String,
	val kind: String,
	val sourceNotificationKey: String,
	val sourceEventKey: String,
	val semanticCandidateKey: String,
	val amountMinor: Long,
	val currency: String,
	val merchant: String,
	val occurredAtLocal: String,
	val timeZone: String,
	val monthKey: String,
	val cardLast4: String,
)

internal sealed interface InboxCompletionData {
	val inboxIds: List<Long>
	val parserVersion: Long
	val sourceEventKey: String
}

internal data class ProcessedInboxCompletionData(
	override val inboxIds: List<Long>,
	override val parserVersion: Long,
	val expense: CardExpenseData,
) : InboxCompletionData {
	override val sourceEventKey = expense.sourceEventKey
}

internal data class FailedInboxCompletionData(
	override val inboxIds: List<Long>,
	override val parserVersion: Long,
	override val sourceEventKey: String,
	val outcome: String,
	val errorCode: String,
) : InboxCompletionData

internal data class CompletionResultData(
	val outcome: String,
	val transactionId: Long?,
	val inserted: Boolean,
	val changedMonthKeys: Set<String>,
)

internal data class MonthTransactionData(
	val transactionId: Long,
	val expense: CardExpenseData,
)

internal data class DiagnosticsData(
	val pending: Long,
	val unsupported: Long,
	val unparsed: Long,
	val possibleDuplicateGroups: Long,
	val reusedNotificationKeys: Long,
	val projectionError: Boolean,
)

internal data class WidgetProjectionData(
	val totalMinor: Long,
	val transactionCount: Long,
)

internal class ExpenseRepository private constructor(context: Context) {
	private val databaseHelper = ExpenseDatabase.getInstance(context)

	fun insertInbox(envelope: RawNotificationEnvelope): Long {
		val values = ContentValues().apply {
			put("notification_key", envelope.notificationKey)
			put("package_name", envelope.packageName)
			put("posted_at_ms", envelope.postedAtMs)
			put("captured_at_ms", envelope.capturedAtMs)
			put("title", envelope.title)
			put("text", envelope.text)
			put("big_text", envelope.bigText)
			put("text_lines_json", envelope.textLinesJson)
		}
		return databaseHelper.writableDatabase.insertOrThrow(INBOX_TABLE, null, values)
	}

	fun getEligibleInboxBatch(parserVersion: Long, limit: Int): EligibleInboxBatchData {
		val rows =
			databaseHelper.readableDatabase
				.rawQuery(
					"""
					SELECT
						id,
						notification_key,
						package_name,
						posted_at_ms,
						captured_at_ms,
						title,
						text,
						big_text,
						text_lines_json
					FROM $INBOX_TABLE
					WHERE
						status = ? OR
						(status IN (?, ?) AND (last_parser_version IS NULL OR last_parser_version < ?))
					ORDER BY id ASC
					LIMIT ?
					""".trimIndent(),
					arrayOf(
						STATUS_PENDING,
						STATUS_UNSUPPORTED,
						STATUS_UNPARSED,
						parserVersion.toString(),
						(limit + 1).toString(),
					),
				)
				.use { cursor ->
					buildList {
						while (cursor.moveToNext()) {
							add(
								InboxEnvelopeData(
									inboxId = cursor.getLong(0),
									notificationKey = cursor.getString(1),
									packageName = cursor.getString(2),
									postedAtMs = cursor.getLong(3),
									capturedAtMs = cursor.getLong(4),
									title = cursor.getNullableString(5),
									text = cursor.getNullableString(6),
									bigText = cursor.getNullableString(7),
									textLines = parseTextLines(cursor.getString(8)),
								),
							)
						}
					}
				}

		return EligibleInboxBatchData(
			items = rows.take(limit),
			hasMore = rows.size > limit,
		)
	}

	fun completeInboxItems(completion: InboxCompletionData): CompletionResultData {
		val database = databaseHelper.writableDatabase
		database.beginTransaction()
		return try {
			val rows = loadInboxRows(database, completion.inboxIds)
			check(rows.size == completion.inboxIds.size) { "Inbox completion contains an unknown ID." }
			check(rows.map { row -> row.identity }.distinct().size == 1) {
				"Inbox completion IDs do not represent one source event."
			}

			val result =
				when (completion) {
					is ProcessedInboxCompletionData -> completeProcessed(database, rows, completion)
					is FailedInboxCompletionData -> completeFailed(database, rows, completion)
				}

			database.setTransactionSuccessful()
			result
		} finally {
			database.endTransaction()
		}
	}

	fun getMonthTransactions(monthKey: String): List<MonthTransactionData> {
		return databaseHelper.readableDatabase
			.rawQuery(
				"""
				SELECT
					id,
					source,
					kind,
					source_notification_key,
					source_event_key,
					semantic_candidate_key,
					amount_minor,
					currency,
					merchant,
					occurred_at_local,
					time_zone,
					month_key,
					card_last4
				FROM transactions
				WHERE kind = ? AND month_key = ? AND currency = ?
				ORDER BY occurred_at_local DESC, id DESC
				""".trimIndent(),
				arrayOf(CARD_EXPENSE_KIND, monthKey, EURO_CURRENCY),
			)
			.use { cursor ->
				buildList {
					while (cursor.moveToNext()) {
						add(
							MonthTransactionData(
								transactionId = cursor.getLong(0),
								expense =
									CardExpenseData(
										source = cursor.getString(1),
										kind = cursor.getString(2),
										sourceNotificationKey = cursor.getString(3),
										sourceEventKey = cursor.getString(4),
										semanticCandidateKey = cursor.getString(5),
										amountMinor = cursor.getLong(6),
										currency = cursor.getString(7),
										merchant = cursor.getString(8),
										occurredAtLocal = cursor.getString(9),
										timeZone = cursor.getString(10),
										monthKey = cursor.getString(11),
										cardLast4 = cursor.getString(12),
									),
							),
						)
					}
				}
			}
	}

	fun getWidgetProjection(monthKey: String): WidgetProjectionData {
		require(MONTH_KEY_PATTERN.matches(monthKey)) { "Month key must use YYYY-MM." }

		return databaseHelper.readableDatabase
			.rawQuery(
				"""
				SELECT COALESCE(SUM(amount_minor), 0), COUNT(*)
				FROM transactions
				WHERE kind = ? AND month_key = ? AND currency = ?
				""".trimIndent(),
				arrayOf(CARD_EXPENSE_KIND, monthKey, EURO_CURRENCY),
			)
			.use { cursor ->
				check(cursor.moveToFirst()) { "Could not read the widget projection." }
				val totalMinor = cursor.getLong(0)
				val transactionCount = cursor.getLong(1)
				check(totalMinor in 0..ExpenseDatabase.MAX_SAFE_INTEGER) {
					"Widget total is outside the safe-integer range."
				}
				check(transactionCount in 0..ExpenseDatabase.MAX_SAFE_INTEGER) {
					"Widget transaction count is outside the safe-integer range."
				}
				WidgetProjectionData(totalMinor, transactionCount)
			}
	}

	fun getDiagnostics(): DiagnosticsData {
		val database = databaseHelper.readableDatabase
		val statusCounts =
			database
				.rawQuery(
					"""
					SELECT
						SUM(CASE WHEN status = 'pending' THEN 1 ELSE 0 END),
						SUM(CASE WHEN status = 'unsupported' THEN 1 ELSE 0 END),
						SUM(CASE WHEN status = 'unparsed' THEN 1 ELSE 0 END)
					FROM $INBOX_TABLE
					""".trimIndent(),
					null,
				)
				.use { cursor ->
					check(cursor.moveToFirst()) { "Could not read inbox diagnostics." }
					Triple(cursor.getLong(0), cursor.getLong(1), cursor.getLong(2))
				}

		return DiagnosticsData(
			pending = statusCounts.first,
			unsupported = statusCounts.second,
			unparsed = statusCounts.third,
			possibleDuplicateGroups =
				querySingleLong(
					database,
					"""
					SELECT COUNT(*)
					FROM (
						SELECT 1
						FROM transactions
						GROUP BY semantic_candidate_key
						HAVING COUNT(*) > 1
					)
					""".trimIndent(),
				),
			reusedNotificationKeys =
				querySingleLong(
					database,
					"""
					SELECT COUNT(*)
					FROM (
						SELECT 1
						FROM $INBOX_TABLE
						WHERE source_event_key IS NOT NULL
						GROUP BY package_name, notification_key
						HAVING COUNT(DISTINCT source_event_key) > 1
					)
					""".trimIndent(),
				),
			projectionError = hasProjectionOverflow(database),
		)
	}

	private fun completeProcessed(
		database: SQLiteDatabase,
		rows: List<InboxRow>,
		completion: ProcessedInboxCompletionData,
	): CompletionResultData {
		val expense = completion.expense
		check(rows.first().identity.notificationKey == expense.sourceNotificationKey) {
			"The expense notification key does not match its raw source event."
		}
		check(
			rows.all { row ->
				isEligible(row, completion.parserVersion) ||
					(
						row.status == STATUS_PROCESSED &&
							row.sourceEventKey == expense.sourceEventKey &&
							row.lastParserVersion == completion.parserVersion &&
							row.transactionId != null
					)
			},
		) { "Inbox row is not eligible for this processed completion." }

		val transactionValues = ContentValues().apply {
			put("source", expense.source)
			put("kind", expense.kind)
			put("source_notification_key", expense.sourceNotificationKey)
			put("source_event_key", expense.sourceEventKey)
			put("semantic_candidate_key", expense.semanticCandidateKey)
			put("amount_minor", expense.amountMinor)
			put("currency", expense.currency)
			put("merchant", expense.merchant)
			put("occurred_at_local", expense.occurredAtLocal)
			put("time_zone", expense.timeZone)
			put("month_key", expense.monthKey)
			put("card_last4", expense.cardLast4)
			put("created_at_ms", System.currentTimeMillis())
		}
		val insertedId =
			database.insertWithOnConflict(
				"transactions",
				null,
				transactionValues,
				SQLiteDatabase.CONFLICT_IGNORE,
			)
		val inserted = insertedId != -1L
		val storedTransaction = loadTransaction(database, expense.sourceEventKey)
		check(storedTransaction.expense == expense) {
			"The existing source event transaction does not match the submitted expense."
		}
		check(
			rows
				.filterNot { row -> isEligible(row, completion.parserVersion) }
				.all { row -> row.transactionId == storedTransaction.transactionId },
		) { "An idempotent inbox row links to a different transaction." }

		val processedAtMs = System.currentTimeMillis()
		rows.filter { row -> isEligible(row, completion.parserVersion) }.forEach { row ->
			updateProcessedRow(
				database,
				row.id,
				expense.sourceEventKey,
				storedTransaction.transactionId,
				completion.parserVersion,
				processedAtMs,
			)
		}

		return CompletionResultData(
			outcome = STATUS_PROCESSED,
			transactionId = storedTransaction.transactionId,
			inserted = inserted,
			changedMonthKeys = setOf(expense.monthKey),
		)
	}

	private fun completeFailed(
		database: SQLiteDatabase,
		rows: List<InboxRow>,
		completion: FailedInboxCompletionData,
	): CompletionResultData {
		check(
			rows.all { row ->
				isEligible(row, completion.parserVersion) ||
					(
						row.status == completion.outcome &&
							row.sourceEventKey == completion.sourceEventKey &&
							row.lastParserVersion == completion.parserVersion &&
							row.lastErrorCode == completion.errorCode &&
							row.transactionId == null
					)
			},
		) { "Inbox row is not eligible for this parser outcome." }

		val processedAtMs = System.currentTimeMillis()
		rows.filter { row -> isEligible(row, completion.parserVersion) }.forEach { row ->
			updateFailedRow(
				database,
				row.id,
				completion.sourceEventKey,
				completion.outcome,
				completion.parserVersion,
				completion.errorCode,
				processedAtMs,
			)
		}

		return CompletionResultData(
			outcome = completion.outcome,
			transactionId = null,
			inserted = false,
			changedMonthKeys = emptySet(),
		)
	}

	private fun loadInboxRows(database: SQLiteDatabase, inboxIds: List<Long>): List<InboxRow> {
		val placeholders = inboxIds.joinToString(",") { "?" }
		val rowsById =
			database
				.rawQuery(
					"""
					SELECT
						id,
						notification_key,
						package_name,
						posted_at_ms,
						title,
						text,
						big_text,
						text_lines_json,
						status,
						source_event_key,
						transaction_id,
						last_parser_version,
						last_error_code
					FROM $INBOX_TABLE
					WHERE id IN ($placeholders)
					""".trimIndent(),
					inboxIds.map(Long::toString).toTypedArray(),
				)
				.use { cursor ->
					buildMap {
						while (cursor.moveToNext()) {
							val row =
								InboxRow(
									id = cursor.getLong(0),
									identity =
										RawSourceIdentity(
											notificationKey = cursor.getString(1),
											packageName = cursor.getString(2),
											postedAtMs = cursor.getLong(3),
											title = cursor.getNullableString(4),
											text = cursor.getNullableString(5),
											bigText = cursor.getNullableString(6),
											textLinesJson = cursor.getString(7),
										),
									status = cursor.getString(8),
									sourceEventKey = cursor.getNullableString(9),
									transactionId = cursor.getNullableLong(10),
									lastParserVersion = cursor.getNullableLong(11),
									lastErrorCode = cursor.getNullableString(12),
								)
							put(row.id, row)
						}
					}
				}

		return inboxIds.mapNotNull(rowsById::get)
	}

	private fun loadTransaction(database: SQLiteDatabase, sourceEventKey: String): MonthTransactionData {
		return database
			.rawQuery(
				"""
				SELECT
					id,
					source,
					kind,
					source_notification_key,
					source_event_key,
					semantic_candidate_key,
					amount_minor,
					currency,
					merchant,
					occurred_at_local,
					time_zone,
					month_key,
					card_last4
				FROM transactions
				WHERE source_event_key = ?
				""".trimIndent(),
				arrayOf(sourceEventKey),
			)
			.use { cursor ->
				check(cursor.moveToFirst()) { "Could not read the materialized transaction." }
				MonthTransactionData(
					transactionId = cursor.getLong(0),
					expense =
						CardExpenseData(
							source = cursor.getString(1),
							kind = cursor.getString(2),
							sourceNotificationKey = cursor.getString(3),
							sourceEventKey = cursor.getString(4),
							semanticCandidateKey = cursor.getString(5),
							amountMinor = cursor.getLong(6),
							currency = cursor.getString(7),
							merchant = cursor.getString(8),
							occurredAtLocal = cursor.getString(9),
							timeZone = cursor.getString(10),
							monthKey = cursor.getString(11),
							cardLast4 = cursor.getString(12),
						),
				)
			}
	}

	private fun updateProcessedRow(
		database: SQLiteDatabase,
		inboxId: Long,
		sourceEventKey: String,
		transactionId: Long,
		parserVersion: Long,
		processedAtMs: Long,
	) {
		database
			.compileStatement(
				"""
				UPDATE $INBOX_TABLE
				SET
					source_event_key = ?,
					transaction_id = ?,
					status = 'processed',
					last_parser_version = ?,
					attempt_count = attempt_count + 1,
					last_error_code = NULL,
					processed_at_ms = ?
				WHERE id = ?
				""".trimIndent(),
			)
			.use { statement ->
				statement.bindString(1, sourceEventKey)
				statement.bindLong(2, transactionId)
				statement.bindLong(3, parserVersion)
				statement.bindLong(4, processedAtMs)
				statement.bindLong(5, inboxId)
				check(statement.executeUpdateDelete() == 1) { "Could not complete an inbox row." }
			}
	}

	private fun updateFailedRow(
		database: SQLiteDatabase,
		inboxId: Long,
		sourceEventKey: String,
		outcome: String,
		parserVersion: Long,
		errorCode: String,
		processedAtMs: Long,
	) {
		database
			.compileStatement(
				"""
				UPDATE $INBOX_TABLE
				SET
					source_event_key = ?,
					transaction_id = NULL,
					status = ?,
					last_parser_version = ?,
					attempt_count = attempt_count + 1,
					last_error_code = ?,
					processed_at_ms = ?
				WHERE id = ?
				""".trimIndent(),
			)
			.use { statement ->
				statement.bindString(1, sourceEventKey)
				statement.bindString(2, outcome)
				statement.bindLong(3, parserVersion)
				statement.bindString(4, errorCode)
				statement.bindLong(5, processedAtMs)
				statement.bindLong(6, inboxId)
				check(statement.executeUpdateDelete() == 1) { "Could not persist an inbox parser outcome." }
			}
	}

	private fun isEligible(row: InboxRow, parserVersion: Long): Boolean {
		return row.status == STATUS_PENDING ||
			(
				row.status in setOf(STATUS_UNSUPPORTED, STATUS_UNPARSED) &&
					(row.lastParserVersion ?: 0L) < parserVersion
			)
	}

	private fun hasProjectionOverflow(database: SQLiteDatabase): Boolean {
		return database
			.rawQuery(
				"""
				SELECT month_key, amount_minor
				FROM transactions
				WHERE kind = ? AND currency = ?
				ORDER BY month_key ASC
				""".trimIndent(),
				arrayOf(CARD_EXPENSE_KIND, EURO_CURRENCY),
			)
			.use { cursor ->
				var previousMonth: String? = null
				var monthTotal = 0L
				while (cursor.moveToNext()) {
					val month = cursor.getString(0)
					val amountMinor = cursor.getLong(1)
					if (month != previousMonth) {
						previousMonth = month
						monthTotal = 0L
					}
					if (amountMinor < 0 || monthTotal > ExpenseDatabase.MAX_SAFE_INTEGER - amountMinor) {
						return@use true
					}
					monthTotal += amountMinor
				}
				false
			}
	}

	private fun querySingleLong(database: SQLiteDatabase, query: String): Long {
		return database.rawQuery(query, null).use { cursor ->
			check(cursor.moveToFirst()) { "Could not read a diagnostic count." }
			cursor.getLong(0)
		}
	}

	private fun parseTextLines(serializedLines: String): List<String> {
		val lines = JSONArray(serializedLines)
		return List(lines.length()) { index -> lines.getString(index) }
	}

	private data class RawSourceIdentity(
		val notificationKey: String,
		val packageName: String,
		val postedAtMs: Long,
		val title: String?,
		val text: String?,
		val bigText: String?,
		val textLinesJson: String,
	)

	private data class InboxRow(
		val id: Long,
		val identity: RawSourceIdentity,
		val status: String,
		val sourceEventKey: String?,
		val transactionId: Long?,
		val lastParserVersion: Long?,
		val lastErrorCode: String?,
	)

	companion object {
		private val MONTH_KEY_PATTERN = Regex("""\d{4}-(0[1-9]|1[0-2])""")
		private const val INBOX_TABLE = "notification_inbox"
		private const val STATUS_PENDING = "pending"
		private const val STATUS_PROCESSED = "processed"
		private const val STATUS_UNSUPPORTED = "unsupported"
		private const val STATUS_UNPARSED = "unparsed"
		private const val CARD_EXPENSE_KIND = "card-expense"
		private const val EURO_CURRENCY = "EUR"

		@Volatile
		private var instance: ExpenseRepository? = null

		fun getInstance(context: Context): ExpenseRepository {
			return instance ?: synchronized(this) {
				instance ?: ExpenseRepository(context.applicationContext).also { repository -> instance = repository }
			}
		}
	}
}

private fun android.database.Cursor.getNullableString(columnIndex: Int): String? {
	return if (isNull(columnIndex)) null else getString(columnIndex)
}

private fun android.database.Cursor.getNullableLong(columnIndex: Int): Long? {
	return if (isNull(columnIndex)) null else getLong(columnIndex)
}
