package com.example.androidterminal.terminalview;

import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TextStyle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TerminalThemeManager {

    private static final Map<String, TerminalTheme> THEMES = new LinkedHashMap<>();

    static {
        // 1. Tokyo Night
        THEMES.put("Tokyo Night", new TerminalTheme(
            "Tokyo Night",
            0xFF1A1B26, // Background
            0xFFC0CAF5, // Foreground
            0xFFC0CAF5, // Cursor
            new int[] {
                0xFF15161E, // 0 Black
                0xFFF7768E, // 1 Red
                0xFF9ECE6A, // 2 Green
                0xFFE0AF68, // 3 Yellow
                0xFF7AA2F7, // 4 Blue
                0xFFBB9AF7, // 5 Magenta
                0xFF7DCFFF, // 6 Cyan
                0xFFA9B1D6, // 7 White
                0xFF414868, // 8 Bright Black
                0xFFF7768E, // 9 Bright Red
                0xFF9ECE6A, // 10 Bright Green
                0xFFE0AF68, // 11 Bright Yellow
                0xFF7AA2F7, // 12 Bright Blue
                0xFFBB9AF7, // 13 Bright Magenta
                0xFF7DCFFF, // 14 Bright Cyan
                0xFFC0CAF5  // 15 Bright White
            }
        ));

        // 2. Catppuccin Mocha
        THEMES.put("Catppuccin Mocha", new TerminalTheme(
            "Catppuccin Mocha",
            0xFF1E1E2E, // Background
            0xFFCDD6F4, // Foreground
            0xFFF5E0DC, // Cursor
            new int[] {
                0xFF45475A, // 0 Black
                0xFFF38BA8, // 1 Red
                0xFFA6E3A1, // 2 Green
                0xFFF9E2AF, // 3 Yellow
                0xFF89B4FA, // 4 Blue
                0xFFF5C2E7, // 5 Magenta
                0xFF94E2D5, // 6 Cyan
                0xFFBAC2DE, // 7 White
                0xFF585B70, // 8 Bright Black
                0xFFF38BA8, // 9 Bright Red
                0xFFA6E3A1, // 10 Bright Green
                0xFFF9E2AF, // 11 Bright Yellow
                0xFF89B4FA, // 12 Bright Blue
                0xFFF5C2E7, // 13 Bright Magenta
                0xFF94E2D5, // 14 Bright Cyan
                0xFFA6ADC8  // 15 Bright White
            }
        ));

        // 3. Dracula
        THEMES.put("Dracula", new TerminalTheme(
            "Dracula",
            0xFF282A36, // Background
            0xFFF8F8F2, // Foreground
            0xFFF8F8F2, // Cursor
            new int[] {
                0xFF21222C, // 0 Black
                0xFFFF5555, // 1 Red
                0xFF50FA7B, // 2 Green
                0xFFF1FA8C, // 3 Yellow
                0xFFBD93F9, // 4 Blue
                0xFFFF79C6, // 5 Magenta
                0xFF8BE9FD, // 6 Cyan
                0xFFBFBFBF, // 7 White
                0xFF4D4D4D, // 8 Bright Black
                0xFFFF6E6E, // 9 Bright Red
                0xFF69FF94, // 10 Bright Green
                0xFFFFFFA5, // 11 Bright Yellow
                0xFFD6ACFF, // 12 Bright Blue
                0xFFFF92DF, // 13 Bright Magenta
                0xFFA4FFFF, // 14 Bright Cyan
                0xFFFFFFFF  // 15 Bright White
            }
        ));

        // 4. One Dark
        THEMES.put("One Dark", new TerminalTheme(
            "One Dark",
            0xFF1E2127, // Background
            0xFFABB2BF, // Foreground
            0xFF528BFF, // Cursor
            new int[] {
                0xFF1E2127, // 0 Black
                0xFFE06C75, // 1 Red
                0xFF98C379, // 2 Green
                0xFFD19A66, // 3 Yellow
                0xFF61AFEF, // 4 Blue
                0xFFC678DD, // 5 Magenta
                0xFF56B6C2, // 6 Cyan
                0xFFABB2BF, // 7 White
                0xFF5C6370, // 8 Bright Black
                0xFFE06C75, // 9 Bright Red
                0xFF98C379, // 10 Bright Green
                0xFFD19A66, // 11 Bright Yellow
                0xFF61AFEF, // 12 Bright Blue
                0xFFC678DD, // 13 Bright Magenta
                0xFF56B6C2, // 14 Bright Cyan
                0xFFFFFFFF  // 15 Bright White
            }
        ));
    }

    public static List<String> getThemeNames() {
        return new ArrayList<>(THEMES.keySet());
    }

    public static TerminalTheme getTheme(String name) {
        TerminalTheme theme = THEMES.get(name);
        if (theme == null) {
            theme = THEMES.get("Tokyo Night");
        }
        return theme;
    }

    public static TerminalTheme getDefaultTheme() {
        return THEMES.get("Tokyo Night");
    }

    public static void applyTheme(TerminalEmulator emulator, TerminalTheme theme) {
        if (emulator == null || theme == null) return;
        try {
            emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_FOREGROUND] = theme.foreground;
            emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND] = theme.background;
            emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] = theme.cursor;
            for (int i = 0; i < 16 && i < theme.ansiColors.length; i++) {
                emulator.mColors.mCurrentColors[i] = theme.ansiColors[i];
            }
        } catch (Exception ignored) {}
    }
}
