package sk.ziacik.expensewidget.notifications

import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.exception.Exceptions
import expo.modules.kotlin.functions.Coroutine
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class ExpenseNotificationsModule : Module() {
	private val context
		get() = appContext.reactContext ?: throw Exceptions.ReactContextLost()

	private val repository
		get() = ExpenseRepository.getInstance(context)

	override fun definition() = ModuleDefinition {
		Name("ExpenseNotifications")
		Events("onExpenseDataChanged", "onNotificationAccessChanged")
		OnCreate {
			ExpenseModuleEventDispatcher.attach(this@ExpenseNotificationsModule)
		}
		OnDestroy {
			ExpenseModuleEventDispatcher.detach(this@ExpenseNotificationsModule)
		}

		AsyncFunction("getNotificationAccessStatusAsync") {
			NotificationAccessStatusRecord(NotificationAccess.isGranted(context))
		}

		AsyncFunction("openNotificationAccessSettingsAsync") {
			if (NotificationAccess.openSettings(context)) {
				mapOf("opened" to true)
			} else {
				mapOf("opened" to false, "reason" to "settings_unavailable")
			}
		}

		AsyncFunction("getEligibleInboxBatchAsync") { parserVersion: Double, limit: Double ->
			databaseCall {
				val validatedParserVersion = positiveSafeInteger(parserVersion, "Parser version")
				val validatedLimit = positiveSafeInteger(limit, "Batch limit")
				require(validatedLimit <= MAX_BATCH_SIZE) { "Batch limit must not exceed $MAX_BATCH_SIZE." }

				val batch = repository.getEligibleInboxBatch(validatedParserVersion, validatedLimit.toInt())
				EligibleInboxBatchRecord(
					items = batch.items.map(::toInboxEnvelopeRecord),
					hasMore = batch.hasMore,
				)
			}
		}

		AsyncFunction("completeInboxItemsAsync") Coroutine { completion: InboxCompletionRecord ->
			val result = databaseCall {
				val validatedCompletion = validateCompletion(completion)
				repository.completeInboxItems(validatedCompletion)
			}
			ExpenseWidgetRenderer.awaitUpdate(context)
			ExpenseModuleEventDispatcher.emitExpenseDataChanged(result.changedMonthKeys)
			CompletionResultRecord(
				outcome = result.outcome,
				transactionId = result.transactionId?.toString(),
				inserted = result.inserted,
			)
		}

		AsyncFunction("requestInboxDrainAsync") {
			ExpenseWidgetRenderer.requestUpdate(context)
			DrainRequestResultRecord(
				disposition =
					if (ExpenseInboxDrainStarter.start(context)) {
						"started"
					} else {
						"pending_after_start_failure"
					},
			)
		}

		AsyncFunction("getMonthTransactionsAsync") { monthKey: String ->
			databaseCall {
				require(MONTH_KEY_PATTERN.matches(monthKey)) { "Month key must use YYYY-MM." }
				MonthTransactionsRecord(
					monthKey = monthKey,
					items = repository.getMonthTransactions(monthKey).map(::toMonthTransactionRecord),
				)
			}
		}

		AsyncFunction("getDiagnosticsAsync") {
			databaseCall {
				val diagnostics = repository.getDiagnostics()
				DiagnosticsRecord(
					pending = toSafeDouble(diagnostics.pending, "Pending count"),
					unsupported = toSafeDouble(diagnostics.unsupported, "Unsupported count"),
					unparsed = toSafeDouble(diagnostics.unparsed, "Unparsed count"),
					possibleDuplicateGroups =
						toSafeDouble(diagnostics.possibleDuplicateGroups, "Possible duplicate group count"),
					reusedNotificationKeys =
						toSafeDouble(diagnostics.reusedNotificationKeys, "Reused notification key count"),
					projectionError = diagnostics.projectionError,
				)
			}
		}
	}

	internal fun emitExpenseDataChanged(monthKeys: Collection<String>) {
		emitOnReactQueue(
			"onExpenseDataChanged",
			mapOf("monthKeys" to monthKeys.distinct().sorted()),
		)
	}

	internal fun emitNotificationAccessChanged(granted: Boolean) {
		emitOnReactQueue(
			"onNotificationAccessChanged",
			mapOf("granted" to granted),
		)
	}

	private fun emitOnReactQueue(eventName: String, body: Map<String, Any?>) {
		appContext.runtime.schedule {
			sendEvent(eventName, body)
		}
	}

	private fun validateCompletion(completion: InboxCompletionRecord): InboxCompletionData {
		require(completion.inboxIds.isNotEmpty()) { "Inbox completion must contain at least one ID." }
		require(completion.inboxIds.size <= MAX_BATCH_SIZE) {
			"Inbox completion must not contain more than $MAX_BATCH_SIZE IDs."
		}
		val inboxIds = completion.inboxIds.map(::parseInboxId)
		require(inboxIds.distinct().size == inboxIds.size) { "Inbox completion contains duplicate IDs." }
		val parserVersion = positiveSafeInteger(completion.parserVersion, "Parser version")

		return when (completion.outcome) {
			"processed" -> {
				require(completion.sourceEventKey == null && completion.errorCode == null) {
					"Processed completion must not contain failure fields."
				}
				val expense = requireNotNull(completion.expense) {
					"Processed completion must contain an expense."
				}
				ProcessedInboxCompletionData(
					inboxIds = inboxIds,
					parserVersion = parserVersion,
					expense = validateExpense(expense),
				)
			}

			"unsupported", "unparsed" -> {
				require(completion.expense == null) { "Failed completion must not contain an expense." }
				val sourceEventKey = requireNotNull(completion.sourceEventKey) {
					"Failed completion must contain a source-event key."
				}
				require(sourceEventKey.isNotEmpty()) { "Source-event key must not be empty." }
				val errorCode = requireNotNull(completion.errorCode) {
					"Failed completion must contain an error code."
				}
				val allowedCodes =
					if (completion.outcome == "unsupported") {
						UNSUPPORTED_ERROR_CODES
					} else {
						UNPARSED_ERROR_CODES
					}
				require(errorCode in allowedCodes) {
					"Error code does not match the completion outcome."
				}

				FailedInboxCompletionData(
					inboxIds = inboxIds,
					parserVersion = parserVersion,
					sourceEventKey = sourceEventKey,
					outcome = completion.outcome,
					errorCode = errorCode,
				)
			}

			else -> throw IllegalArgumentException("Unknown inbox completion outcome.")
		}
	}

	private fun validateExpense(expense: CardExpenseRecord): CardExpenseData {
		require(expense.source == "csob-sk-smartbanking") { "Expense source is not supported." }
		require(expense.kind == "card-expense") { "Expense kind is not supported." }
		require(expense.currency == "EUR") { "Expense currency is not supported." }
		require(expense.timeZone == "Europe/Bratislava") { "Expense time zone is not supported." }
		require(expense.sourceNotificationKey.isNotEmpty()) { "Notification key must not be empty." }
		require(expense.sourceEventKey.isNotEmpty()) { "Source-event key must not be empty." }
		require(expense.semanticCandidateKey.isNotEmpty()) { "Semantic candidate key must not be empty." }
		require(expense.merchant.isNotEmpty()) { "Merchant must not be empty." }
		require(LOCAL_DATE_TIME_PATTERN.matches(expense.occurredAtLocal)) {
			"Expense local date-time has an invalid format."
		}
		require(MONTH_KEY_PATTERN.matches(expense.monthKey)) { "Expense month key has an invalid format." }
		require(expense.occurredAtLocal.startsWith(expense.monthKey)) {
			"Expense month key does not match its local date-time."
		}
		require(CARD_LAST4_PATTERN.matches(expense.cardLast4)) { "Card suffix must contain four digits." }

		return CardExpenseData(
			source = expense.source,
			kind = expense.kind,
			sourceNotificationKey = expense.sourceNotificationKey,
			sourceEventKey = expense.sourceEventKey,
			semanticCandidateKey = expense.semanticCandidateKey,
			amountMinor = positiveSafeInteger(expense.amountMinor, "Expense amount"),
			currency = expense.currency,
			merchant = expense.merchant,
			occurredAtLocal = expense.occurredAtLocal,
			timeZone = expense.timeZone,
			monthKey = expense.monthKey,
			cardLast4 = expense.cardLast4,
		)
	}

	private fun parseInboxId(rawId: String): Long {
		require(INBOX_ID_PATTERN.matches(rawId)) { "Inbox ID must be a positive base-10 integer." }
		return rawId.toLongOrNull() ?: throw IllegalArgumentException("Inbox ID is outside the SQLite integer range.")
	}

	private fun positiveSafeInteger(value: Double, label: String): Long {
		require(value.isFinite() && value % 1.0 == 0.0) { "$label must be an integer." }
		require(value >= 1.0 && value <= ExpenseDatabase.MAX_SAFE_INTEGER.toDouble()) {
			"$label must be a positive safe integer."
		}
		return value.toLong()
	}

	private fun toInboxEnvelopeRecord(envelope: InboxEnvelopeData): InboxEnvelopeRecord {
		return InboxEnvelopeRecord(
			inboxId = envelope.inboxId.toString(),
			notificationKey = envelope.notificationKey,
			packageName = envelope.packageName,
			postedAtMs = toSafeDouble(envelope.postedAtMs, "Notification post time"),
			capturedAtMs = toSafeDouble(envelope.capturedAtMs, "Notification capture time"),
			title = envelope.title,
			text = envelope.text,
			bigText = envelope.bigText,
			textLines = envelope.textLines,
		)
	}

	private fun toMonthTransactionRecord(transaction: MonthTransactionData): MonthTransactionRecord {
		val expense = transaction.expense
		return MonthTransactionRecord(
			transactionId = transaction.transactionId.toString(),
			source = expense.source,
			kind = expense.kind,
			sourceNotificationKey = expense.sourceNotificationKey,
			sourceEventKey = expense.sourceEventKey,
			semanticCandidateKey = expense.semanticCandidateKey,
			amountMinor = toSafeDouble(expense.amountMinor, "Expense amount"),
			currency = expense.currency,
			merchant = expense.merchant,
			occurredAtLocal = expense.occurredAtLocal,
			timeZone = expense.timeZone,
			monthKey = expense.monthKey,
			cardLast4 = expense.cardLast4,
		)
	}

	private fun toSafeDouble(value: Long, label: String): Double {
		if (value < 0 || value > ExpenseDatabase.MAX_SAFE_INTEGER) {
			throw ProjectionOverflowException("$label is outside the JavaScript safe-integer range.")
		}
		return value.toDouble()
	}

	private inline fun <T> databaseCall(block: () -> T): T {
		return try {
			block()
		} catch (error: ProjectionOverflowException) {
			throw CodedException("ERR_PROJECTION_OVERFLOW", error.message, error)
		} catch (error: IllegalArgumentException) {
			throw CodedException("ERR_INVALID_ARGUMENT", error.message, error)
		} catch (error: Exception) {
			throw CodedException("ERR_DATABASE", "Could not access the expense database.", error)
		}
	}

	private class ProjectionOverflowException(message: String) : RuntimeException(message)

	companion object {
		private const val MAX_BATCH_SIZE = 50L

		private val MONTH_KEY_PATTERN = Regex("^[0-9]{4}-(0[1-9]|1[0-2])$")
		private val LOCAL_DATE_TIME_PATTERN =
			Regex("^[0-9]{4}-(0[1-9]|1[0-2])-[0-9]{2}T([01][0-9]|2[0-3]):[0-5][0-9]:00$")
		private val CARD_LAST4_PATTERN = Regex("^[0-9]{4}$")
		private val INBOX_ID_PATTERN = Regex("^[1-9][0-9]*$")
		private val UNSUPPORTED_ERROR_CODES =
			setOf(
				"unsupported_package",
				"unsupported_title",
			)
		private val UNPARSED_ERROR_CODES =
			setOf(
				"missing_body",
				"invalid_body_shape",
				"invalid_amount",
				"unsupported_amount",
				"invalid_datetime",
				"invalid_card",
				"invalid_balance",
			)
	}
}
