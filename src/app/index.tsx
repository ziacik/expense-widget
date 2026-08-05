import { SymbolView } from "expo-symbols";
import { useState } from "react";
import { ActivityIndicator, FlatList, Pressable, RefreshControl, StyleSheet, Text, View } from "react-native";

import { BudgetEditorModal } from "@/components/budget-editor-modal";
import { MonthSelector } from "@/components/month-selector";
import { TransactionRow } from "@/components/transaction-row";
import { MaxContentWidth } from "@/constants/theme";
import type { MonthlyBudgetProgress } from "@/domain/budget/monthly-budget";
import { useExpenseOverview } from "@/hooks/use-expense-overview";
import { useTheme } from "@/hooks/use-theme";
import { formatEuroMinor } from "@/utils/expense-format";

export default function ExpenseOverviewScreen() {
	const colors = useTheme();
	const overview = useExpenseOverview();
	const [isBudgetEditorVisible, setIsBudgetEditorVisible] = useState(false);

	return (
		<>
			<FlatList
				contentContainerStyle={[styles.content, { backgroundColor: colors.background }]}
				data={overview.transactions}
				ItemSeparatorComponent={() => <View style={[styles.separator, { backgroundColor: colors.border }]} />}
				keyExtractor={(transaction) => transaction.transactionId}
				ListEmptyComponent={<OverviewEmptyState error={overview.error} isLoading={overview.isLoading} onRetry={() => void overview.refresh()} />}
				ListHeaderComponent={
					<OverviewHeader
						budgetProgress={overview.budgetProgress}
						canSelectNextMonth={overview.canSelectNextMonth}
						error={overview.error}
						isLoading={overview.isLoading}
						monthKey={overview.selectedMonthKey}
						monthlyBudgetMinor={overview.monthlyBudgetMinor}
						notificationAccessGranted={overview.notificationAccessGranted}
						onEditBudget={() => setIsBudgetEditorVisible(true)}
						onMonthChange={overview.setSelectedMonthKey}
						onOpenNotificationSettings={() => void overview.openNotificationSettings()}
						summary={overview.summary}
					/>
				}
				refreshControl={
					<RefreshControl
						colors={[colors.text]}
						onRefresh={() => void overview.refresh()}
						progressBackgroundColor={colors.backgroundElement}
						refreshing={overview.isRefreshing}
						tintColor={colors.text}
					/>
				}
				renderItem={({ item }) => <TransactionRow transaction={item} />}
			/>
			{isBudgetEditorVisible ? (
				<BudgetEditorModal
					amountMinor={overview.monthlyBudgetMinor}
					onClose={() => setIsBudgetEditorVisible(false)}
					onSave={overview.saveMonthlyBudget}
				/>
			) : null}
		</>
	);
}

type OverviewHeaderProps = {
	budgetProgress: MonthlyBudgetProgress | null;
	canSelectNextMonth: boolean;
	error: string | null;
	isLoading: boolean;
	monthKey: string;
	monthlyBudgetMinor: number | null;
	notificationAccessGranted: boolean | null;
	onEditBudget: () => void;
	onMonthChange: (monthKey: string) => void;
	onOpenNotificationSettings: () => void;
	summary: { value: { totalMinor: number; transactionCount: number }; error: null } | { value: null; error: string };
};

function OverviewHeader({
	budgetProgress,
	canSelectNextMonth,
	error,
	isLoading,
	monthKey,
	monthlyBudgetMinor,
	notificationAccessGranted,
	onEditBudget,
	onMonthChange,
	onOpenNotificationSettings,
	summary,
}: OverviewHeaderProps) {
	const colors = useTheme();

	return (
		<>
			<MonthSelector canSelectNext={canSelectNextMonth} monthKey={monthKey} onChange={onMonthChange} />
			<View
				style={[
					styles.summaryBand,
					{
						backgroundColor: colors.backgroundElement,
						borderColor: colors.border,
					},
				]}
			>
				<Text style={[styles.summaryLabel, { color: colors.textSecondary }]}>Spolu</Text>
				<View style={styles.summaryValueSlot}>
					{isLoading ? (
						<ActivityIndicator color={colors.text} size="small" />
					) : summary.error !== null ? (
						<Text style={[styles.summaryError, { color: colors.warning }]}>{summary.error}</Text>
					) : (
						<Text style={[styles.summaryValue, { color: colors.expense }]}>{formatEuroMinor(summary.value.totalMinor)}</Text>
					)}
				</View>
				<BudgetSummary amountMinor={monthlyBudgetMinor} onEdit={onEditBudget} progress={budgetProgress} />
			</View>

			<NotificationAccessBand granted={notificationAccessGranted} onOpenSettings={onOpenNotificationSettings} />

			{error && !isLoading ? (
				<View style={[styles.inlineError, { backgroundColor: colors.errorBackground }]}>
					<Text style={[styles.inlineErrorText, { color: colors.text }]}>{error}</Text>
				</View>
			) : null}

			<Text style={[styles.sectionTitle, { color: colors.text }]}>Transakcie</Text>
		</>
	);
}

