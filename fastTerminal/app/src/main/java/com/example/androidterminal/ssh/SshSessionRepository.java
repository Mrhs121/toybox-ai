package com.example.androidterminal.ssh;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.TerminalSessionClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class SshSessionRepository {

    private static final Object LOCK = new Object();
    private static final LinkedHashMap<String, SshTerminalSession> SESSIONS = new LinkedHashMap<>();

    private SshSessionRepository() {
    }

    @NonNull
    public static SshTerminalSession create(
        @NonNull TerminalSessionClient terminalSessionClient,
        @NonNull SshTerminalSession.Listener listener
    ) {
        synchronized (LOCK) {
            SshTerminalSession session = new SshTerminalSession(terminalSessionClient, listener);
            SESSIONS.put(session.getSessionId(), session);
            return session;
        }
    }

    public static void attachUi(
        @NonNull SshTerminalSession session,
        @NonNull TerminalSessionClient terminalSessionClient,
        @NonNull SshTerminalSession.Listener listener
    ) {
        synchronized (LOCK) {
            if (SESSIONS.containsKey(session.getSessionId())) {
                session.attachUi(terminalSessionClient, listener);
            }
        }
    }

    public static void detachUi(@NonNull SshTerminalSession session) {
        synchronized (LOCK) {
            if (SESSIONS.containsKey(session.getSessionId())) {
                session.detachUi();
            }
        }
    }

    public static void disconnectAndRemove(@NonNull SshTerminalSession session, @NonNull String message) {
        synchronized (LOCK) {
            if (SESSIONS.remove(session.getSessionId()) != null) {
                session.disconnect(message);
            }
        }
    }

    @Nullable
    public static SshTerminalSession findById(@Nullable String sessionId) {
        synchronized (LOCK) {
            return sessionId == null ? null : SESSIONS.get(sessionId);
        }
    }

    @NonNull
    public static List<SshTerminalSession> listSessions() {
        synchronized (LOCK) {
            return new ArrayList<>(SESSIONS.values());
        }
    }

    public static boolean hasConnectedSessions() {
        synchronized (LOCK) {
            for (SshTerminalSession session : SESSIONS.values()) {
                if (session.isConnected()) {
                    return true;
                }
            }
            return false;
        }
    }
}
