import { StyleSheet, Text, View } from "react-native";

import type { MonthTransaction } from "../../modules/expense-notifications";
import { useTheme } from "@/hooks/use-theme";
import { formatExpenseMinor, formatTransactionTimestamp } from "@/utils/expense-format";

type TransactionRowProps = {
	transaction: MonthTransaction;
};

export function TransactionRow({ transaction }: TransactionRowProps) {
	const colors = useTheme();

	return (
		<View style={styles.container}>
			<View style={styles.description}>
				<Text numberOfLines={2} style={[styles.merchant, { color: colors.text }]}>
					{transaction.merchant}
				</Text>
				<Text numberOfLines={2} style={[styles.metadata, { color: colors.textSecondary }]}>
					{formatTransactionTimestamp(transaction.occurredAtLocal)} · Karta •••• {transaction.cardLast4}
				</Text>
			</View>
			<Text numberOfLines={1} style={[styles.amount, { color: colors.expense }]}>
				{formatExpenseMinor(transaction.amountMinor)}
			</Text>
		</View>
	);
}

const styles = StyleSheet.create({
	container: {
		minHeight: 78,
		flexDirection: "row",
		alignItems: "center",
		gap: 12,
		paddingVertical: 12,
	},
	description: {
		flex: 1,
		minWidth: 0,
		gap: 4,
	},
	merchant: {
		fontSize: 16,
		fontWeight: "700",
		lineHeight: 21,
	},
	metadata: {
		fontSize: 13,
		fontWeight: "500",
		lineHeight: 18,
	},
	amount: {
		maxWidth: "42%",
		fontSize: 16,
		fontWeight: "700",
		textAlign: "right",
	},
});
