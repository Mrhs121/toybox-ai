package com.example.androidterminal.ssh;

public final class SshConnectionConfig {

    public enum AuthType {
        PASSWORD,
        KEY
    }

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final AuthType authType;
    private final String privateKeyPath;
    private final String passphrase;

    public SshConnectionConfig(String host, int port, String username, String password) {
        this(host, port, username, password, AuthType.PASSWORD, null, null);
    }

    public SshConnectionConfig(String host, int port, String username, String password,
                               AuthType authType, String privateKeyPath, String passphrase) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.authType = authType != null ? authType : AuthType.PASSWORD;
        this.privateKeyPath = privateKeyPath;
        this.passphrase = passphrase;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public AuthType getAuthType() {
        return authType;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public String getPassphrase() {
        return passphrase;
    }
}
