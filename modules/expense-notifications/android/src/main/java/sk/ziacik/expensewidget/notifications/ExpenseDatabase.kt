package sk.ziacik.expensewidget.notifications

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal class ExpenseDatabase private constructor(context: Context) :
	SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

	init {
		setWriteAheadLoggingEnabled(true)
	}

	override fun onConfigure(database: SQLiteDatabase) {
		super.onConfigure(database)
		database.setForeignKeyConstraintsEnabled(true)
	}

	override fun onCreate(database: SQLiteDatabase) {
		createTransactionsTable(database)
		createInboxTable(database, INBOX_TABLE)
		createIndexes(database)
	}

	override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
		if (oldVersion != 1 || newVersion != DATABASE_VERSION) {
			throw IllegalStateException("Missing database migration from version $oldVersion to $newVersion.")
		}

		createTransactionsTable(database)
		database.execSQL("DROP TABLE IF EXISTS headless_probe_effects")
		createInboxTable(database, MIGRATION_INBOX_TABLE)
		database.execSQL(
			"""
			INSERT INTO $MIGRATION_INBOX_TABLE (
				id,
				notification_key,
				package_name,
				posted_at_ms,
				captured_at_ms,
				title,
				text,
				big_text,
				text_lines_json,
				source_event_key,
				transaction_id,
				status,
				last_parser_version,
				attempt_count,
				last_error_code,
				processed_at_ms
			)
			SELECT
				id,
				notification_key,
				package_name,
				posted_at_ms,
				captured_at_ms,
				title,
				text,
				big_text,
				text_lines_json,
				NULL,
				NULL,
				'pending',
				NULL,
				0,
				NULL,
				NULL
			FROM $INBOX_TABLE
			""".trimIndent(),
		)
		database.execSQL("DROP TABLE $INBOX_TABLE")
		database.execSQL("ALTER TABLE $MIGRATION_INBOX_TABLE RENAME TO $INBOX_TABLE")
		createIndexes(database)
	}

	override fun onDowngrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
		throw IllegalStateException("Database downgrade from version $oldVersion to $newVersion is not supported.")
	}

	private fun createTransactionsTable(database: SQLiteDatabase) {
		database.execSQL(
			"""
			CREATE TABLE IF NOT EXISTS transactions (
				id INTEGER PRIMARY KEY AUTOINCREMENT,
				source TEXT NOT NULL,
				kind TEXT NOT NULL,
				source_notification_key TEXT NOT NULL,
				source_event_key TEXT NOT NULL UNIQUE,
				semantic_candidate_key TEXT NOT NULL,
				amount_minor INTEGER NOT NULL
					CHECK(typeof(amount_minor) = 'integer' AND amount_minor BETWEEN 1 AND $MAX_SAFE_INTEGER),
				currency TEXT NOT NULL,
				merchant TEXT NOT NULL CHECK(length(merchant) > 0),
				occurred_at_local TEXT NOT NULL,
				time_zone TEXT NOT NULL,
				month_key TEXT NOT NULL,
				card_last4 TEXT NOT NULL,
				created_at_ms INTEGER NOT NULL
					CHECK(typeof(created_at_ms) = 'integer' AND created_at_ms BETWEEN 0 AND $MAX_SAFE_INTEGER)
			)
			""".trimIndent(),
		)
	}

	private fun createInboxTable(database: SQLiteDatabase, tableName: String) {
		database.execSQL(
			"""
			CREATE TABLE $tableName (
				id INTEGER PRIMARY KEY AUTOINCREMENT,
				notification_key TEXT NOT NULL,
				package_name TEXT NOT NULL,
				posted_at_ms INTEGER NOT NULL
					CHECK(typeof(posted_at_ms) = 'integer' AND posted_at_ms BETWEEN 0 AND $MAX_SAFE_INTEGER),
				captured_at_ms INTEGER NOT NULL
					CHECK(typeof(captured_at_ms) = 'integer' AND captured_at_ms BETWEEN 0 AND $MAX_SAFE_INTEGER),
				title TEXT,
				text TEXT,
				big_text TEXT,
				text_lines_json TEXT NOT NULL,
				source_event_key TEXT,
				transaction_id INTEGER REFERENCES transactions(id) ON DELETE RESTRICT,
				status TEXT NOT NULL DEFAULT 'pending'
					CHECK(status IN ('pending', 'processed', 'unsupported', 'unparsed')),
				last_parser_version INTEGER
					CHECK(
						last_parser_version IS NULL OR
						(typeof(last_parser_version) = 'integer' AND last_parser_version BETWEEN 1 AND $MAX_SAFE_INTEGER)
					),
				attempt_count INTEGER NOT NULL DEFAULT 0
					CHECK(typeof(attempt_count) = 'integer' AND attempt_count BETWEEN 0 AND $MAX_SAFE_INTEGER),
				last_error_code TEXT,
				processed_at_ms INTEGER
					CHECK(
						processed_at_ms IS NULL OR
						(typeof(processed_at_ms) = 'integer' AND processed_at_ms BETWEEN 0 AND $MAX_SAFE_INTEGER)
					),
				CHECK(
					(
						status = 'pending' AND source_event_key IS NULL AND transaction_id IS NULL AND
						last_parser_version IS NULL AND attempt_count = 0 AND last_error_code IS NULL AND
						processed_at_ms IS NULL
					) OR (
						status = 'processed' AND source_event_key IS NOT NULL AND transaction_id IS NOT NULL AND
						last_parser_version IS NOT NULL AND attempt_count > 0 AND last_error_code IS NULL AND
						processed_at_ms IS NOT NULL
					) OR (
						status = 'unsupported' AND source_event_key IS NOT NULL AND transaction_id IS NULL AND
						last_parser_version IS NOT NULL AND attempt_count > 0 AND
						last_error_code IN ('unsupported_package', 'unsupported_title') AND processed_at_ms IS NOT NULL
					) OR (
						status = 'unparsed' AND source_event_key IS NOT NULL AND transaction_id IS NULL AND
						last_parser_version IS NOT NULL AND attempt_count > 0 AND
						last_error_code IN (
							'missing_body',
							'invalid_body_shape',
							'invalid_amount',
							'unsupported_amount',
							'invalid_datetime',
							'invalid_card',
							'invalid_balance'
						) AND processed_at_ms IS NOT NULL
					)
				)
			)
			""".trimIndent(),
		)
	}

	private fun createIndexes(database: SQLiteDatabase) {
		database.execSQL(
			"CREATE INDEX inbox_eligibility ON $INBOX_TABLE(status, last_parser_version, id)",
		)
		database.execSQL("CREATE INDEX inbox_transaction ON $INBOX_TABLE(transaction_id)")
		database.execSQL(
			"CREATE INDEX inbox_notification_source ON $INBOX_TABLE(package_name, notification_key, source_event_key)",
		)
		database.execSQL(
			"CREATE INDEX transactions_month ON transactions(month_key, kind, currency, occurred_at_local DESC, id DESC)",
		)
		database.execSQL("CREATE INDEX transactions_semantic ON transactions(semantic_candidate_key)")
	}

	companion object {
		const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L

		private const val DATABASE_NAME = "expense_widget.db"
		private const val DATABASE_VERSION = 2
		private const val INBOX_TABLE = "notification_inbox"
		private const val MIGRATION_INBOX_TABLE = "notification_inbox_v2"

		@Volatile
		private var instance: ExpenseDatabase? = null

		fun getInstance(context: Context): ExpenseDatabase {
			return instance ?: synchronized(this) {
				instance ?: ExpenseDatabase(context).also { database -> instance = database }
			}
		}
	}
}
