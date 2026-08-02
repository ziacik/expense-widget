import { NativeModule, requireNativeModule } from "expo";

declare class ExpenseNotificationsModule extends NativeModule {}

export default requireNativeModule<ExpenseNotificationsModule>("ExpenseNotifications");
