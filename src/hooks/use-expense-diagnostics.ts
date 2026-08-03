import { useCallback, useEffect, useRef, useState } from "react";
import { AppState } from "react-native";

import ExpenseNotifications, { type ExpenseDiagnostics } from "../../modules/expense-notifications";

export function useExpenseDiagnostics() {
	const [diagnostics, setDiagnostics] = useState<ExpenseDiagnostics | null>(null);
	const [isLoading, setIsLoading] = useState(true);
	const [isRefreshing, setIsRefreshing] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const requestSequence = useRef(0);

	const load = useCallback(async () => {
		const requestId = ++requestSequence.current;

		try {
			const nextDiagnostics = await ExpenseNotifications.getDiagnosticsAsync();
			if (requestId !== requestSequence.current) {
				return;
			}
			setDiagnostics(nextDiagnostics);
			setError(null);
		} catch {
			if (requestId === requestSequence.current) {
				setError("Diagnostiku sa nepodarilo načítať");
			}
		} finally {
			if (requestId === requestSequence.current) {
				setIsLoading(false);
				setIsRefreshing(false);
			}
		}
	}, []);

	const refresh = useCallback(async () => {
		setIsRefreshing(true);
		await load();
	}, [load]);

	useEffect(() => {
		const loadTimer = setTimeout(() => void load(), 0);
		const dataSubscription = ExpenseNotifications.addListener("onExpenseDataChanged", () => void load());
		const appStateSubscription = AppState.addEventListener("change", (state) => {
			if (state === "active") {
				void load();
			}
		});

		return () => {
			clearTimeout(loadTimer);
			requestSequence.current += 1;
			dataSubscription.remove();
			appStateSubscription.remove();
		};
	}, [load]);

	return { diagnostics, isLoading, isRefreshing, error, refresh };
}
