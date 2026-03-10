package com.softropic.sendam.security.infrastructure.jwt;


import com.softropic.sendam.security.repo.Secret;

public class JwtSecretProvider {
    private static final ThreadLocal<Secret> contextHolder = new ThreadLocal<>(); // NOPMD

    private JwtSecretProvider() {}

    public static byte[] getKey() {
        final Secret secret = contextHolder.get();
        if(secret == null) {
            return new byte[0];
        }
        return secret.getSecretBytes();
    }

    public static void setSecret(Secret secret) {
        contextHolder.set(secret);
    }

    public static void removeFromThread() {
        contextHolder.remove();
    }


}