function BudgetSummary({ amountMinor, onEdit, progress }: { amountMinor: number | null; onEdit: () => void; progress: MonthlyBudgetProgress | null }) {
	const colors = useTheme();
	if (amountMinor === null) {
		return (
			<Pressable accessibilityRole="button" onPress={onEdit} style={({ pressed }) => [styles.budgetSetup, pressed && styles.pressed]}>
				<SymbolView name={{ ios: "plus", android: "add" }} size={20} tintColor={colors.text} />
				<Text style={[styles.budgetSetupText, { color: colors.text }]}>Nastaviť mesačný rozpočet</Text>
			</Pressable>
		);
	}

	const progressColor = progress === null || progress.percentage < 75 ? colors.success : progress.percentage < 100 ? colors.warning : colors.expense;
	const detailText =
		progress === null
			? `Rozpočet ${formatEuroMinor(amountMinor)}`
			: progress.remainingMinor >= 0
				? `Zostáva ${formatEuroMinor(progress.remainingMinor)} z ${formatEuroMinor(amountMinor)}`
				: `Prekročené o ${formatEuroMinor(Math.abs(progress.remainingMinor))}`;

	return (
		<View style={styles.budgetSummary}>
			<View style={styles.budgetRow}>
				<Text numberOfLines={1} style={[styles.budgetDetail, { color: colors.textSecondary }]}>
					{detailText}
				</Text>
				{progress ? <Text style={[styles.budgetPercentage, { color: progressColor }]}>{progress.percentage} %</Text> : null}
				<Pressable
					accessibilityLabel="Upraviť mesačný rozpočet"
					accessibilityRole="button"
					onPress={onEdit}
					style={({ pressed }) => [styles.editBudgetButton, pressed && styles.pressed]}
				>
					<SymbolView name={{ ios: "pencil", android: "edit" }} size={18} tintColor={colors.text} />
				</Pressable>
			</View>
			<View style={[styles.progressTrack, { backgroundColor: colors.backgroundSelected }]}>
				<View style={[styles.progressFill, { backgroundColor: progressColor, width: `${(progress?.progress ?? 0) * 100}%` }]} />
			</View>
		</View>
	);
}

function NotificationAccessBand({ granted, onOpenSettings }: { granted: boolean | null; onOpenSettings: () => void }) {
	const colors = useTheme();
	if (granted === null) {
		return (
			<View style={[styles.accessBand, { borderColor: colors.border }]}>
				<ActivityIndicator color={colors.textSecondary} size="small" />
				<Text style={[styles.accessText, { color: colors.textSecondary }]}>Kontrolujem prístup</Text>
			</View>
		);
	}
	if (granted) {
		return (
			<View style={[styles.accessBand, { borderColor: colors.border }]}>
				<View style={[styles.statusDot, { backgroundColor: colors.success }]} />
				<Text style={[styles.accessText, { color: colors.text }]}>ČSOB pripojená</Text>
			</View>
		);
	}

	return (
		<View
			style={[
				styles.accessBand,
				{
					backgroundColor: colors.warningBackground,
					borderColor: colors.warning,
				},
			]}
		>
			<View style={[styles.statusDot, { backgroundColor: colors.warning }]} />
			<Text style={[styles.accessWarningText, { color: colors.text }]}>Prístup k notifikáciám je vypnutý</Text>
			<Pressable
				accessibilityRole="button"
				onPress={onOpenSettings}
				style={({ pressed }) => [styles.settingsButton, { borderColor: colors.warning }, pressed && styles.pressed]}
			>
				<SymbolView name={{ ios: "gearshape", android: "settings" }} size={20} tintColor={colors.text} />
				<Text style={[styles.settingsButtonText, { color: colors.text }]}>Povoliť prístup</Text>
			</Pressable>
		</View>
	);
}

