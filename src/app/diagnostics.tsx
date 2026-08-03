import { SymbolView } from "expo-symbols";
import { ActivityIndicator, FlatList, Pressable, RefreshControl, StyleSheet, Text, View } from "react-native";

import { MaxContentWidth } from "@/constants/theme";
import { useExpenseDiagnostics } from "@/hooks/use-expense-diagnostics";
import { useTheme } from "@/hooks/use-theme";

type DiagnosticRow = { key: string; label: string; value: string; warning?: boolean };

export default function DiagnosticsScreen() {
	const colors = useTheme();
	const state = useExpenseDiagnostics();
	const rows: DiagnosticRow[] = state.diagnostics
		? [
				{
					key: "pending",
					label: "Čakajúce notifikácie",
					value: state.diagnostics.pending.toString(),
				},
				{
					key: "unsupported",
					label: "Nepodporované formáty",
					value: state.diagnostics.unsupported.toString(),
				},
				{
					key: "unparsed",
					label: "Nespracované formáty",
					value: state.diagnostics.unparsed.toString(),
				},
				{
					key: "possibleDuplicateGroups",
					label: "Možné duplicitné skupiny",
					value: state.diagnostics.possibleDuplicateGroups.toString(),
				},
				{
					key: "reusedNotificationKeys",
					label: "Opakovane použité kľúče",
					value: state.diagnostics.reusedNotificationKeys.toString(),
				},
				...(state.diagnostics.projectionError
					? [
							{
								key: "projectionError",
								label: "Mesačný súčet",
								value: "Chyba súčtu",
								warning: true,
							},
						]
					: []),
			]
		: [];

	return (
		<FlatList
			contentContainerStyle={[styles.content, { backgroundColor: colors.background }]}
			data={rows}
			ItemSeparatorComponent={() => <View style={[styles.separator, { backgroundColor: colors.border }]} />}
			keyExtractor={(item) => item.key}
			ListEmptyComponent={<DiagnosticsEmptyState error={state.error} isLoading={state.isLoading} onRetry={() => void state.refresh()} />}
			ListHeaderComponent={<Text style={[styles.sectionTitle, { color: colors.text }]}>Spracovanie</Text>}
			refreshControl={
				<RefreshControl
					colors={[colors.text]}
					onRefresh={() => void state.refresh()}
					progressBackgroundColor={colors.backgroundElement}
					refreshing={state.isRefreshing}
					tintColor={colors.text}
				/>
			}
			renderItem={({ item }) => (
				<View style={[styles.row, item.warning && { backgroundColor: colors.warningBackground }]}>
					<Text style={[styles.label, { color: colors.text }]}>{item.label}</Text>
					<Text style={[styles.value, { color: item.warning ? colors.warning : colors.textSecondary }]}>{item.value}</Text>
				</View>
			)}
		/>
	);
}

function DiagnosticsEmptyState({ error, isLoading, onRetry }: { error: string | null; isLoading: boolean; onRetry: () => void }) {
	const colors = useTheme();
	if (isLoading) {
		return (
			<View style={styles.emptyState}>
				<ActivityIndicator color={colors.text} />
			</View>
		);
	}
	if (error) {
		return (
			<View style={styles.emptyState}>
				<Text style={[styles.emptyText, { color: colors.text }]}>{error}</Text>
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

	return null;
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
	sectionTitle: {
		paddingTop: 24,
		paddingBottom: 12,
		fontSize: 18,
		fontWeight: "800",
	},
	row: {
		minHeight: 58,
		marginHorizontal: -20,
		paddingHorizontal: 20,
		paddingVertical: 12,
		flexDirection: "row",
		alignItems: "center",
		gap: 16,
	},
	separator: {
		height: StyleSheet.hairlineWidth,
	},
	label: {
		flex: 1,
		fontSize: 15,
		fontWeight: "600",
	},
	value: {
		maxWidth: "45%",
		fontSize: 15,
		fontWeight: "800",
		textAlign: "right",
	},
	emptyState: {
		minHeight: 220,
		alignItems: "center",
		justifyContent: "center",
		gap: 16,
	},
	emptyText: {
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
