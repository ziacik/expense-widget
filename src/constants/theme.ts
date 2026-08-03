import "@/global.css";

import { Platform } from "react-native";

export const Colors = {
	light: {
		text: "#1D2228",
		background: "#F7F8FA",
		backgroundElement: "#FFFFFF",
		backgroundSelected: "#E9EDF1",
		textSecondary: "#687078",
		border: "#D9DEE3",
		expense: "#C93232",
		success: "#257A4A",
		warning: "#956000",
		warningBackground: "#FFF4D6",
		errorBackground: "#FCE8E8",
	},
	dark: {
		text: "#F4F5F6",
		background: "#151719",
		backgroundElement: "#202326",
		backgroundSelected: "#30353A",
		textSecondary: "#AAB0B6",
		border: "#3A4046",
		expense: "#FF7777",
		success: "#62C58D",
		warning: "#F0BA53",
		warningBackground: "#3B301A",
		errorBackground: "#3B2224",
	},
} as const;

export type ThemeColor = keyof typeof Colors.light & keyof typeof Colors.dark;

export const Fonts = Platform.select({
	ios: {
		sans: "system-ui",
		serif: "ui-serif",
		rounded: "ui-rounded",
		mono: "ui-monospace",
	},
	default: {
		sans: "normal",
		serif: "serif",
		rounded: "normal",
		mono: "monospace",
	},
	web: {
		sans: "var(--font-display)",
		serif: "var(--font-serif)",
		rounded: "var(--font-rounded)",
		mono: "var(--font-mono)",
	},
});

export const Spacing = {
	half: 2,
	one: 4,
	two: 8,
	three: 16,
	four: 24,
	five: 32,
	six: 48,
} as const;

export const MaxContentWidth = 720;