function OverviewEmptyState({ error, isLoading, onRetry }: { error: string | null; isLoading: boolean; onRetry: () => void }) {
	const colors = useTheme();
	if (isLoading) {
		return <View style={styles.emptySlot} />;
	}
	if (error) {
		return (
			<View style={styles.emptyState}>
				<Text style={[styles.emptyTitle, { color: colors.text }]}>{error}</Text>
				<Pressable
					accessibilityRole="button"
					onPress={onRetry}
					style={({ pressed }) => [styles.retryButton, { borderColor: colors.border }, pressed && styles.pressed]}
				>
					<SymbolView name={{ ios: "arrow.clockwise", android: "refresh" }} size={20} tintColor={colors.text} />
					<Text style={[styles.retryText, { color: colors.text }]}>Skúsiť znova</Text>
				</Pressable>
			</View>
		);
	}

	return (
		<View style={styles.emptyState}>
			<Text style={[styles.emptyTitle, { color: colors.text }]}>Žiadne výdavky</Text>
		</View>
	);
}

const styles = StyleSheet.create({
	content: {
		flexGrow: 1,
		width: "100%",
		maxWidth: MaxContentWidth,
		alignSelf: "center",
		paddingHorizontal: 20,
		paddingBottom: 32,
	},
	separator: {
		height: StyleSheet.hairlineWidth,
	},
	summaryBand: {
		minHeight: 168,
		marginHorizontal: -20,
		paddingHorizontal: 20,
		paddingVertical: 22,
		borderTopWidth: StyleSheet.hairlineWidth,
		borderBottomWidth: StyleSheet.hairlineWidth,
	},
	summaryLabel: {
		fontSize: 14,
		fontWeight: "600",
	},
	summaryValueSlot: {
		height: 54,
		justifyContent: "center",
	},
	summaryValue: {
		fontSize: 36,
		fontWeight: "800",
		lineHeight: 46,
	},
	summaryError: {
		fontSize: 24,
		fontWeight: "700",
	},
	budgetSetup: {
		minHeight: 40,
		marginTop: 4,
		flexDirection: "row",
		alignItems: "center",
		gap: 8,
	},
	budgetSetupText: {
		fontSize: 14,
		fontWeight: "700",
	},
	budgetSummary: {
		marginTop: 4,
		gap: 8,
	},
	budgetRow: {
		height: 32,
		flexDirection: "row",
		alignItems: "center",
		gap: 10,
	},
	budgetDetail: {
		flex: 1,
		fontSize: 13,
		fontWeight: "600",
	},
	budgetPercentage: {
		fontSize: 14,
		fontWeight: "800",
	},
	editBudgetButton: {
		width: 32,
		height: 32,
		alignItems: "center",
		justifyContent: "center",
	},
	progressTrack: {
		height: 6,
		borderRadius: 3,
		overflow: "hidden",
	},
	progressFill: {
		height: 6,
		borderRadius: 3,
	},
	accessBand: {
		minHeight: 64,
		marginHorizontal: -20,
		paddingHorizontal: 20,
		paddingVertical: 10,
		borderBottomWidth: StyleSheet.hairlineWidth,
		flexDirection: "row",
		alignItems: "center",
		gap: 10,
	},
	statusDot: {
		width: 10,
		height: 10,
		borderRadius: 5,
	},
	accessText: {
		flex: 1,
		fontSize: 14,
		fontWeight: "700",
	},
	accessWarningText: {
		flex: 1,
		fontSize: 14,
		fontWeight: "700",
		lineHeight: 19,
	},
	settingsButton: {
		minHeight: 44,
		maxWidth: "48%",
		borderWidth: 1,
		borderRadius: 8,
		paddingHorizontal: 12,
		flexDirection: "row",
		alignItems: "center",
		justifyContent: "center",
		gap: 7,
	},
	settingsButtonText: {
		flexShrink: 1,
		fontSize: 13,
		fontWeight: "700",
		textAlign: "center",
	},
	inlineError: {
		marginHorizontal: -20,
		paddingHorizontal: 20,
		paddingVertical: 10,
	},
	inlineErrorText: {
		fontSize: 13,
		fontWeight: "600",
	},
	sectionTitle: {
		paddingTop: 24,
		paddingBottom: 8,
		fontSize: 18,
		fontWeight: "800",
	},
	emptySlot: {
		height: 72,
	},
	emptyState: {
		minHeight: 150,
		alignItems: "center",
		justifyContent: "center",
		gap: 16,
		paddingVertical: 24,
	},
	emptyTitle: {
		fontSize: 16,
		fontWeight: "700",
		textAlign: "center",
	},
	retryButton: {
		height: 44,
		borderWidth: 1,
		borderRadius: 8,
		paddingHorizontal: 16,
		flexDirection: "row",
		alignItems: "center",
		justifyContent: "center",
		gap: 8,
	},
	retryText: {
		fontSize: 14,
		fontWeight: "700",
	},
	pressed: {
		opacity: 0.6,
	},
});
