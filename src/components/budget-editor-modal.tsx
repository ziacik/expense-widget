import { SymbolView } from "expo-symbols";
import { useState } from "react";
import { ActivityIndicator, KeyboardAvoidingView, Modal, Platform, Pressable, StyleSheet, Text, TextInput, View } from "react-native";

import { parseMonthlyBudgetInput } from "@/domain/budget/monthly-budget";
import { useTheme } from "@/hooks/use-theme";
import { formatEuroInput } from "@/utils/expense-format";

type BudgetEditorModalProps = {
	amountMinor: number | null;
	onClose: () => void;
	onSave: (amountMinor: number) => Promise<void>;
};

export function BudgetEditorModal({ amountMinor, onClose, onSave }: BudgetEditorModalProps) {
	const colors = useTheme();
	const [inputValue, setInputValue] = useState(() => (amountMinor === null ? "" : formatEuroInput(amountMinor)));
	const [error, setError] = useState<string | null>(null);
	const [isSaving, setIsSaving] = useState(false);

	const submit = async () => {
		const parsedAmount = parseMonthlyBudgetInput(inputValue);
		if (parsedAmount === null) {
			setError("Zadaj kladnú sumu s najviac dvoma desatinnými miestami");
			return;
		}

		setError(null);
		setIsSaving(true);
		try {
			await onSave(parsedAmount);
			onClose();
		} catch {
			setError("Rozpočet sa nepodarilo uložiť");
		} finally {
			setIsSaving(false);
		}
	};

	return (
		<Modal animationType="fade" onRequestClose={onClose} transparent visible>
			<KeyboardAvoidingView behavior={Platform.OS === "ios" ? "padding" : undefined} style={styles.backdrop}>
				<View style={[styles.dialog, { backgroundColor: colors.backgroundElement, borderColor: colors.border }]}>
					<View style={styles.header}>
						<Text style={[styles.title, { color: colors.text }]}>Mesačný rozpočet</Text>
						<Pressable
							accessibilityLabel="Zavrieť"
							accessibilityRole="button"
							disabled={isSaving}
							onPress={onClose}
							style={({ pressed }) => [styles.iconButton, pressed && styles.pressed]}
						>
							<SymbolView name={{ ios: "xmark", android: "close" }} size={22} tintColor={colors.text} />
						</Pressable>
					</View>

					<View style={[styles.inputRow, { borderColor: error ? colors.expense : colors.border }]}>
						<TextInput
							accessibilityLabel="Výška mesačného rozpočtu"
							autoFocus
							inputMode="decimal"
							keyboardType="decimal-pad"
							onChangeText={setInputValue}
							onSubmitEditing={() => void submit()}
							placeholder="500,00"
							placeholderTextColor={colors.textSecondary}
							returnKeyType="done"
							selectTextOnFocus
							style={[styles.input, { color: colors.text }]}
							value={inputValue}
						/>
						<Text style={[styles.currency, { color: colors.textSecondary }]}>€</Text>
					</View>

					<View style={styles.errorSlot}>{error ? <Text style={[styles.error, { color: colors.expense }]}>{error}</Text> : null}</View>

					<View style={styles.actions}>
						<Pressable
							accessibilityRole="button"
							disabled={isSaving}
							onPress={onClose}
							style={({ pressed }) => [styles.cancelButton, { borderColor: colors.border }, pressed && styles.pressed]}
						>
							<Text style={[styles.cancelText, { color: colors.text }]}>Zrušiť</Text>
						</Pressable>
						<Pressable
							accessibilityRole="button"
							disabled={isSaving}
							onPress={() => void submit()}
							style={({ pressed }) => [styles.saveButton, { backgroundColor: colors.text }, pressed && styles.pressed]}
						>
							{isSaving ? (
								<ActivityIndicator color={colors.background} size="small" />
							) : (
								<SymbolView name={{ ios: "checkmark", android: "check" }} size={20} tintColor={colors.background} />
							)}
							<Text style={[styles.saveText, { color: colors.background }]}>Uložiť</Text>
						</Pressable>
					</View>
				</View>
			</KeyboardAvoidingView>
		</Modal>
	);
}

const styles = StyleSheet.create({
	backdrop: {
		flex: 1,
		backgroundColor: "rgba(0, 0, 0, 0.58)",
		alignItems: "center",
		justifyContent: "center",
		padding: 20,
	},
	dialog: {
		width: "100%",
		maxWidth: 420,
		borderWidth: StyleSheet.hairlineWidth,
		borderRadius: 8,
		padding: 20,
	},
	header: {
		height: 40,
		flexDirection: "row",
		alignItems: "center",
		justifyContent: "space-between",
	},
	title: {
		fontSize: 20,
		fontWeight: "800",
	},
	iconButton: {
		width: 40,
		height: 40,
		alignItems: "center",
		justifyContent: "center",
	},
	inputRow: {
		height: 54,
		marginTop: 20,
		borderWidth: 1,
		borderRadius: 8,
		paddingHorizontal: 14,
		flexDirection: "row",
		alignItems: "center",
	},
	input: {
		flex: 1,
		fontSize: 20,
		fontWeight: "700",
	},
	currency: {
		fontSize: 18,
		fontWeight: "700",
	},
	errorSlot: {
		minHeight: 48,
		paddingTop: 8,
	},
	error: {
		fontSize: 13,
		fontWeight: "600",
		lineHeight: 18,
	},
	actions: {
		flexDirection: "row",
		justifyContent: "flex-end",
		gap: 10,
	},
	cancelButton: {
		height: 44,
		borderWidth: 1,
		borderRadius: 8,
		paddingHorizontal: 16,
		alignItems: "center",
		justifyContent: "center",
	},
	cancelText: {
		fontSize: 14,
		fontWeight: "700",
	},
	saveButton: {
		height: 44,
		minWidth: 112,
		borderRadius: 8,
		paddingHorizontal: 16,
		flexDirection: "row",
		alignItems: "center",
		justifyContent: "center",
		gap: 7,
	},
	saveText: {
		fontSize: 14,
		fontWeight: "800",
	},
	pressed: {
		opacity: 0.65,
	},
});
