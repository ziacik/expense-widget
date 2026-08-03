package sk.ziacik.expensewidget.notifications

import android.app.Notification
import android.service.notification.StatusBarNotification
import org.json.JSONArray

internal data class RawNotificationEnvelope(
	val notificationKey: String,
	val packageName: String,
	val postedAtMs: Long,
	val capturedAtMs: Long,
	val title: String?,
	val text: String?,
	val bigText: String?,
	val textLinesJson: String,
) {
	companion object {
		fun from(statusBarNotification: StatusBarNotification): RawNotificationEnvelope {
			val extras = statusBarNotification.notification.extras
			val textLines =
				extras
					.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
					?.map { value -> value.toString() }
					.orEmpty()
			val serializedTextLines = JSONArray().apply {
				textLines.forEach { value -> put(value) }
			}.toString()

			return RawNotificationEnvelope(
				notificationKey = statusBarNotification.key,
				packageName = statusBarNotification.packageName,
				postedAtMs = statusBarNotification.postTime,
				capturedAtMs = System.currentTimeMillis(),
				title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
				text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
				bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
				textLinesJson = serializedTextLines,
			)
		}
	}
}
