import { MenuView } from "@expo/ui/community/menu";
import { DarkTheme, DefaultTheme, Stack, ThemeProvider, useRouter } from "expo-router";
import { SymbolView } from "expo-symbols";
import { StyleSheet, useColorScheme, View } from "react-native";

import { Colors } from "@/constants/theme";

export default function RootLayout() {
	const colorScheme = useColorScheme();
	const isDark = colorScheme === "dark";
	const colors = Colors[isDark ? "dark" : "light"];
	const baseTheme = isDark ? DarkTheme : DefaultTheme;
	const navigationTheme = {
		...baseTheme,
		colors: {
			...baseTheme.colors,
			background: colors.background,
			card: colors.backgroundElement,
			text: colors.text,
			border: colors.border,
		},
	};

	return (
		<ThemeProvider value={navigationTheme}>
			<Stack
				screenOptions={{
					contentStyle: { backgroundColor: colors.background },
					headerShadowVisible: false,
					headerStyle: { backgroundColor: colors.backgroundElement },
					headerTitleStyle: { fontSize: 20, fontWeight: "700" },
				}}
			>
				<Stack.Screen name="index" options={{ title: "Výdavky", headerRight: DiagnosticsMenu }} />
				<Stack.Screen name="diagnostics" options={{ title: "Diagnostika", presentation: "modal" }} />
			</Stack>
		</ThemeProvider>
	);
}

function DiagnosticsMenu() {
	const router = useRouter();
	const colorScheme = useColorScheme();
	const colors = Colors[colorScheme === "dark" ? "dark" : "light"];

	return (
		<MenuView
			actions={[{ id: "diagnostics", title: "Diagnostika" }]}
			onPressAction={({ nativeEvent }) => {
				if (nativeEvent.event === "diagnostics") {
					router.push("./diagnostics");
				}
			}}
		>
			<View accessible accessibilityLabel="Ďalšie možnosti" accessibilityRole="button" style={styles.headerIcon}>
				<SymbolView name={{ ios: "ellipsis", android: "more_vert" }} size={24} tintColor={colors.text} />
			</View>
		</MenuView>
	);
}

const styles = StyleSheet.create({
	headerIcon: {
		width: 48,
		height: 48,
		alignItems: "center",
		justifyContent: "center",
	},
});
