import { NativeModule, registerWebModule } from "expo";

class ExpenseNotificationsModule extends NativeModule {}

export default registerWebModule(ExpenseNotificationsModule, "ExpenseNotifications");
