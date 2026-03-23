package com.example.androidterminal.ssh;

import androidx.annotation.NonNull;

import com.termux.terminal.TerminalSessionClient;

public final class SshSessionRepository {

    private static final Object LOCK = new Object();
    private static SshTerminalSession currentSession;

    private SshSessionRepository() {
    }

    @NonNull
    public static SshTerminalSession getOrCreate(
        @NonNull TerminalSessionClient terminalSessionClient,
        @NonNull SshTerminalSession.Listener listener
    ) {
        synchronized (LOCK) {
            if (currentSession == null) {
                currentSession = new SshTerminalSession(terminalSessionClient, listener);
            } else {
                currentSession.attachUi(terminalSessionClient, listener);
            }
            return currentSession;
        }
    }

    @NonNull
    public static SshTerminalSession replace(
        @NonNull TerminalSessionClient terminalSessionClient,
        @NonNull SshTerminalSession.Listener listener
    ) {
        synchronized (LOCK) {
            if (currentSession != null) {
                currentSession.disconnect("Reconnecting");
            }
            currentSession = new SshTerminalSession(terminalSessionClient, listener);
            return currentSession;
        }
    }

    public static void detachUi(SshTerminalSession session) {
        synchronized (LOCK) {
            if (currentSession == session) {
                currentSession.detachUi();
            }
        }
    }

    public static void disconnectAndClear(SshTerminalSession session, @NonNull String message) {
        synchronized (LOCK) {
            if (currentSession == session) {
                currentSession.disconnect(message);
                currentSession = null;
            }
        }
    }

    public static SshTerminalSession peek() {
        synchronized (LOCK) {
            return currentSession;
        }
    }
}
