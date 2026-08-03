package sk.ziacik.expensewidget.notifications

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

internal object NotificationAccess {
	fun isGranted(context: Context): Boolean {
		val applicationContext = context.applicationContext
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
			applicationContext
				.getSystemService(NotificationManager::class.java)
				.isNotificationListenerAccessGranted(listenerComponent(applicationContext))
		} else {
			NotificationManagerCompat
				.getEnabledListenerPackages(applicationContext)
				.contains(applicationContext.packageName)
		}
	}

	fun openSettings(context: Context): Boolean {
		val applicationContext = context.applicationContext
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			val detailIntent =
				Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
					.putExtra(
						Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
						listenerComponent(applicationContext).flattenToString(),
					)
			if (startResolvedActivity(applicationContext, detailIntent)) {
				return true
			}
		}

		return startResolvedActivity(
			applicationContext,
			Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
		)
	}

	private fun listenerComponent(context: Context): ComponentName {
		return ComponentName(context, ExpenseNotificationListenerService::class.java)
	}

	private fun startResolvedActivity(context: Context, intent: Intent): Boolean {
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		if (intent.resolveActivity(context.packageManager) == null) {
			return false
		}

		return try {
			context.startActivity(intent)
			true
		} catch (_: RuntimeException) {
			false
		}
	}
}
