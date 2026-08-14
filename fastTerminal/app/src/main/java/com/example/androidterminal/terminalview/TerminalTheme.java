package com.example.androidterminal.terminalview;

public final class TerminalTheme {
    public final String name;
    public final int background;
    public final int foreground;
    public final int cursor;
    public final int[] ansiColors; // 16 colors (0..15)

    public TerminalTheme(String name, int background, int foreground, int cursor, int[] ansiColors) {
        this.name = name;
        this.background = background;
        this.foreground = foreground;
        this.cursor = cursor;
        this.ansiColors = ansiColors;
    }
}
