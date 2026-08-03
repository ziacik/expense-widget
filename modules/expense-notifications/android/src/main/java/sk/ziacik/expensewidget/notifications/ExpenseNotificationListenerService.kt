package sk.ziacik.expensewidget.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.concurrent.Executors

class ExpenseNotificationListenerService : NotificationListenerService() {
	private val repository by lazy { ExpenseRepository.getInstance(applicationContext) }
	private val persistenceExecutor = Executors.newSingleThreadExecutor()

	override fun onNotificationPosted(statusBarNotification: StatusBarNotification?) {
		if (statusBarNotification?.packageName != CSOB_PACKAGE_NAME) {
			return
		}

		val envelope = RawNotificationEnvelope.from(statusBarNotification)
		persistenceExecutor.execute {
			try {
				repository.insertInbox(envelope)
				ExpenseModuleEventDispatcher.emitExpenseDataChanged(emptyList())
				ExpenseInboxDrainStarter.start(applicationContext)
			} catch (error: Exception) {
				Log.e(TAG, "Could not persist a bank notification.", error)
			}
		}
	}

	override fun onListenerConnected() {
		super.onListenerConnected()
		persistenceExecutor.execute {
			val notifications =
				try {
					activeNotifications.orEmpty()
				} catch (error: RuntimeException) {
					Log.e(TAG, "Could not inspect active notifications after listener connection.", error)
					emptyArray()
				}

			notifications
				.filter { notification -> notification.packageName == CSOB_PACKAGE_NAME }
				.forEach { notification ->
					try {
						repository.insertInbox(RawNotificationEnvelope.from(notification))
						ExpenseModuleEventDispatcher.emitExpenseDataChanged(emptyList())
					} catch (error: Exception) {
						Log.e(TAG, "Could not recover an active bank notification.", error)
					}
				}

			refreshNotificationAccess()
			ExpenseInboxDrainStarter.start(applicationContext)
		}
	}

	override fun onListenerDisconnected() {
		super.onListenerDisconnected()
		persistenceExecutor.execute(::refreshNotificationAccess)
	}

	override fun onDestroy() {
		persistenceExecutor.shutdown()
		super.onDestroy()
	}

	private fun refreshNotificationAccess() {
		val granted = NotificationAccess.isGranted(applicationContext)
		ExpenseModuleEventDispatcher.emitNotificationAccessChanged(granted)
		ExpenseWidgetRenderer.requestUpdate(applicationContext)
	}

	companion object {
		private const val CSOB_PACKAGE_NAME = "com.zentity.sbank.csobsk"
		private const val TAG = "ExpenseNotification"
	}
}
