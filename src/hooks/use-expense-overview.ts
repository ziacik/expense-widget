import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AppState } from "react-native";

import ExpenseNotifications, { type MonthTransaction } from "../../modules/expense-notifications";
import { calculateMonthlyBudgetProgress } from "@/domain/budget/monthly-budget";
import { compareMonthKeys, getBratislavaMonthKey } from "@/domain/transactions/month-selection";
import { summarizeMonth } from "@/domain/transactions/monthly-summary";

type SummaryState = { value: { totalMinor: number; transactionCount: number }; error: null } | { value: null; error: string };

const INITIAL_MONTH_KEY = getBratislavaMonthKey(Date.now());

export function useExpenseOverview() {
	const [currentMonthKey, setCurrentMonthKey] = useState(INITIAL_MONTH_KEY);
	const [selectedMonthKey, updateSelectedMonthKey] = useState(INITIAL_MONTH_KEY);
	const [transactions, setTransactions] = useState<MonthTransaction[]>([]);
	const [monthlyBudgetMinor, setMonthlyBudgetMinor] = useState<number | null>(null);
	const [notificationAccessGranted, setNotificationAccessGranted] = useState<boolean | null>(null);
	const [isLoading, setIsLoading] = useState(true);
	const [isRefreshing, setIsRefreshing] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const requestSequence = useRef(0);
	const currentMonthKeyRef = useRef(INITIAL_MONTH_KEY);

	const load = useCallback(async () => {
		const requestId = ++requestSequence.current;

		try {
			const [monthTransactions, accessStatus, monthlyBudget] = await Promise.all([
				ExpenseNotifications.getMonthTransactionsAsync(selectedMonthKey),
				ExpenseNotifications.getNotificationAccessStatusAsync(),
				ExpenseNotifications.getMonthlyBudgetAsync(),
			]);
			if (requestId !== requestSequence.current) {
				return;
			}

			setTransactions(monthTransactions.items);
			setNotificationAccessGranted(accessStatus.granted);
			setMonthlyBudgetMinor(monthlyBudget.amountMinor);
			setError(null);
		} catch {
			if (requestId === requestSequence.current) {
				setError("Údaje sa nepodarilo načítať");
			}
		} finally {
			if (requestId === requestSequence.current) {
				setIsLoading(false);
				setIsRefreshing(false);
			}
		}
	}, [selectedMonthKey]);

	const refresh = useCallback(async () => {
		setIsRefreshing(true);
		await load();
	}, [load]);

	const setSelectedMonthKey = useCallback((monthKey: string) => {
		requestSequence.current += 1;
		setIsLoading(true);
		setError(null);
		setTransactions([]);
		updateSelectedMonthKey(monthKey);
	}, []);

	useEffect(() => {
		const loadTimer = setTimeout(() => void load(), 0);
		return () => {
			clearTimeout(loadTimer);
			requestSequence.current += 1;
		};
	}, [load]);

	useEffect(() => {
		const refreshAfterNativeChange = () => void load();
		const dataSubscription = ExpenseNotifications.addListener("onExpenseDataChanged", refreshAfterNativeChange);
		const accessSubscription = ExpenseNotifications.addListener("onNotificationAccessChanged", refreshAfterNativeChange);
		const appStateSubscription = AppState.addEventListener("change", (state) => {
			if (state !== "active") {
				return;
			}

			const nextCurrentMonthKey = getBratislavaMonthKey(Date.now());
			const previousCurrentMonthKey = currentMonthKeyRef.current;
			currentMonthKeyRef.current = nextCurrentMonthKey;
			setCurrentMonthKey(nextCurrentMonthKey);
			updateSelectedMonthKey((previousSelectedMonthKey) =>
				previousSelectedMonthKey === previousCurrentMonthKey ? nextCurrentMonthKey : previousSelectedMonthKey,
			);
			void ExpenseNotifications.requestInboxDrainAsync();
			void load();
		});

		void ExpenseNotifications.requestInboxDrainAsync();

		return () => {
			dataSubscription.remove();
			accessSubscription.remove();
			appStateSubscription.remove();
		};
	}, [load]);

	const summary = useMemo<SummaryState>(() => {
		try {
			return {
				value: summarizeMonth(transactions, selectedMonthKey),
				error: null,
			};
		} catch {
			return { value: null, error: "Chyba súčtu" };
		}
	}, [selectedMonthKey, transactions]);

	const openNotificationSettings = useCallback(async () => {
		try {
			const result = await ExpenseNotifications.openNotificationAccessSettingsAsync();
			if (!result.opened) {
				setError("Nastavenia nie sú dostupné");
			}
		} catch {
			setError("Nastavenia sa nepodarilo otvoriť");
		}
	}, []);

	const saveMonthlyBudget = useCallback(async (amountMinor: number) => {
		try {
			const storedBudget = await ExpenseNotifications.setMonthlyBudgetAsync(amountMinor);
			if (storedBudget.amountMinor === null) {
				throw new Error("The stored monthly budget is missing.");
			}
			setMonthlyBudgetMinor(storedBudget.amountMinor);
			setError(null);
		} catch (saveError) {
			setError("Rozpočet sa nepodarilo uložiť");
			throw saveError;
		}
	}, []);

	const budgetProgress = useMemo(() => {
		if (summary.value === null || monthlyBudgetMinor === null) {
			return null;
		}
		return calculateMonthlyBudgetProgress(summary.value.totalMinor, monthlyBudgetMinor);
	}, [monthlyBudgetMinor, summary.value]);

	return {
		currentMonthKey,
		selectedMonthKey,
		setSelectedMonthKey,
		canSelectNextMonth: compareMonthKeys(selectedMonthKey, currentMonthKey) < 0,
		transactions,
		monthlyBudgetMinor,
		budgetProgress,
		notificationAccessGranted,
		isLoading,
		isRefreshing,
		error,
		summary,
		refresh,
		openNotificationSettings,
		saveMonthlyBudget,
	};
}
