package sk.ziacik.expensewidget.notifications

import android.content.Intent
import android.util.Log
import com.facebook.react.HeadlessJsTaskService
import com.facebook.react.bridge.Arguments
import com.facebook.react.jstasks.HeadlessJsTaskConfig

class ExpenseHeadlessJsTaskService : HeadlessJsTaskService() {
	override fun getTaskConfig(intent: Intent?): HeadlessJsTaskConfig? {
		if (intent?.action != ExpenseInboxDrainStarter.ACTION_DRAIN_INBOX) {
			return null
		}

		return HeadlessJsTaskConfig(
			TASK_NAME,
			Arguments.createMap(),
			TASK_TIMEOUT_MS,
			true,
		)
	}

	override fun onHeadlessJsTaskFinish(taskId: Int) {
		Log.i(TAG, "Headless JS task finished.")
		super.onHeadlessJsTaskFinish(taskId)
	}

	companion object {
		const val TASK_NAME = "ExpenseInboxDrain"
		private const val TASK_TIMEOUT_MS = 60_000L
		private const val TAG = "ExpenseHeadlessTask"
	}
}
