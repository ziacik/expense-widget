package sk.ziacik.expensewidget.notifications

import java.util.WeakHashMap

internal object ExpenseModuleEventDispatcher {
	private val modules = WeakHashMap<ExpenseNotificationsModule, Unit>()

	@Synchronized
	fun attach(module: ExpenseNotificationsModule) {
		modules[module] = Unit
	}

	@Synchronized
	fun detach(module: ExpenseNotificationsModule) {
		modules.remove(module)
	}

	fun emitExpenseDataChanged(monthKeys: Collection<String>) {
		moduleSnapshot().forEach { module -> module.emitExpenseDataChanged(monthKeys) }
	}

	fun emitNotificationAccessChanged(granted: Boolean) {
		moduleSnapshot().forEach { module -> module.emitNotificationAccessChanged(granted) }
	}

	@Synchronized
	private fun moduleSnapshot(): List<ExpenseNotificationsModule> = modules.keys.toList()
}
