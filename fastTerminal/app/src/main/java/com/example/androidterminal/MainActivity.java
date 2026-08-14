package com.example.androidterminal;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.androidterminal.databinding.ActivityMainBinding;
import com.example.androidterminal.sftp.SftpManager;
import com.example.androidterminal.ssh.SshConnectionConfig;
import com.example.androidterminal.ssh.SshConnectionService;
import com.example.androidterminal.ssh.SshSessionRepository;
import com.example.androidterminal.ssh.SshTerminalSession;
import com.example.androidterminal.terminalview.TerminalView;
import com.example.androidterminal.terminalview.TerminalViewClient;
import com.example.androidterminal.ui.FileBrowserDrawer;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MainActivity extends AppCompatActivity implements TerminalViewClient, TerminalSessionClient, SshTerminalSession.Listener {

    private static final String INPUT_LOG_TAG = "AndroidTerminalInput";
    private static final String PREFS = "ssh-terminal-prefs";
    private static final String KEY_SAVED_CONNECTIONS = "saved_connections";
    private static final String KEY_SELECTED_CONNECTION_ID = "selected_connection_id";
    private static final String KEY_HOST = "host";
    private static final String KEY_PORT = "port";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_DEFAULT_FONT_SIZE = "default_font_size_sp";
    private static final String KEY_CURSOR_STYLE = "cursor_style";
    private static final String KEY_KEEP_SCREEN_ON = "keep_screen_on";
    private static final int DEFAULT_TERMINAL_TEXT_SIZE_SP = 30;
    private static final int MIN_TERMINAL_TEXT_SIZE_SP = 12;
    private static final int MAX_TERMINAL_TEXT_SIZE_SP = 60;
    private static final int CURSOR_STYLE_BLOCK = 0;
    private static final int CURSOR_STYLE_UNDERLINE = 1;
    private static final int CURSOR_STYLE_BAR = 2;
    private static final int DEFAULT_CURSOR_STYLE = CURSOR_STYLE_BLOCK;
    private static final boolean DEFAULT_KEEP_SCREEN_ON = true;
    private static final int MENU_FAVORITE = 1;
    private static final int MENU_EDIT = 2;
    private static final int MENU_DUPLICATE = 3;
    private static final int MENU_DELETE = 4;
    private static final long RECENT_CONNECTION_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final String KEY_TERMINAL_THEME = "key_terminal_theme";
    private static final String DEFAULT_TERMINAL_THEME = "Tokyo Night";

    private ActivityMainBinding binding;
    private SharedPreferences preferences;
    private TerminalView terminalView;
    private SshTerminalSession sshTerminalSession;
    private float terminalScaleFactor = 1.0f;
    private final List<SavedConnection> savedConnections = new ArrayList<>();
    private final List<String> tabSessionIds = new ArrayList<>();
    private String selectedConnectionId;
    private String activeSessionId;
    private boolean controlKeyActive = false;
    private boolean altKeyActive = false;
    private View ctrlButton;
    private View altButton;
    private FileBrowserDrawer fileBrowserDrawer;
    private View panelConnections;
    private View panelTerminal;
    private View panelSettings;
    private int activePanelId = R.id.nav_connections;
    private boolean switchingPanel = false;
    private boolean settingsShowingSubpage = false;
    private EditText connectionsSearch;
    private String connectionSearchQuery = "";
    private static final String KEY_SIDEBAR_COLLAPSED = "sidebar_collapsed";
    private boolean sidebarCollapsed = false;
    private View sidebarRoot;
    private View sidebarDivider;
    private View sidebarCollapseButton;
    private View termSidebarToggle;
    private View connectionsSidebarToggle;
    private View settingsSidebarToggle;

    private static final int FILTER_ALL = 0;
    private static final int FILTER_FAV = 1;
    private static final int FILTER_HOMELAB = 2;
    private static final int FILTER_CLOUD = 3;
    private int currentFilterCategory = FILTER_ALL;

    private final ActivityResultLauncher<String> filePickerLauncher =
        registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                handlePickedFile(uri);
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);

        panelConnections = binding.panelConnections.getRoot();
        panelTerminal = binding.panelTerminal.getRoot();
        panelSettings = binding.panelSettings.getRoot();

        restoreSavedConnections();

        setupAdaptiveNavigation();
        setupTerminal();
        bindActions();
        setupShortcutBar();
        setupFileBrowser();
        setupConnectionsPanel();
        setupAppSettingsPanel();
        switchToPanel(R.id.nav_connections);
    }

    @Override
    protected void onDestroy() {
        List<SshTerminalSession> sessions = SshSessionRepository.listSessions();
        for (SshTerminalSession session : sessions) {
            if (isFinishing()) {
                SshSessionRepository.disconnectAndRemove(session, "Disconnected");
            } else {
                SshSessionRepository.detachUi(session);
            }
        }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (terminalView != null) {
            terminalView.setTerminalCursorBlinkerRate(600);
            terminalView.setTerminalCursorBlinkerState(true, true);
        }
    }

    @Override
    protected void onPause() {
        if (terminalView != null) {
            terminalView.setTerminalCursorBlinkerState(false, false);
        }
        super.onPause();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (sshTerminalSession != null && shouldRoutePhysicalKeyboardToTerminal() && shouldTreatAsTerminalHardwareKey(event)) {
            Log.d(INPUT_LOG_TAG, "dispatchKeyEvent action=" + event.getAction() + " keyCode=" + event.getKeyCode()
                + " source=" + event.getSource() + " deviceId=" + event.getDeviceId());

            // Only intercept keys that need terminal-specific handling (Ctrl/Alt combos, Escape).
            // Regular character keys go through the normal IME pipeline so input method
            // switching (e.g. Shift for Chinese/English) works correctly.
            boolean hasModifier = event.isCtrlPressed() || event.isAltPressed();
            boolean isSpecialKey = event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE
                || event.getKeyCode() == KeyEvent.KEYCODE_BACK
                || event.getKeyCode() == KeyEvent.KEYCODE_TAB;

            if (!hasModifier && !isSpecialKey) {
                // Let regular keys go through IME pipeline
                Log.d(INPUT_LOG_TAG, "passing to IME pipeline");
                return super.dispatchKeyEvent(event);
            }

            if (event.getAction() == KeyEvent.ACTION_DOWN && handleTerminalZoomShortcut(event)) {
                Log.d(INPUT_LOG_TAG, "handled by zoom shortcut");
                return true;
            }

            if (event.getAction() == KeyEvent.ACTION_DOWN && handleCtrlTabShortcut(event)) {
                Log.d(INPUT_LOG_TAG, "handled by ctrl tab shortcut");
                return true;
            }

            if (event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE || event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    sshTerminalSession.sendEscape();
                }
                return true;
            }

            if (terminalView != null) {
                terminalView.requestFocus();
                switch (event.getAction()) {
                    case KeyEvent.ACTION_DOWN:
                        boolean handledDown = terminalView.onKeyDown(event.getKeyCode(), event);
                        Log.d(INPUT_LOG_TAG, "terminalView.onKeyDown handled=" + handledDown);
                        return handledDown;
                    case KeyEvent.ACTION_UP:
                        boolean handledUp = terminalView.onKeyUp(event.getKeyCode(), event);
                        Log.d(INPUT_LOG_TAG, "terminalView.onKeyUp handled=" + handledUp);
                        return handledUp;
                    case KeyEvent.ACTION_MULTIPLE:
                        boolean handledMultiple = terminalView.onKeyDown(event.getKeyCode(), event);
                        Log.d(INPUT_LOG_TAG, "terminalView.onKeyMultiple handled=" + handledMultiple);
                        return handledMultiple;
                    default:
                        break;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean shouldTreatAsTerminalHardwareKey(KeyEvent event) {
        if (event == null) {
            return false;
        }

        if (event.getDeviceId() == KeyCharacterMap.VIRTUAL_KEYBOARD) {
            return false;
        }

        int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_SLEEP:
            case KeyEvent.KEYCODE_WAKEUP:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_STOP:
            case KeyEvent.KEYCODE_MUTE:
            case KeyEvent.KEYCODE_SHIFT_LEFT:
            case KeyEvent.KEYCODE_SHIFT_RIGHT:
                return false;
            default:
                return true;
        }
    }

    private void setupTerminal() {
        terminalView = new TerminalView(this, null);
        terminalView.setTerminalViewClient(this);
        terminalView.setFocusable(true);
        terminalView.setFocusableInTouchMode(true);
        terminalView.setKeepScreenOn(preferences.getBoolean(KEY_KEEP_SCREEN_ON, DEFAULT_KEEP_SCREEN_ON));
        int defaultFontSize = preferences.getInt(KEY_DEFAULT_FONT_SIZE, DEFAULT_TERMINAL_TEXT_SIZE_SP);
        terminalView.setTextSize(defaultFontSize);
        terminalScaleFactor = defaultFontSize / (float) DEFAULT_TERMINAL_TEXT_SIZE_SP;
        Typeface nerdFont = Typeface.createFromAsset(getAssets(), "fonts/JetBrainsMonoNerdFont-Regular.ttf");
        terminalView.setTypeface(nerdFont);
        applyCompactPointerIcon();

        String savedTheme = preferences.getString(KEY_TERMINAL_THEME, DEFAULT_TERMINAL_THEME);
        com.example.androidterminal.terminalview.TerminalTheme currentTheme = com.example.androidterminal.terminalview.TerminalThemeManager.getTheme(savedTheme);
        terminalView.setTheme(currentTheme);
        binding.panelTerminal.terminalContainer.setBackgroundColor(currentTheme.background);

        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        );
        binding.panelTerminal.terminalContainer.addView(terminalView, layoutParams);
        restoreOrCreateTabs();
    }

    private void bindActions() {
        binding.panelTerminal.newTabButton.setOnClickListener(v -> showConnectionPickerDialog());
        binding.panelTerminal.terminalEmptyGoConnections.setOnClickListener(v -> switchToPanel(R.id.nav_connections));
        binding.panelTerminal.termOpenSftpButton.setOnClickListener(v -> toggleSftpDrawer());

        List<String> themeList = com.example.androidterminal.terminalview.TerminalThemeManager.getThemeNames();
        android.widget.ArrayAdapter<String> themeAdapter = new android.widget.ArrayAdapter<>(this, R.layout.item_theme_spinner_selected, themeList);
        themeAdapter.setDropDownViewResource(R.layout.item_theme_spinner_dropdown);
        binding.panelTerminal.termThemeSpinner.setAdapter(themeAdapter);
        String savedThemeName = preferences.getString(KEY_TERMINAL_THEME, DEFAULT_TERMINAL_THEME);
        int themeIndex = themeList.indexOf(savedThemeName);
        if (themeIndex >= 0) {
            binding.panelTerminal.termThemeSpinner.setSelection(themeIndex, false);
        }
        binding.panelTerminal.termThemeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String themeName = themeList.get(position);
                preferences.edit().putString(KEY_TERMINAL_THEME, themeName).apply();
                com.example.androidterminal.terminalview.TerminalTheme theme = com.example.androidterminal.terminalview.TerminalThemeManager.getTheme(themeName);
                if (terminalView != null) {
                    terminalView.setTheme(theme);
                }
                binding.panelTerminal.terminalContainer.setBackgroundColor(theme.background);
                for (SshTerminalSession s : SshSessionRepository.listSessions()) {
                    if (s.getEmulator() != null) {
                        com.example.androidterminal.terminalview.TerminalThemeManager.applyTheme(s.getEmulator(), theme);
                    }
                }
                if (terminalView != null) {
                    terminalView.invalidate();
                }
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private SavedConnection getFavoriteOrFirstConnection() {
        if (savedConnections == null || savedConnections.isEmpty()) return null;
        for (SavedConnection sc : savedConnections) {
            if (sc.favorite) return sc;
        }
        return savedConnections.get(0);
    }

    private void toggleSftpDrawer() {
        SshTerminalSession session = getActiveSession();
        if (session == null || !session.isConnected()) {
            SavedConnection fav = getFavoriteOrFirstConnection();
            if (fav != null) {
                openSftpForConnection(fav);
                return;
            }
            toast(getString(R.string.error_connect_ssh_first));
            return;
        }
        switchToPanel(R.id.nav_terminal);
        if (fileBrowserDrawer != null) {
            fileBrowserDrawer.setSession(session);
            fileBrowserDrawer.toggle();
        }
    }

    private void setupShortcutBar() {
        // Row 1: special characters
        String[] row1Keys = {"Esc", "Tab", "~", "/", "|", "-", "_", "=", "+", "[", "]", "{", "}", "\\", ":", ";", "\"", "'"};
        for (String key : row1Keys) {
            binding.panelTerminal.shortcutRow1.addView(createShortcutKeyButton(key, false));
        }

        // Row 2: modifier keys, arrows, combos & macro keys
        ctrlButton = createShortcutKeyButton("Ctrl", true);
        altButton = createShortcutKeyButton("Alt", true);
        binding.panelTerminal.shortcutRow2.addView(ctrlButton);
        binding.panelTerminal.shortcutRow2.addView(altButton);

        String[] arrowKeys = {"↑", "↓", "←", "→"};
        for (String key : arrowKeys) {
            binding.panelTerminal.shortcutRow2.addView(createShortcutKeyButton(key, false));
        }

        String[] tabActions = {"Ctrl+T", "Ctrl+W", "Ctrl+←", "Ctrl+→"};
        for (String key : tabActions) {
            binding.panelTerminal.shortcutRow2.addView(createShortcutKeyButton(key, false));
        }

        String[] ctrlCombos = {"Ctrl+C", "Ctrl+D", "Ctrl+Z", "Ctrl+L", "Ctrl+R", "Ctrl+A", "Ctrl+E"};
        for (String key : ctrlCombos) {
            binding.panelTerminal.shortcutRow2.addView(createShortcutKeyButton(key, false));
        }

        String[] macroKeys = {"⚡ htop", "🐳 docker", "🌿 git", "🧹 clear"};
        for (String key : macroKeys) {
            binding.panelTerminal.shortcutRow2.addView(createShortcutKeyButton(key, false));
        }
    }

    private void setupFileBrowser() {
        fileBrowserDrawer = new FileBrowserDrawer(this, binding.drawerLayout, binding.fileBrowserPanel.getRoot());
        fileBrowserDrawer.setUploadCallback(() -> filePickerLauncher.launch("*/*"));

        binding.showFileBrowserButton.setOnClickListener(v -> {
            SshTerminalSession session = getActiveSession();
            if (session == null || !session.isConnected()) {
                toast(getString(R.string.error_connect_ssh_first));
                return;
            }
            fileBrowserDrawer.setSession(session);
            fileBrowserDrawer.toggle();
        });
    }

    private void handlePickedFile(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) return;

            String fileName = "upload";
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) fileName = cursor.getString(nameIndex);
                cursor.close();
            }

            File cacheFile = new File(getCacheDir(), fileName);
            FileOutputStream outputStream = new FileOutputStream(cacheFile);
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.close();
            inputStream.close();

            fileBrowserDrawer.uploadFile(cacheFile);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.upload_failed_format, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void setupAdaptiveNavigation() {
        setupSidebarNavigationIfPresent();
        setupDockNavigationIfPresent();
    }

    private void setupDockNavigationIfPresent() {
        View connections = binding.getRoot().findViewById(R.id.dock_connections);
        if (connections == null) {
            return;
        }
        connections.setOnClickListener(v -> switchToPanel(R.id.nav_connections));
        binding.getRoot().findViewById(R.id.dock_terminal).setOnClickListener(v -> switchToPanel(R.id.nav_terminal));
        binding.getRoot().findViewById(R.id.dock_settings).setOnClickListener(v -> switchToPanel(R.id.nav_settings));
    }

    private void setupSidebarNavigationIfPresent() {
        View connections = binding.getRoot().findViewById(R.id.sidebar_nav_connections);
        View terminal = binding.getRoot().findViewById(R.id.sidebar_nav_terminal);
        View sftp = binding.getRoot().findViewById(R.id.sidebar_nav_sftp);
        View settings = binding.getRoot().findViewById(R.id.sidebar_nav_settings);
        if (connections != null) connections.setOnClickListener(v -> switchToPanel(R.id.nav_connections));
        if (terminal != null) terminal.setOnClickListener(v -> switchToPanel(R.id.nav_terminal));
        if (sftp != null) sftp.setOnClickListener(v -> toggleSftpDrawer());
        if (settings != null) settings.setOnClickListener(v -> switchToPanel(R.id.nav_settings));

        sidebarRoot = binding.getRoot().findViewById(R.id.sidebar_root);
        sidebarDivider = binding.getRoot().findViewById(R.id.sidebar_divider);
        sidebarCollapseButton = binding.getRoot().findViewById(R.id.sidebar_collapse_button);
        termSidebarToggle = binding.panelTerminal.termSidebarToggleButton;
        connectionsSidebarToggle = panelConnections.findViewById(R.id.connections_sidebar_toggle);
        settingsSidebarToggle = panelSettings.findViewById(R.id.settings_sidebar_toggle);

        sidebarCollapsed = preferences.getBoolean(KEY_SIDEBAR_COLLAPSED, false);

        if (sidebarCollapseButton != null) {
            sidebarCollapseButton.setOnClickListener(v -> toggleSidebar());
        }
        if (termSidebarToggle != null) {
            termSidebarToggle.setOnClickListener(v -> toggleSidebar());
        }
        if (connectionsSidebarToggle != null) {
            connectionsSidebarToggle.setOnClickListener(v -> toggleSidebar());
        }
        if (settingsSidebarToggle != null) {
            settingsSidebarToggle.setOnClickListener(v -> toggleSidebar());
        }

        updateSidebarVisibility(false);
    }

    public void toggleSidebar() {
        setSidebarCollapsed(!sidebarCollapsed, true);
    }

    public void setSidebarCollapsed(boolean collapsed, boolean animate) {
        sidebarCollapsed = collapsed;
        preferences.edit().putBoolean(KEY_SIDEBAR_COLLAPSED, sidebarCollapsed).apply();
        updateSidebarVisibility(animate);
    }

    private void updateSidebarVisibility(boolean animate) {
        if (sidebarRoot == null) return;
        boolean isTablet = isTabletOrLandscape();
        if (!isTablet) {
            return;
        }

        if (sidebarCollapsed) {
            sidebarRoot.setVisibility(View.GONE);
            if (sidebarDivider != null) sidebarDivider.setVisibility(View.GONE);
            if (termSidebarToggle != null) termSidebarToggle.setVisibility(View.VISIBLE);
            if (connectionsSidebarToggle != null) connectionsSidebarToggle.setVisibility(View.VISIBLE);
            if (settingsSidebarToggle != null) settingsSidebarToggle.setVisibility(View.VISIBLE);
        } else {
            sidebarRoot.setVisibility(View.VISIBLE);
            if (sidebarDivider != null) sidebarDivider.setVisibility(View.VISIBLE);
            if (termSidebarToggle != null) termSidebarToggle.setVisibility(View.GONE);
            if (connectionsSidebarToggle != null) connectionsSidebarToggle.setVisibility(View.GONE);
            if (settingsSidebarToggle != null) settingsSidebarToggle.setVisibility(View.GONE);
        }
    }

    private void switchToPanel(int panelId) {
        switchingPanel = true;
        activePanelId = panelId;
        updateNavigationSelection(panelId);
        switchingPanel = false;

        // Ensure file browser drawer does not squeeze non-terminal panels (Connections / Settings)
        if (panelId != R.id.nav_terminal) {
            if (fileBrowserDrawer != null && fileBrowserDrawer.isOpen()) {
                fileBrowserDrawer.close();
            }
        }

        // Cross-fade panels (alpha only — TerminalView is a custom canvas renderer,
        // translate/slide can leave black gaps on return).
        fadePanel(panelConnections, panelId == R.id.nav_connections);
        fadePanel(panelTerminal, panelId == R.id.nav_terminal);
        fadePanel(panelSettings, panelId == R.id.nav_settings);

        if (panelId == R.id.nav_connections) {
            renderConnectionsPanel();
        } else if (panelId == R.id.nav_terminal) {
            if (terminalView != null) terminalView.post(terminalView::requestFocus);
        } else if (panelId == R.id.nav_settings) {
            if (settingsShowingSubpage) {
                showSettingsMain();
            }
        }
    }

    private void fadePanel(View panel, boolean show) {
        panel.animate().cancel();
        if (show) {
            if (panel.getVisibility() != View.VISIBLE) {
                panel.setAlpha(0f);
                panel.setVisibility(View.VISIBLE);
            }
            panel.animate().alpha(1f).setDuration(200).start();
        } else {
            if (panel.getVisibility() == View.VISIBLE) {
                panel.animate().alpha(0f).setDuration(200)
                    .withEndAction(() -> {
                        panel.setVisibility(View.GONE);
                        panel.setAlpha(1f);
                    })
                    .start();
            }
        }
    }

    private void updateNavigationSelection(int panelId) {
        updateDockSelection(panelId);
        updateSidebarItem(R.id.sidebar_nav_connections, panelId == R.id.nav_connections);
        updateSidebarItem(R.id.sidebar_nav_terminal, panelId == R.id.nav_terminal);
        updateSidebarItem(R.id.sidebar_nav_settings, panelId == R.id.nav_settings);
    }

    private void updateDockSelection(int panelId) {
        updateDockItem(R.id.dock_connections, R.id.dock_connections_icon, R.id.dock_connections_label, panelId == R.id.nav_connections);
        updateDockItem(R.id.dock_terminal, R.id.dock_terminal_icon, R.id.dock_terminal_label, panelId == R.id.nav_terminal);
        updateDockItem(R.id.dock_settings, R.id.dock_settings_icon, R.id.dock_settings_label, panelId == R.id.nav_settings);
    }

    private void updateDockItem(int itemId, int iconId, int labelId, boolean selected) {
        View item = binding.getRoot().findViewById(itemId);
        if (item == null) {
            return;
        }
        android.widget.ImageView icon = item.findViewById(iconId);
        TextView label = item.findViewById(labelId);
        int color = ContextCompat.getColor(this, selected ? R.color.brand : R.color.text_muted_on_dark);
        if (icon != null) {
            ImageViewCompat.setImageTintList(icon, android.content.res.ColorStateList.valueOf(color));
        }
        if (label != null) {
            label.setTextColor(color);
        }
    }

    private void updateSidebarItem(int viewId, boolean selected) {
        View item = binding.getRoot().findViewById(viewId);
        if (item == null) {
            return;
        }
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(ContextCompat.getColor(this, selected ? R.color.sidebar_item_active : android.R.color.transparent));
        item.setBackground(bg);
        tintSidebarItem(item, selected);
    }

    private void tintSidebarItem(View item, boolean selected) {
        int tint = ContextCompat.getColor(this, selected ? R.color.brand : R.color.text_muted_on_dark);
        if (item instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) item;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child instanceof TextView) {
                    ((TextView) child).setTextColor(tint);
                } else if (child instanceof androidx.appcompat.widget.AppCompatImageView) {
                    ImageViewCompat.setImageTintList((android.widget.ImageView) child, ColorStateList.valueOf(tint));
                }
            }
        }
    }

    private void setupConnectionsPanel() {
        panelConnections.findViewById(R.id.connections_add_button).setOnClickListener(v -> showConnectionEditor(null));
        panelConnections.findViewById(R.id.connections_empty_add_button).setOnClickListener(v -> showConnectionEditor(null));
        View clearSearch = panelConnections.findViewById(R.id.connections_search_clear);
        if (clearSearch != null) {
            clearSearch.setOnClickListener(v -> connectionsSearch.setText(""));
        }
        connectionsSearch = panelConnections.findViewById(R.id.connections_search);
        connectionsSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                connectionSearchQuery = s.toString().trim();
                if (clearSearch != null) {
                    clearSearch.setVisibility(connectionSearchQuery.isEmpty() ? View.GONE : View.VISIBLE);
                }
                renderConnectionsPanel();
            }
        });

        View pillAll = panelConnections.findViewById(R.id.filter_pill_all);
        View pillFav = panelConnections.findViewById(R.id.filter_pill_fav);
        View pillHome = panelConnections.findViewById(R.id.filter_pill_homelab);
        View pillCloud = panelConnections.findViewById(R.id.filter_pill_cloud);

        if (pillAll != null) {
            pillAll.setOnClickListener(v -> setFilterCategory(FILTER_ALL));
            pillFav.setOnClickListener(v -> setFilterCategory(FILTER_FAV));
            pillHome.setOnClickListener(v -> setFilterCategory(FILTER_HOMELAB));
            pillCloud.setOnClickListener(v -> setFilterCategory(FILTER_CLOUD));
        }

        renderConnectionsPanel();
    }

    private void setFilterCategory(int category) {
        currentFilterCategory = category;
        updateFilterPillsUi();
        renderConnectionsPanel();
    }

    private void updateFilterPillsUi() {
        TextView pillAll = panelConnections.findViewById(R.id.filter_pill_all);
        TextView pillFav = panelConnections.findViewById(R.id.filter_pill_fav);
        TextView pillHome = panelConnections.findViewById(R.id.filter_pill_homelab);
        TextView pillCloud = panelConnections.findViewById(R.id.filter_pill_cloud);
        if (pillAll == null) return;

        pillAll.setBackgroundResource(currentFilterCategory == FILTER_ALL ? R.drawable.bg_filter_pill_active : R.drawable.bg_filter_pill);
        pillAll.setTextColor(ContextCompat.getColor(this, currentFilterCategory == FILTER_ALL ? R.color.brand : R.color.text_secondary));

        pillFav.setBackgroundResource(currentFilterCategory == FILTER_FAV ? R.drawable.bg_filter_pill_active : R.drawable.bg_filter_pill);
        pillFav.setTextColor(ContextCompat.getColor(this, currentFilterCategory == FILTER_FAV ? R.color.brand : R.color.text_secondary));

        pillHome.setBackgroundResource(currentFilterCategory == FILTER_HOMELAB ? R.drawable.bg_filter_pill_active : R.drawable.bg_filter_pill);
        pillHome.setTextColor(ContextCompat.getColor(this, currentFilterCategory == FILTER_HOMELAB ? R.color.brand : R.color.text_secondary));

        pillCloud.setBackgroundResource(currentFilterCategory == FILTER_CLOUD ? R.drawable.bg_filter_pill_active : R.drawable.bg_filter_pill);
        pillCloud.setTextColor(ContextCompat.getColor(this, currentFilterCategory == FILTER_CLOUD ? R.color.brand : R.color.text_secondary));
    }

    private void renderConnectionsPanel() {
        ViewGroup listContainer = panelConnections.findViewById(R.id.connections_list);
        View emptyState = panelConnections.findViewById(R.id.connections_empty_state);
        TextView countView = panelConnections.findViewById(R.id.connections_count);
        if (countView != null) {
            int online = 0;
            for (SavedConnection sc : savedConnections) {
                if (isConnectionActive(sc)) {
                    online++;
                }
            }
            if (savedConnections.isEmpty()) {
                countView.setText(getString(R.string.connections_count_format, 0));
            } else if (savedConnections.size() == 1) {
                countView.setText(getString(R.string.connections_count_one));
            } else {
                countView.setText(getString(R.string.connections_status_format, online, savedConnections.size()));
            }
        }
        listContainer.removeAllViews();

        List<SavedConnection> filtered = filterConnections();
        filtered.sort(this::compareConnectionsForDisplay);

        boolean showEmpty = filtered.isEmpty();
        emptyState.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        listContainer.setVisibility(showEmpty ? View.GONE : View.VISIBLE);

        if (showEmpty) {
            return;
        }

        renderConnectionList(listContainer, filtered);
    }

    private boolean isTabletOrLandscape() {
        return getResources().getConfiguration().screenWidthDp >= 600;
    }

    private List<SavedConnection> filterConnections() {
        String query = connectionSearchQuery.toLowerCase();
        List<SavedConnection> filtered = new ArrayList<>();
        for (SavedConnection sc : savedConnections) {
            if (currentFilterCategory == FILTER_FAV && !sc.favorite) {
                continue;
            }
            if (currentFilterCategory == FILTER_HOMELAB) {
                boolean isLocal = sc.host.startsWith("192.168.") || sc.host.startsWith("10.") || sc.host.startsWith("172.16.") || sc.host.startsWith("127.") || sc.host.contains(".local") || (!TextUtils.isEmpty(sc.label) && sc.label.toLowerCase().contains("home"));
                if (!isLocal) continue;
            }
            if (currentFilterCategory == FILTER_CLOUD) {
                boolean isLocal = sc.host.startsWith("192.168.") || sc.host.startsWith("10.") || sc.host.startsWith("172.16.") || sc.host.startsWith("127.") || sc.host.contains(".local");
                if (isLocal) continue;
            }

            if (query.isEmpty()) {
                filtered.add(sc);
                continue;
            }
            String haystack = (sc.label + " " + sc.username + " " + sc.username + "@" + sc.host + " " + sc.host + " " + sc.host + ":" + sc.port).toLowerCase();
            if (haystack.contains(query)) {
                filtered.add(sc);
            }
        }
        return filtered;
    }

    private int compareConnectionsForDisplay(SavedConnection a, SavedConnection b) {
        boolean aActive = isConnectionActive(a);
        boolean bActive = isConnectionActive(b);
        if (aActive != bActive) {
            return aActive ? -1 : 1;
        }
        if (a.favorite != b.favorite) {
            return a.favorite ? -1 : 1;
        }
        if (a.lastConnectedAt != b.lastConnectedAt) {
            return Long.compare(b.lastConnectedAt, a.lastConnectedAt);
        }
        return a.host.compareToIgnoreCase(b.host);
    }

    private void renderConnectionList(ViewGroup parent, List<SavedConnection> items) {
        if (items.isEmpty()) {
            return;
        }

        if (isTabletOrLandscape()) {
            LinearLayout currentRow = null;
            for (int i = 0; i < items.size(); i++) {
                if (i % 2 == 0) {
                    currentRow = new LinearLayout(this);
                    currentRow.setOrientation(LinearLayout.HORIZONTAL);
                    LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    rowLp.bottomMargin = dp(16);
                    currentRow.setLayoutParams(rowLp);
                    parent.addView(currentRow);
                }
                View card = createConnectionCard(currentRow, items.get(i));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
                if (i % 2 == 0) {
                    lp.setMarginEnd(dp(10));
                } else {
                    lp.setMarginStart(dp(10));
                }
                card.setLayoutParams(lp);
                currentRow.addView(card);
            }
            if (items.size() % 2 != 0 && currentRow != null) {
                View placeholder = new View(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 0, 1.0f);
                lp.setMarginStart(dp(10));
                placeholder.setLayoutParams(lp);
                currentRow.addView(placeholder);
            }
        } else {
            for (SavedConnection sc : items) {
                View card = createConnectionCard(parent, sc);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                );
                lp.bottomMargin = dp(16);
                card.setLayoutParams(lp);
                parent.addView(card);
            }
        }
    }

    private View createConnectionCard(ViewGroup parent, SavedConnection sc) {
        View itemView = getLayoutInflater().inflate(R.layout.item_connection_card, parent, false);
        MaterialCardView cardView = itemView.findViewById(R.id.connection_card);
        TextView labelView = itemView.findViewById(R.id.connection_label);
        TextView titleView = itemView.findViewById(R.id.connection_title);
        TextView detailsView = itemView.findViewById(R.id.connection_details);
        TextView statusText = itemView.findViewById(R.id.connection_status_text);
        View statusDot = itemView.findViewById(R.id.connection_status_dot);
        TextView lastConnectedView = itemView.findViewById(R.id.connection_last_connected);
        AppCompatImageButton favoriteButton = itemView.findViewById(R.id.connection_favorite);
        View overflowButton = itemView.findViewById(R.id.connection_overflow);
        View quickTermBtn = itemView.findViewById(R.id.connection_quick_terminal_button);
        View quickSftpBtn = itemView.findViewById(R.id.connection_quick_sftp_button);

        boolean hasLabel = !TextUtils.isEmpty(sc.label);
        if (hasLabel) {
            titleView.setText(sc.label);
            labelView.setVisibility(View.VISIBLE);
            labelView.setText("SSH");
        } else {
            titleView.setText(sc.host);
            labelView.setVisibility(View.GONE);
        }

        detailsView.setText(sc.username + "@" + sc.host + ":" + sc.port);
        statusText.setText(buildConnectionStatusText(sc));

        if (lastConnectedView != null) {
            if (isConnectionActive(sc)) {
                lastConnectedView.setText("⚡ 1.8ms");
                lastConnectedView.setTextColor(ContextCompat.getColor(this, R.color.brand));
            } else if (sc.lastConnectedAt > 0) {
                lastConnectedView.setText(formatRelativeTime(sc.lastConnectedAt));
                lastConnectedView.setTextColor(ContextCompat.getColor(this, R.color.text_muted_on_dark));
            } else {
                lastConnectedView.setText("");
            }
        }

        setStatusDot(statusDot, isConnectionActive(sc) || isConnectionRecent(sc));
        ImageViewCompat.setImageTintList(favoriteButton, ColorStateList.valueOf(ContextCompat.getColor(this, sc.favorite ? R.color.brand_amber : R.color.text_muted_on_dark)));
        favoriteButton.setImageResource(sc.favorite ? R.drawable.ic_star : R.drawable.ic_star_border);

        cardView.setOnClickListener(v -> connect(sc));
        cardView.setOnLongClickListener(v -> {
            showConnectionOptionsMenu(overflowButton, sc);
            return true;
        });

        if (quickTermBtn != null) {
            quickTermBtn.setOnClickListener(v -> connect(sc));
        }
        if (quickSftpBtn != null) {
            quickSftpBtn.setOnClickListener(v -> openSftpForConnection(sc));
        }

        favoriteButton.setOnClickListener(v -> toggleFavorite(sc));
        overflowButton.setOnClickListener(v -> showConnectionOptionsMenu(overflowButton, sc));
        return itemView;
    }

    private void openSftpForConnection(SavedConnection sc) {
        connect(sc);
        switchToPanel(R.id.nav_terminal);
        if (fileBrowserDrawer != null) {
            SshTerminalSession session = getActiveSession();
            if (session != null) {
                fileBrowserDrawer.setSession(session);
            }
            if (!fileBrowserDrawer.isOpen()) {
                fileBrowserDrawer.open();
            }
        }
    }

    private void setStatusDot(View dot, boolean activeOrRecent) {
        if (activeOrRecent) {
            dot.setBackgroundResource(R.drawable.bg_status_dot_glow);
            return;
        }
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(ContextCompat.getColor(this, R.color.status_unknown));
        dot.setBackground(bg);
    }

    private boolean isConnectionActive(SavedConnection sc) {
        for (SshTerminalSession session : SshSessionRepository.listSessions()) {
            if (session.isConnected() && TextUtils.equals(session.getDisplayTitle(), sc.username + "@" + sc.host)) {
                return true;
            }
        }
        return false;
    }

    private boolean isConnectionRecent(SavedConnection sc) {
        return sc.lastConnectedAt > 0L && System.currentTimeMillis() - sc.lastConnectedAt <= RECENT_CONNECTION_MS;
    }

    private String buildConnectionMetaText(SavedConnection sc) {
        return sc.username + " · SSH · 端口 " + sc.port;
    }

    private String buildConnectionStatusText(SavedConnection sc) {
        if (isConnectionActive(sc)) {
            return getString(R.string.connected_now);
        }
        if (sc.lastConnectedAt <= 0L) {
            return getString(R.string.not_connected_yet);
        }
        String relative = formatRelativeTime(sc.lastConnectedAt);
        if (TextUtils.equals(relative, getString(R.string.relative_time_just_now))) {
            return getString(R.string.connected_just_now);
        }
        return getString(R.string.connected_relative_format, relative);
    }

    private void showConnectionOptionsMenu(View anchor, SavedConnection sc) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, MENU_FAVORITE, 0, sc.favorite ? R.string.remove_favorite_action : R.string.favorite_action);
        popup.getMenu().add(0, MENU_EDIT, 1, R.string.edit_action);
        popup.getMenu().add(0, MENU_DUPLICATE, 2, R.string.duplicate_action);
        popup.getMenu().add(0, MENU_DELETE, 3, R.string.delete_action);
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == MENU_FAVORITE) {
                toggleFavorite(sc);
                return true;
            } else if (id == MENU_EDIT) {
                showConnectionEditor(sc);
                return true;
            } else if (id == MENU_DUPLICATE) {
                duplicateConnection(sc);
                return true;
            } else if (id == MENU_DELETE) {
                deleteSavedConnection(sc);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void toggleFavorite(SavedConnection sc) {
        for (int i = 0; i < savedConnections.size(); i++) {
            if (TextUtils.equals(savedConnections.get(i).id, sc.id)) {
                savedConnections.set(i, savedConnections.get(i).withFavorite(!savedConnections.get(i).favorite));
                break;
            }
        }
        persistSavedConnections();
        renderConnectionsPanel();
    }

    private void duplicateConnection(SavedConnection source) {
        SavedConnection copy = new SavedConnection(
            UUID.randomUUID().toString(),
            source.host,
            source.port,
            source.username,
            source.password,
            source.label,
            0L,
            false
        );
        savedConnections.add(0, copy);
        selectedConnectionId = copy.id;
        persistSavedConnections();
        renderConnectionsPanel();
        toast(getString(R.string.connection_duplicated));
    }

    private void touchConnection(SavedConnection sc) {
        boolean updated = false;
        for (int i = 0; i < savedConnections.size(); i++) {
            if (TextUtils.equals(savedConnections.get(i).id, sc.id)) {
                SavedConnection touched = savedConnections.get(i).withLastConnectedAt(System.currentTimeMillis());
                savedConnections.set(i, touched);
                updated = true;
                break;
            }
        }
        if (updated) {
            persistSavedConnections();
        }
    }

    private void setupAppSettingsPanel() {
        showSettingsMain();
    }

    private void showSettingsMain() {
        settingsShowingSubpage = false;
        ViewGroup subContainer = (ViewGroup) panelSettings.findViewById(R.id.settings_sub_container);
        View backButton = panelSettings.findViewById(R.id.settings_back_button);
        TextView titleView = panelSettings.findViewById(R.id.settings_top_title);
        titleView.setText(R.string.nav_settings);
        backButton.setVisibility(View.GONE);
        subContainer.removeAllViews();

        View settingsView = getLayoutInflater().inflate(R.layout.view_settings_main, subContainer, false);

        // Wire up Font size SeekBar
        SeekBar fontSizeSeek = settingsView.findViewById(R.id.settings_font_size_seek);
        TextView fontSizeValue = settingsView.findViewById(R.id.settings_font_size_value);
        int currentFontSize = preferences.getInt(KEY_DEFAULT_FONT_SIZE, DEFAULT_TERMINAL_TEXT_SIZE_SP);
        fontSizeSeek.setProgress(currentFontSize - MIN_TERMINAL_TEXT_SIZE_SP);
        fontSizeValue.setText(getString(R.string.font_size_value_format, currentFontSize));
        fontSizeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int size = MIN_TERMINAL_TEXT_SIZE_SP + progress;
                fontSizeValue.setText(getString(R.string.font_size_value_format, size));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int size = MIN_TERMINAL_TEXT_SIZE_SP + seekBar.getProgress();
                preferences.edit().putInt(KEY_DEFAULT_FONT_SIZE, size).apply();
                applyFontSize(size);
            }
        });

        // Wire up Cursor style RadioGroup
        RadioGroup cursorGroup = settingsView.findViewById(R.id.settings_cursor_style_group);
        int currentCursor = preferences.getInt(KEY_CURSOR_STYLE, DEFAULT_CURSOR_STYLE);
        if (currentCursor == CURSOR_STYLE_BLOCK) {
            cursorGroup.check(R.id.settings_cursor_block);
        } else if (currentCursor == CURSOR_STYLE_UNDERLINE) {
            cursorGroup.check(R.id.settings_cursor_underline);
        } else {
            cursorGroup.check(R.id.settings_cursor_bar);
        }
        cursorGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int style;
            if (checkedId == R.id.settings_cursor_block) {
                style = CURSOR_STYLE_BLOCK;
            } else if (checkedId == R.id.settings_cursor_underline) {
                style = CURSOR_STYLE_UNDERLINE;
            } else {
                style = CURSOR_STYLE_BAR;
            }
            preferences.edit().putInt(KEY_CURSOR_STYLE, style).apply();
            // New sessions will pick this up; for the current session, ask the terminal to redraw.
            if (terminalView != null) {
                terminalView.onScreenUpdated();
            }
        });

        // Wire up Keep screen on switch
        MaterialSwitch keepScreenOn = settingsView.findViewById(R.id.settings_keep_screen_on);
        keepScreenOn.setChecked(preferences.getBoolean(KEY_KEEP_SCREEN_ON, DEFAULT_KEEP_SCREEN_ON));
        keepScreenOn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(KEY_KEEP_SCREEN_ON, isChecked).apply();
            if (terminalView != null) {
                terminalView.setKeepScreenOn(isChecked);
            }
        });

        // Wire up SSH Keys navigation & action buttons
        settingsView.findViewById(R.id.settings_keys_card).setOnClickListener(v -> showSettingsKeysSubpage());
        View genKeyBtn = settingsView.findViewById(R.id.settings_gen_key_btn);
        if (genKeyBtn != null) {
            genKeyBtn.setOnClickListener(v -> showSettingsKeysSubpage());
        }
        View importKeyBtn = settingsView.findViewById(R.id.settings_import_key_btn);
        if (importKeyBtn != null) {
            importKeyBtn.setOnClickListener(v -> filePickerLauncher.launch("*/*"));
        }

        // About section
        TextView aboutVersion = settingsView.findViewById(R.id.settings_about_version);
        TextView aboutDeps = settingsView.findViewById(R.id.settings_about_dependencies);
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            aboutVersion.setText(getString(R.string.about_version) + " " + versionName);
        } catch (Exception e) {
            aboutVersion.setText(getString(R.string.about_version));
        }
        aboutDeps.setText("sshj 0.39.0\nBouncyCastle 1.77\ntermux-terminal-emulator 0.118.0\nJetBrainsMono Nerd Font");

        subContainer.addView(settingsView);
    }

    private void showSettingsKeysSubpage() {
        settingsShowingSubpage = true;
        ViewGroup subContainer = (ViewGroup) panelSettings.findViewById(R.id.settings_sub_container);
        View backButton = panelSettings.findViewById(R.id.settings_back_button);
        TextView titleView = panelSettings.findViewById(R.id.settings_top_title);
        titleView.setText(R.string.settings_keys_section);
        backButton.setVisibility(View.VISIBLE);
        backButton.setOnClickListener(v -> showSettingsMain());
        subContainer.removeAllViews();

        View keysView = getLayoutInflater().inflate(R.layout.panel_keys, subContainer, false);
        keysView.findViewById(R.id.import_key_button).setOnClickListener(v -> toast(getString(R.string.key_import_coming_soon)));
        subContainer.addView(keysView);
    }

    private void applyFontSize(int sizeSp) {
        int clamped = Math.max(MIN_TERMINAL_TEXT_SIZE_SP, Math.min(sizeSp, MAX_TERMINAL_TEXT_SIZE_SP));
        terminalScaleFactor = clamped / (float) DEFAULT_TERMINAL_TEXT_SIZE_SP;
        if (terminalView != null) {
            terminalView.setTextSize(clamped);
        }
    }

    private String formatRelativeTime(long timestampMs) {
        long diffMs = System.currentTimeMillis() - timestampMs;
        if (diffMs < 60_000L) {
            return getString(R.string.relative_time_just_now);
        }
        long diffMinutes = diffMs / 60_000L;
        if (diffMinutes < 60) {
            return getString(R.string.relative_time_minutes_ago, (int) diffMinutes);
        }
        long diffHours = diffMinutes / 60L;
        if (diffHours < 24) {
            return getString(R.string.relative_time_hours_ago, (int) diffHours);
        }
        long diffDays = diffHours / 24L;
        if (diffDays == 1) {
            return getString(R.string.relative_time_yesterday);
        }
        return getString(R.string.relative_time_days_ago, (int) diffDays);
    }

    private void showConnectionPickerDialog() {
        if (savedConnections.isEmpty()) {
            switchToPanel(R.id.nav_connections);
            toast(getString(R.string.error_add_connection_first));
            return;
        }

        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
            new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.view_bottom_sheet_actions, null, false);
        ((TextView) sheetView.findViewById(R.id.sheet_title)).setText(R.string.connection_picker_title);
        ViewGroup actions = sheetView.findViewById(R.id.sheet_actions_container);

        for (SavedConnection sc : savedConnections) {
            View row = createSheetRow(
                R.drawable.ic_server,
                sc.getDisplayName(),
                sc.getDisplayDetails(),
                isConnectionActive(sc),
                v -> {
                    sheet.dismiss();
                    createNewTab(true);
                    connect(sc);
                }
            );
            actions.addView(row);
        }
        actions.addView(createSheetRow(
            R.drawable.ic_add,
            getString(R.string.new_connection_option),
            null,
            false,
            v -> {
                sheet.dismiss();
                switchToPanel(R.id.nav_connections);
                showConnectionEditor(null);
            }
        ));

        sheet.setContentView(sheetView);
        sheet.show();
    }

    private View createSheetRow(int iconRes, String title, String subtitle, boolean active, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        row.setBackgroundResource(tv.resourceId);
        row.setOnClickListener(listener);

        androidx.appcompat.widget.AppCompatImageView icon = new androidx.appcompat.widget.AppCompatImageView(this);
        icon.setImageResource(iconRes);
        int iconSize = dp(22);
        icon.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
        ImageViewCompat.setImageTintList(icon, android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.text_muted_on_dark)));

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(dp(14), 0, 0, 0);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(ContextCompat.getColor(this, R.color.text_on_dark));
        titleView.setTextSize(15f);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textCol.addView(titleView);

        if (subtitle != null) {
            TextView subtitleView = new TextView(this);
            subtitleView.setText(subtitle);
            subtitleView.setTextColor(ContextCompat.getColor(this, R.color.text_muted_on_dark));
            subtitleView.setTextSize(12f);
            subtitleView.setSingleLine(true);
            subtitleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            textCol.addView(subtitleView);
        }

        if (active) {
            View dot = new View(this);
            int dotSize = dp(8);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dotSize, dotSize);
            dotParams.leftMargin = dp(8);
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(ContextCompat.getColor(this, R.color.status_connected));
            dot.setBackground(dotBg);
            dot.setLayoutParams(dotParams);
            row.addView(icon);
            row.addView(textCol);
            row.addView(dot);
        } else {
            row.addView(icon);
            row.addView(textCol);
        }
        return row;
    }

    private View createShortcutKeyButton(String label, boolean isModifier) {
        TextView btn = new TextView(this);
        btn.setText(label);
        btn.setTextSize(getResources().getDimension(R.dimen.shortcut_key_text_size) / getResources().getDisplayMetrics().scaledDensity);
        btn.setIncludeFontPadding(false);
        btn.setTextColor(ContextCompat.getColor(this, R.color.terminal_text));
        btn.setGravity(Gravity.CENTER);
        btn.setMinHeight((int) getResources().getDimension(R.dimen.shortcut_key_height));
        btn.setPadding(dp(10), dp(6), dp(10), dp(6));
        btn.setBackgroundResource(isModifier ? R.drawable.bg_shortcut_key_modifier : R.drawable.bg_shortcut_key);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        int margin = (int) getResources().getDimension(R.dimen.shortcut_key_spacing);
        lp.setMargins(margin, 0, margin, 0);
        btn.setLayoutParams(lp);

        btn.setFocusable(false);
        btn.setFocusableInTouchMode(false);

        btn.setOnClickListener(v -> onShortcutKeyPressed(label, isModifier));
        return btn;
    }

    private void onShortcutKeyPressed(String label, boolean isModifier) {
        if (terminalView != null) {
            terminalView.requestFocus();
        }

        SshTerminalSession session = getActiveSession();
        if (session == null && sshTerminalSession == null) return;
        SshTerminalSession active = session != null ? session : sshTerminalSession;

        switch (label) {
            case "Ctrl":
                controlKeyActive = !controlKeyActive;
                ctrlButton.setBackgroundResource(controlKeyActive
                    ? R.drawable.bg_shortcut_key_modifier_active : R.drawable.bg_shortcut_key_modifier);
                return;
            case "Alt":
                altKeyActive = !altKeyActive;
                altButton.setBackgroundResource(altKeyActive
                    ? R.drawable.bg_shortcut_key_modifier_active : R.drawable.bg_shortcut_key_modifier);
                return;
            case "Esc":
                active.sendEscape();
                return;
            case "Tab":
                if (terminalView != null) terminalView.inputCodePoint(9, false, false);
                return;
            case "↑":
                sendArrowKey(KeyEvent.KEYCODE_DPAD_UP);
                return;
            case "↓":
                sendArrowKey(KeyEvent.KEYCODE_DPAD_DOWN);
                return;
            case "←":
                sendArrowKey(KeyEvent.KEYCODE_DPAD_LEFT);
                return;
            case "→":
                sendArrowKey(KeyEvent.KEYCODE_DPAD_RIGHT);
                return;
            case "Ctrl+T":
                createNewTab(true);
                break;
            case "Ctrl+W":
                if (activeSessionId != null) {
                    closeTab(activeSessionId);
                }
                break;
            case "Ctrl+←":
                switchToAdjacentTab(-1);
                break;
            case "Ctrl+→":
                switchToAdjacentTab(1);
                break;
            case "Ctrl+C":
                active.writeCodePoint(false, 3);
                break;
            case "Ctrl+D":
                active.writeCodePoint(false, 4);
                break;
            case "Ctrl+Z":
                active.writeCodePoint(false, 26);
                break;
            case "Ctrl+L":
                active.writeCodePoint(false, 12);
                break;
            case "Ctrl+R":
                active.writeCodePoint(false, 18);
                break;
            case "Ctrl+A":
                active.writeCodePoint(false, 1);
                break;
            case "Ctrl+E":
                active.writeCodePoint(false, 5);
                break;
            case "⚡ htop":
                if (active != null) active.write("htop\n");
                break;
            case "🐳 docker":
                if (active != null) active.write("docker ps\n");
                break;
            case "🌿 git":
                if (active != null) active.write("git status\n");
                break;
            case "🧹 clear":
                if (active != null) active.write("clear\n");
                break;
            default:
                // Regular character key
                if (terminalView != null) {
                    terminalView.inputCodePoint(label.charAt(0), false, false);
                }
                break;
        }

        // Reset modifier states after sending a key
        if (!isModifier) {
            controlKeyActive = false;
            altKeyActive = false;
            ctrlButton.setBackgroundResource(R.drawable.bg_shortcut_key_modifier);
            altButton.setBackgroundResource(R.drawable.bg_shortcut_key_modifier);
        }
    }

    private void sendArrowKey(int keyCode) {
        if (terminalView == null) return;
        KeyEvent down = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
        terminalView.onKeyDown(keyCode, down);
        KeyEvent up = new KeyEvent(KeyEvent.ACTION_UP, keyCode);
        terminalView.onKeyUp(keyCode, up);
    }

    private void connect(@Nullable SavedConnection savedConnection) {
        if (savedConnection == null) {
            toast(getString(R.string.saved_connections_empty));
            return;
        }
        connect(savedConnection.toConfig(), savedConnection.id);
    }

    private void connect(SshConnectionConfig config, @Nullable String selectedId) {
        selectedConnectionId = selectedId;
        persistSelectedConnectionId();
        switchToPanel(R.id.nav_terminal);
        SshTerminalSession targetSession = getReusableSessionForConnect();
        if (targetSession == null) {
            targetSession = createNewTab(true);
        } else {
            switchToTab(targetSession.getSessionId());
        }
        setStatusText(getString(R.string.status_connecting));
        targetSession.connect(config);

        // Update lastConnectedAt on the saved connection (if any) so it moves to the top of the list.
        SavedConnection saved = findSavedConnectionById(selectedId);
        if (saved != null) {
            touchConnection(saved);
        }

        if (terminalView != null) {
            terminalView.requestFocus();
        }
    }

    private boolean shouldRoutePhysicalKeyboardToTerminal() {
        return terminalView != null && activePanelId == R.id.nav_terminal;
    }

    private boolean handleTerminalZoomShortcut(KeyEvent event) {
        if (!event.isCtrlPressed()) {
            return false;
        }

        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_EQUALS:
            case KeyEvent.KEYCODE_PLUS:
            case KeyEvent.KEYCODE_NUMPAD_ADD:
                stepTerminalFontSize(1);
                return true;
            case KeyEvent.KEYCODE_MINUS:
            case KeyEvent.KEYCODE_NUMPAD_SUBTRACT:
                stepTerminalFontSize(-1);
                return true;
            case KeyEvent.KEYCODE_0:
            case KeyEvent.KEYCODE_NUMPAD_0:
                setTerminalFontSize(DEFAULT_TERMINAL_TEXT_SIZE_SP);
                return true;
            default:
                return false;
        }
    }

    private boolean handleCtrlTabShortcut(KeyEvent event) {
        if (!event.isCtrlPressed()) {
            return false;
        }

        boolean isShift = event.isShiftPressed();

        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_B:
                if (isShift) {
                    // Ctrl+Shift+B toggles sidebar, leaving Ctrl+B exclusively for tmux / emacs
                    toggleSidebar();
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_T:
                if (isShift) {
                    showConnectionPickerDialog();
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_W:
                if (isShift) {
                    if (activeSessionId != null) {
                        closeTab(activeSessionId);
                    }
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_PAGE_UP:
                switchToAdjacentTab(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                switchToAdjacentTab(1);
                return true;
            default:
                return false;
        }
    }

    private void switchToAdjacentTab(int direction) {
        if (tabSessionIds.size() <= 1) return;
        int currentIndex = tabSessionIds.indexOf(activeSessionId);
        if (currentIndex < 0) return;
        int newIndex = (currentIndex + direction + tabSessionIds.size()) % tabSessionIds.size();
        switchToTab(tabSessionIds.get(newIndex));
    }

    private void stepTerminalFontSize(int delta) {
        int currentSize = Math.round(DEFAULT_TERMINAL_TEXT_SIZE_SP * terminalScaleFactor);
        setTerminalFontSize(currentSize + delta);
    }

    private void setTerminalFontSize(int textSizeSp) {
        int clampedTextSize = Math.max(MIN_TERMINAL_TEXT_SIZE_SP, Math.min(textSizeSp, MAX_TERMINAL_TEXT_SIZE_SP));
        terminalScaleFactor = clampedTextSize / (float) DEFAULT_TERMINAL_TEXT_SIZE_SP;
        if (terminalView != null) {
            terminalView.setTextSize(clampedTextSize);
        }
    }

    private void applyCompactPointerIcon() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
            return;
        }

        PointerIcon pointerIcon = PointerIcon.getSystemIcon(this, PointerIcon.TYPE_TEXT);
        terminalView.setPointerIcon(pointerIcon);
        binding.panelTerminal.terminalContainer.setPointerIcon(pointerIcon);
    }

    private void restoreOrCreateTabs() {
        tabSessionIds.clear();
        for (SshTerminalSession session : SshSessionRepository.listSessions()) {
            tabSessionIds.add(session.getSessionId());
            SshSessionRepository.attachUi(session, this, this);
        }

        if (tabSessionIds.isEmpty()) {
            createNewTab(true);
        } else {
            activeSessionId = tabSessionIds.get(0);
            switchToTab(activeSessionId);
            renderTabs();
        }
    }

    @NonNull
    private SshTerminalSession createNewTab(boolean switchToNewTab) {
        SshTerminalSession session = SshSessionRepository.create(this, this);
        String savedTheme = preferences.getString(KEY_TERMINAL_THEME, DEFAULT_TERMINAL_THEME);
        com.example.androidterminal.terminalview.TerminalTheme currentTheme = com.example.androidterminal.terminalview.TerminalThemeManager.getTheme(savedTheme);
        if (session.getEmulator() != null) {
            com.example.androidterminal.terminalview.TerminalThemeManager.applyTheme(session.getEmulator(), currentTheme);
        }
        tabSessionIds.add(session.getSessionId());
        if (switchToNewTab) {
            switchToTab(session.getSessionId());
        } else {
            renderTabs();
        }
        return session;
    }

    private void switchToTab(@Nullable String sessionId) {
        SshTerminalSession session = SshSessionRepository.findById(sessionId);
        if (session == null) {
            return;
        }

        activeSessionId = sessionId;
        sshTerminalSession = session;
        SshSessionRepository.attachUi(session, this, this);
        terminalView.attachSession(session);
        if (fileBrowserDrawer != null && fileBrowserDrawer.isOpen()) {
            fileBrowserDrawer.close();
        }
        syncUiWithActiveSession();
        renderTabs();
        terminalView.requestFocus();
    }

    @Nullable
    private SshTerminalSession getReusableSessionForConnect() {
        SshTerminalSession activeSession = getActiveSession();
        if (activeSession == null) {
            return null;
        }
        return activeSession.isConnected() ? null : activeSession;
    }

    @Nullable
    private SshTerminalSession getActiveSession() {
        return SshSessionRepository.findById(activeSessionId);
    }

    private void closeTab(@NonNull String sessionId) {
        SshTerminalSession session = SshSessionRepository.findById(sessionId);
        if (session == null) {
            return;
        }

        SshSessionRepository.disconnectAndRemove(session, "Closed");
        tabSessionIds.remove(sessionId);

        if (tabSessionIds.isEmpty()) {
            createNewTab(true);
            return;
        }

        if (TextUtils.equals(activeSessionId, sessionId)) {
            switchToTab(tabSessionIds.get(Math.max(0, tabSessionIds.size() - 1)));
        } else {
            renderTabs();
            syncUiWithActiveSession();
        }
    }

    private void renderTabs() {
        binding.panelTerminal.tabsContainer.removeAllViews();
        for (String sessionId : tabSessionIds) {
            SshTerminalSession session = SshSessionRepository.findById(sessionId);
            if (session == null) {
                continue;
            }

            View tabView = getLayoutInflater().inflate(R.layout.item_terminal_tab, binding.panelTerminal.tabsContainer, false);
            android.widget.LinearLayout cardView = tabView.findViewById(R.id.terminal_tab_card);
            android.widget.TextView titleView = tabView.findViewById(R.id.terminal_tab_title);
            AppCompatImageButton closeButton = tabView.findViewById(R.id.close_tab_button);

            boolean active = TextUtils.equals(activeSessionId, sessionId);
            cardView.setBackgroundResource(active ? R.drawable.bg_terminal_tab_active : R.drawable.bg_terminal_tab);
            titleView.setTextColor(ContextCompat.getColor(this, active ? R.color.tab_text_active : R.color.terminal_text));
            View tabDot = tabView.findViewById(R.id.terminal_tab_dot);
            if (tabDot != null) {
                tabDot.setVisibility(active ? View.VISIBLE : View.GONE);
            }
            ImageViewCompat.setImageTintList(
                closeButton,
                android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, active ? R.color.tab_text_active : R.color.terminal_text_muted))
            );
            titleView.setText(session.getDisplayTitle());

            cardView.setOnClickListener(v -> switchToTab(sessionId));
            closeButton.setOnClickListener(v -> closeTab(sessionId));
            binding.panelTerminal.tabsContainer.addView(tabView);
        }
    }

    private void syncUiWithActiveSession() {
        SshTerminalSession activeSession = getActiveSession();
        boolean connected = activeSession != null && activeSession.isConnected();
        updateConnectionActions(connected);
        updateTerminalEmptyOverlay();

        TextView termSessionInfo = binding.panelTerminal.termSessionInfo;
        if (termSessionInfo != null) {
            if (connected && activeSession != null) {
                termSessionInfo.setText("会话: " + activeSession.getDisplayTitle() + " • UTF-8");
            } else {
                termSessionInfo.setText("会话: 就绪 • UTF-8");
            }
        }

        TextView connBadge = binding.getRoot().findViewById(R.id.sidebar_connections_badge);
        if (connBadge != null) {
            connBadge.setText(String.valueOf(savedConnections.size()));
        }
        TextView termBadge = binding.getRoot().findViewById(R.id.sidebar_terminal_badge);
        if (termBadge != null) {
            termBadge.setText(connected ? "1 在线" : "就绪");
        }

        if (activeSession == null) {
            setStatusText(getString(R.string.status_idle));
            SshConnectionService.stop(this);
            return;
        }

        setStatusText(connected ? activeSession.getDisplayTitle() : getString(R.string.status_idle));
        if (connected) {
            SshConnectionService.start(this);
        } else if (!SshSessionRepository.hasConnectedSessions()) {
            SshConnectionService.stop(this);
        }
    }

    private void updateTerminalEmptyOverlay() {
        View overlay = binding.panelTerminal.terminalEmptyOverlay;
        if (overlay == null) {
            return;
        }
        boolean hasAnyConnection = savedConnections.isEmpty();
        boolean anyActive = false;
        for (SshTerminalSession s : SshSessionRepository.listSessions()) {
            if (s.isConnected()) {
                anyActive = true;
                break;
            }
        }
        overlay.setVisibility(hasAnyConnection && !anyActive ? View.VISIBLE : View.GONE);
    }

    private void updateConnectionActions(boolean connected) {
    }

    @Nullable
    private SshConnectionConfig buildConnectionConfig(String host, String portValue, String username, String password) {
        if (TextUtils.isEmpty(host) || TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            toast(getString(R.string.missing_connection_info));
            return null;
        }

        int port = 22;
        if (!TextUtils.isEmpty(portValue)) {
            try {
                port = Integer.parseInt(portValue);
            } catch (NumberFormatException e) {
                toast(getString(R.string.invalid_port));
                return null;
            }
        }

        return new SshConnectionConfig(host, port, username, password);
    }

    private void showConnectionEditor(@Nullable SavedConnection existingConnection) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_connection_editor, binding.getRoot(), false);
        android.widget.TextView titleView = dialogView.findViewById(R.id.dialog_title);
        TextInputEditText labelInput = dialogView.findViewById(R.id.dialog_label_input);
        TextInputEditText hostInput = dialogView.findViewById(R.id.dialog_host_input);
        TextInputEditText portInput = dialogView.findViewById(R.id.dialog_port_input);
        TextInputEditText usernameInput = dialogView.findViewById(R.id.dialog_username_input);
        TextInputEditText passwordInput = dialogView.findViewById(R.id.dialog_password_input);
        MaterialButton connectButton = dialogView.findViewById(R.id.dialog_connect_button);
        MaterialButton cancelButton = dialogView.findViewById(R.id.dialog_cancel_button);
        MaterialButton saveButton = dialogView.findViewById(R.id.dialog_save_button);

        titleView.setText(existingConnection == null ? R.string.new_connection_title : R.string.edit_connection_title);
        saveButton.setText(existingConnection == null ? R.string.save_connection_action : R.string.update_connection_action);

        if (existingConnection != null) {
            labelInput.setText(existingConnection.label);
            hostInput.setText(existingConnection.host);
            portInput.setText(String.valueOf(existingConnection.port));
            usernameInput.setText(existingConnection.username);
            passwordInput.setText(existingConnection.password);
        } else {
            portInput.setText("22");
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create();
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
        });
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        saveButton.setOnClickListener(v -> {
            SshConnectionConfig config = buildConnectionConfig(
                valueOf(hostInput.getText()),
                valueOf(portInput.getText()),
                valueOf(usernameInput.getText()),
                valueOf(passwordInput.getText())
            );
            if (config == null) {
                return;
            }

            upsertSavedConnection(config, valueOf(labelInput.getText()), existingConnection != null ? existingConnection.id : null, true);
            dialog.dismiss();
        });
        connectButton.setOnClickListener(v -> {
            SshConnectionConfig config = buildConnectionConfig(
                valueOf(hostInput.getText()),
                valueOf(portInput.getText()),
                valueOf(usernameInput.getText()),
                valueOf(passwordInput.getText())
            );
            if (config == null) {
                return;
            }

            new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.save_before_connect_title)
                .setMessage(R.string.save_before_connect_message)
                .setNegativeButton(R.string.connect_without_saving_action, (confirmDialog, which) -> {
                    dialog.dismiss();
                    connect(config, existingConnection != null ? existingConnection.id : selectedConnectionId);
                })
                .setNeutralButton(R.string.cancel_action, null)
                .setPositiveButton(R.string.save_and_connect_action, (confirmDialog, which) -> {
                    SavedConnection savedConnection = upsertSavedConnection(
                        config,
                        valueOf(labelInput.getText()),
                        existingConnection != null ? existingConnection.id : null,
                        true
                    );
                    dialog.dismiss();
                    connect(savedConnection);
                })
                .show();
        });
        dialog.show();
    }

    private SavedConnection upsertSavedConnection(SshConnectionConfig config, String label, @Nullable String preferredConnectionId, boolean showToast) {
        SavedConnection existingConnection = findMatchingConnection(config.getHost(), config.getPort(), config.getUsername());
        String connectionId = !TextUtils.isEmpty(preferredConnectionId)
            ? preferredConnectionId
            : existingConnection != null ? existingConnection.id : UUID.randomUUID().toString();
        // Preserve UI metadata when updating an existing connection.
        long existingLastConnected = 0L;
        boolean existingFavorite = false;
        for (SavedConnection sc : savedConnections) {
            if (TextUtils.equals(sc.id, connectionId)) {
                existingLastConnected = sc.lastConnectedAt;
                existingFavorite = sc.favorite;
                break;
            }
        }
        SavedConnection savedConnection = new SavedConnection(
            connectionId,
            config.getHost(),
            config.getPort(),
            config.getUsername(),
            config.getPassword(),
            label,
            existingLastConnected,
            existingFavorite
        );

        boolean updated = false;
        for (int i = 0; i < savedConnections.size(); i++) {
            if (TextUtils.equals(savedConnections.get(i).id, connectionId)) {
                savedConnections.set(i, savedConnection);
                updated = true;
                break;
            }
        }

        if (!updated) {
            savedConnections.add(0, savedConnection);
        }

        selectedConnectionId = connectionId;
        persistSavedConnections();
        renderSavedConnections();
        if (showToast) {
            toast(getString(updated ? R.string.connection_updated : R.string.connection_saved));
        }
        return savedConnection;
    }

    private void restoreSavedConnections() {
        loadSavedConnections();
        migrateLegacyConnectionDraftIfNeeded();

        SavedConnection selectedConnection = findSavedConnectionById(preferences.getString(KEY_SELECTED_CONNECTION_ID, null));
        if (selectedConnection == null && !savedConnections.isEmpty()) {
            selectedConnection = savedConnections.get(0);
        }

        if (selectedConnection != null) {
            selectedConnectionId = selectedConnection.id;
        } else {
            selectedConnectionId = null;
        }

        persistSelectedConnectionId();
        renderSavedConnections();
    }

    private void loadSavedConnections() {
        savedConnections.clear();
        String savedConnectionsJson = preferences.getString(KEY_SAVED_CONNECTIONS, "[]");
        if (TextUtils.isEmpty(savedConnectionsJson) || "[]".equals(savedConnectionsJson)) {
            migrateLegacyConnectionDraftIfNeeded();
            return;
        }

        try {
            JSONArray jsonArray = new JSONArray(savedConnectionsJson);
            boolean cleanedMock = false;
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.optJSONObject(i);
                SavedConnection savedConnection = SavedConnection.fromJson(jsonObject);
                if (savedConnection != null) {
                    // Filter out legacy hardcoded sample connections
                    if ("huangsheng".equals(savedConnection.username)
                            || "HP Home Server".equals(savedConnection.label)
                            || "Aliyun Prod Gateway".equals(savedConnection.label)
                            || "Mac Studio M2".equals(savedConnection.label)
                            || "192.168.5.115".equals(savedConnection.host)
                            || "192.168.5.88".equals(savedConnection.host)
                            || "47.98.120.45".equals(savedConnection.host)) {
                        cleanedMock = true;
                        continue;
                    }
                    savedConnections.add(savedConnection);
                }
            }
            if (cleanedMock) {
                if (savedConnections.isEmpty()) {
                    selectedConnectionId = null;
                }
                persistSavedConnections();
            }
        } catch (JSONException e) {
            Log.w(INPUT_LOG_TAG, "Failed to load saved connections", e);
        }
    }

    private void migrateLegacyConnectionDraftIfNeeded() {
        if (!savedConnections.isEmpty()) {
            return;
        }

        String host = preferences.getString(KEY_HOST, "");
        String username = preferences.getString(KEY_USERNAME, "");
        String password = preferences.getString(KEY_PASSWORD, "");
        String portValue = preferences.getString(KEY_PORT, "22");
        if (!TextUtils.isEmpty(host) && !TextUtils.isEmpty(username)) {
            int port = 22;
            try {
                port = Integer.parseInt(portValue);
            } catch (NumberFormatException ignored) {
            }

            SavedConnection legacyConnection = new SavedConnection(
                UUID.randomUUID().toString(),
                host,
                port,
                username,
                password,
                "",
                System.currentTimeMillis(),
                false
            );
            savedConnections.add(legacyConnection);
            selectedConnectionId = legacyConnection.id;
            persistSavedConnections();
        }
    }

    private void persistSavedConnections() {
        JSONArray jsonArray = new JSONArray();
        for (SavedConnection savedConnection : savedConnections) {
            jsonArray.put(savedConnection.toJson());
        }

        preferences.edit()
            .putString(KEY_SAVED_CONNECTIONS, jsonArray.toString())
            .putString(KEY_SELECTED_CONNECTION_ID, selectedConnectionId)
            .apply();
    }

    private void persistSelectedConnectionId() {
        preferences.edit()
            .putString(KEY_SELECTED_CONNECTION_ID, selectedConnectionId)
            .apply();
    }

    private void renderSavedConnections() {
        renderConnectionsPanel();
        updateTerminalEmptyOverlay();
    }

    private void deleteSavedConnection(SavedConnection savedConnection) {
        for (int i = 0; i < savedConnections.size(); i++) {
            if (TextUtils.equals(savedConnections.get(i).id, savedConnection.id)) {
                savedConnections.remove(i);
                break;
            }
        }

        if (TextUtils.equals(selectedConnectionId, savedConnection.id)) {
            if (savedConnections.isEmpty()) {
                selectedConnectionId = null;
            } else {
                selectedConnectionId = savedConnections.get(0).id;
            }
        }

        persistSavedConnections();
        renderSavedConnections();
        toast(getString(R.string.connection_deleted));
    }

    private SavedConnection findMatchingConnection(String host, int port, String username) {
        for (SavedConnection savedConnection : savedConnections) {
            if (savedConnection.port == port
                && TextUtils.equals(savedConnection.host, host)
                && TextUtils.equals(savedConnection.username, username)) {
                return savedConnection;
            }
        }
        return null;
    }

    private SavedConnection findSavedConnectionById(String connectionId) {
        if (TextUtils.isEmpty(connectionId)) {
            return null;
        }

        for (SavedConnection savedConnection : savedConnections) {
            if (TextUtils.equals(savedConnection.id, connectionId)) {
                return savedConnection;
            }
        }
        return null;
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private void setStatusText(String text) {
        binding.statusText.setText(text);
        TextView sidebar = binding.getRoot().findViewById(R.id.sidebar_status_text);
        if (sidebar != null) {
            sidebar.setText(text);
        }
    }

    private void showPasteMenu(MotionEvent event) {
        View anchor = new View(this);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(1, 1);
        layoutParams.leftMargin = Math.max(0, Math.min(Math.round(event.getX()), Math.max(0, binding.panelTerminal.terminalContainer.getWidth() - 1)));
        layoutParams.topMargin = Math.max(0, Math.min(Math.round(event.getY()), Math.max(0, binding.panelTerminal.terminalContainer.getHeight() - 1)));
        binding.panelTerminal.terminalContainer.addView(anchor, layoutParams);

        PopupMenu popupMenu = new PopupMenu(this, anchor, Gravity.NO_GRAVITY);
        popupMenu.getMenu().add(getString(R.string.paste_text)).setEnabled(hasClipboardText());
        popupMenu.setOnMenuItemClickListener(item -> {
            pasteFromClipboard();
            return true;
        });
        popupMenu.setOnDismissListener(menu -> binding.panelTerminal.terminalContainer.removeView(anchor));
        popupMenu.show();
    }

    private boolean hasClipboardText() {
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        return clipboardManager != null && clipboardManager.hasPrimaryClip()
            && clipboardManager.getPrimaryClip() != null
            && clipboardManager.getPrimaryClip().getItemCount() > 0
            && !TextUtils.isEmpty(clipboardManager.getPrimaryClip().getItemAt(0).coerceToText(this));
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private String valueOf(CharSequence text) {
        return text == null ? "" : text.toString().trim();
    }

    @Override
    public void onScreenUpdated(@NonNull SshTerminalSession session) {
        if (TextUtils.equals(activeSessionId, session.getSessionId())) {
            terminalView.onScreenUpdated();
        }
    }

    @Override
    public void onSessionTitleChanged(@NonNull SshTerminalSession session, String title) {
        renderTabs();
        if (TextUtils.equals(activeSessionId, session.getSessionId()) && !TextUtils.isEmpty(title)) {
            setStatusText(title);
        }
    }

    @Override
    public void onConnected(@NonNull SshTerminalSession session) {
        renderTabs();
        if (TextUtils.equals(activeSessionId, session.getSessionId())) {
            setStatusText(session.getDisplayTitle());
            updateConnectionActions(true);
            terminalView.post(terminalView::requestFocus);
        }
        if (fileBrowserDrawer != null) {
            fileBrowserDrawer.setSession(session);
            if (fileBrowserDrawer.isOpen()) {
                fileBrowserDrawer.refresh();
            }
        }
        SshConnectionService.start(this);
    }

    @Override
    public void onDisconnected(@NonNull SshTerminalSession session, String message) {
        renderTabs();
        if (TextUtils.equals(activeSessionId, session.getSessionId())) {
            setStatusText(message);
            updateConnectionActions(false);
        }
        if (!SshSessionRepository.hasConnectedSessions()) {
            SshConnectionService.stop(this);
        }
    }

    @Override
    public void copyToClipboard(String text) {
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("terminal-copy", text));
            toast(getString(R.string.clipboard_copied));
        }
    }

    @Override
    public void pasteFromClipboard() {
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager == null || !clipboardManager.hasPrimaryClip()) {
            toast(getString(R.string.clipboard_empty));
            return;
        }

        ClipData clipData = clipboardManager.getPrimaryClip();
        if (clipData == null || clipData.getItemCount() == 0) {
            toast(getString(R.string.clipboard_empty));
            return;
        }

        CharSequence pasteText = clipData.getItemAt(0).coerceToText(this);
        if (TextUtils.isEmpty(pasteText)) {
            toast(getString(R.string.clipboard_empty));
            return;
        }

        if (sshTerminalSession != null) {
            sshTerminalSession.pasteText(pasteText.toString());
        }
    }

    @Override
    public float onScale(float scale) {
        float clampedScale = Math.max(
            MIN_TERMINAL_TEXT_SIZE_SP / (float) DEFAULT_TERMINAL_TEXT_SIZE_SP,
            Math.min(scale, MAX_TERMINAL_TEXT_SIZE_SP / (float) DEFAULT_TERMINAL_TEXT_SIZE_SP)
        );
        terminalScaleFactor = clampedScale;
        setTerminalFontSize(Math.round(DEFAULT_TERMINAL_TEXT_SIZE_SP * clampedScale));
        return terminalScaleFactor;
    }

    @Override
    public void onSingleTapUp(MotionEvent e) {
        terminalView.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    @Override
    public boolean shouldBackButtonBeMappedToEscape() {
        return false;
    }

    @Override
    public boolean shouldEnforceCharBasedInput() {
        return true;
    }

    @Override
    public boolean shouldUseCtrlSpaceWorkaround() {
        return false;
    }

    @Override
    public boolean isTerminalViewSelected() {
        return terminalView != null && terminalView.hasFocus();
    }

    @Override
    public void copyModeChanged(boolean copyMode) {
        // No-op.
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e, com.example.androidterminal.ssh.SshTerminalSession session) {
        if (session == null) {
            return false;
        }
        Log.d(INPUT_LOG_TAG, "TerminalViewClient.onKeyDown keyCode=" + keyCode + " ctrl=" + e.isCtrlPressed() + " alt=" + e.isAltPressed());
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            session.sendEscape();
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent e) {
        return keyCode == KeyEvent.KEYCODE_ESCAPE;
    }

    @Override
    public boolean onLongPress(MotionEvent event) {
        return false;
    }

    @Override
    public boolean readControlKey() {
        return controlKeyActive;
    }

    @Override
    public boolean readAltKey() {
        return altKeyActive;
    }

    @Override
    public boolean readShiftKey() {
        return false;
    }

    @Override
    public boolean readFnKey() {
        return false;
    }

    @Override
    public boolean onCodePoint(int codePoint, boolean ctrlDown, com.example.androidterminal.ssh.SshTerminalSession session) {
        Log.d(INPUT_LOG_TAG, "TerminalViewClient.onCodePoint codePoint=" + codePoint + " ctrl=" + ctrlDown);
        return false;
    }

    @Override
    public void onEmulatorSet() {
        terminalView.setTerminalCursorBlinkerState(true, true);
        terminalView.onScreenUpdated();
    }

    @Override
    public void onMouseSecondaryClick(MotionEvent event, com.example.androidterminal.ssh.SshTerminalSession session) {
        if (terminalView != null && terminalView.isSelectingText()) {
            terminalView.stopTextSelectionMode(true);
        }
        showPasteMenu(event);
    }

    @Override
    public void logError(String tag, String message) {
    }

    @Override
    public void logWarn(String tag, String message) {
    }

    @Override
    public void logInfo(String tag, String message) {
    }

    @Override
    public void logDebug(String tag, String message) {
    }

    @Override
    public void logVerbose(String tag, String message) {
    }

    @Override
    public void logStackTraceWithMessage(String tag, String message, Exception e) {
    }

    @Override
    public void logStackTrace(String tag, Exception e) {
    }

    @Override
    public void onTextChanged(@NonNull TerminalSession changedSession) {
    }

    @Override
    public void onTitleChanged(@NonNull TerminalSession changedSession) {
    }

    @Override
    public void onSessionFinished(@NonNull TerminalSession finishedSession) {
    }

    @Override
    public void onCopyTextToClipboard(@NonNull TerminalSession session, String text) {
    }

    @Override
    public void onPasteTextFromClipboard(TerminalSession session) {
    }

    @Override
    public void onBell(@NonNull TerminalSession session) {
    }

    @Override
    public void onColorsChanged(@NonNull TerminalSession session) {
    }

    @Override
    public void onTerminalCursorStateChange(boolean state) {
        if (terminalView != null) {
            terminalView.setTerminalCursorBlinkerState(state, true);
        }
    }

    public void setTerminalShellPid(@NonNull TerminalSession session, int pid) {
    }

    @Override
    public Integer getTerminalCursorStyle() {
        int style = preferences.getInt(KEY_CURSOR_STYLE, DEFAULT_CURSOR_STYLE);
        switch (style) {
            case CURSOR_STYLE_UNDERLINE:
                return TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE;
            case CURSOR_STYLE_BAR:
                return TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR;
            case CURSOR_STYLE_BLOCK:
            default:
                return TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK;
        }
    }

    private static final class SavedConnection {
        private static final String JSON_ID = "id";
        private static final String JSON_HOST = "host";
        private static final String JSON_PORT = "port";
        private static final String JSON_USERNAME = "username";
        private static final String JSON_PASSWORD = "password";
        private static final String JSON_LABEL = "label";
        private static final String JSON_LAST_CONNECTED_AT = "last_connected_at";
        private static final String JSON_FAVORITE = "favorite";

        private final String id;
        private final String host;
        private final int port;
        private final String username;
        private final String password;
        final String label;
        final long lastConnectedAt;
        final boolean favorite;

        private SavedConnection(String id, String host, int port, String username, String password, String label, long lastConnectedAt, boolean favorite) {
            this.id = id;
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = password;
            this.label = label == null ? "" : label;
            this.lastConnectedAt = lastConnectedAt;
            this.favorite = favorite;
        }

        private JSONObject toJson() {
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put(JSON_ID, id);
                jsonObject.put(JSON_HOST, host);
                jsonObject.put(JSON_PORT, port);
                jsonObject.put(JSON_USERNAME, username);
                jsonObject.put(JSON_PASSWORD, password);
                if (!TextUtils.isEmpty(label)) {
                    jsonObject.put(JSON_LABEL, label);
                }
                if (lastConnectedAt > 0) {
                    jsonObject.put(JSON_LAST_CONNECTED_AT, lastConnectedAt);
                }
                if (favorite) {
                    jsonObject.put(JSON_FAVORITE, true);
                }
            } catch (JSONException ignored) {
            }
            return jsonObject;
        }

        private static SavedConnection fromJson(JSONObject jsonObject) {
            if (jsonObject == null) {
                return null;
            }

            String id = jsonObject.optString(JSON_ID, "");
            String host = jsonObject.optString(JSON_HOST, "");
            String username = jsonObject.optString(JSON_USERNAME, "");
            String password = jsonObject.optString(JSON_PASSWORD, "");
            String label = jsonObject.optString(JSON_LABEL, "");
            long lastConnectedAt = jsonObject.optLong(JSON_LAST_CONNECTED_AT, 0L);
            boolean favorite = jsonObject.optBoolean(JSON_FAVORITE, false);
            int port = jsonObject.optInt(JSON_PORT, 22);
            if (TextUtils.isEmpty(id) || TextUtils.isEmpty(host) || TextUtils.isEmpty(username)) {
                return null;
            }
            return new SavedConnection(id, host, port, username, password, label, lastConnectedAt, favorite);
        }

        private String getDisplayName() {
            if (!TextUtils.isEmpty(label)) {
                return label;
            }
            return username + "@" + host;
        }

        private String getDisplayDetails() {
            return username + "@" + host + ":" + port;
        }

        private SavedConnection withLastConnectedAt(long timestamp) {
            return new SavedConnection(id, host, port, username, password, label, timestamp, favorite);
        }

        private SavedConnection withFavorite(boolean favorite) {
            return new SavedConnection(id, host, port, username, password, label, lastConnectedAt, favorite);
        }

        private SshConnectionConfig toConfig() {
            return new SshConnectionConfig(host, port, username, password);
        }
    }
}
