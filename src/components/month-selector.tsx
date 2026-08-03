import { SymbolView } from "expo-symbols";
import { Pressable, StyleSheet, Text, View } from "react-native";

import { nextMonthKey, previousMonthKey } from "@/domain/transactions/month-selection";
import { useTheme } from "@/hooks/use-theme";
import { formatMonthLabel } from "@/utils/expense-format";

type MonthSelectorProps = {
	monthKey: string;
	canSelectNext: boolean;
	onChange: (monthKey: string) => void;
};

export function MonthSelector({ monthKey, canSelectNext, onChange }: MonthSelectorProps) {
	const colors = useTheme();

	return (
		<View style={styles.container}>
			<MonthButton
				accessibilityLabel="Predchádzajúci mesiac"
				icon={{ ios: "chevron.left", android: "chevron_left" }}
				onPress={() => onChange(previousMonthKey(monthKey))}
			/>
			<Text style={[styles.label, { color: colors.text }]}>{formatMonthLabel(monthKey)}</Text>
			<MonthButton
				accessibilityLabel="Nasledujúci mesiac"
				disabled={!canSelectNext}
				icon={{ ios: "chevron.right", android: "chevron_right" }}
				onPress={() => onChange(nextMonthKey(monthKey))}
			/>
		</View>
	);
}

type MonthButtonProps = {
	accessibilityLabel: string;
	disabled?: boolean;
	icon: { ios: "chevron.left" | "chevron.right"; android: "chevron_left" | "chevron_right" };
	onPress: () => void;
};

function MonthButton({ accessibilityLabel, disabled = false, icon, onPress }: MonthButtonProps) {
	const colors = useTheme();

	return (
		<Pressable
			accessibilityLabel={accessibilityLabel}
			accessibilityRole="button"
			accessibilityState={{ disabled }}
			disabled={disabled}
			hitSlop={8}
			onPress={onPress}
			style={({ pressed }) => [styles.button, pressed && styles.pressed, disabled && styles.disabled]}
		>
			<SymbolView name={icon} size={24} tintColor={colors.text} />
		</Pressable>
	);
}

const styles = StyleSheet.create({
	container: {
		height: 64,
		flexDirection: "row",
		alignItems: "center",
		justifyContent: "space-between",
	},
	button: {
		width: 48,
		height: 48,
		alignItems: "center",
		justifyContent: "center",
	},
	pressed: {
		opacity: 0.55,
	},
	disabled: {
		opacity: 0.25,
	},
	label: {
		flex: 1,
		fontSize: 17,
		fontWeight: "700",
		textAlign: "center",
	},
});
