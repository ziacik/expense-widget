package sk.ziacik.expensewidget.notifications

import android.content.Context
import android.content.Intent
import android.util.Log

internal object ExpenseInboxDrainStarter {
	const val ACTION_DRAIN_INBOX =
		"sk.ziacik.expensewidget.notifications.action.DRAIN_INBOX"

	fun start(context: Context): Boolean {
		val applicationContext = context.applicationContext
		val intent =
			Intent(applicationContext, ExpenseHeadlessJsTaskService::class.java).setAction(
				ACTION_DRAIN_INBOX,
			)

		return try {
			applicationContext.startService(intent) != null
		} catch (error: IllegalStateException) {
			Log.e(TAG, "Android rejected the Headless JS service start.", error)
			false
		} catch (error: SecurityException) {
			Log.e(TAG, "Android denied the Headless JS service start.", error)
			false
		}
	}

	private const val TAG = "ExpenseInboxDrain"
}
