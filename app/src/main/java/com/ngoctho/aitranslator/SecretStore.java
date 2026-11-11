package com.ngoctho.aitranslator;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Stores user-entered secrets encrypted with a key held by Android Keystore.
 * This protects secrets at rest on the device. It does not make a client-side
 * API key safe for redistribution; production deployments should use a proxy.
 */
public final class SecretStore {
    private static final String STORE = "secure_settings";
    private static final String ALIAS = "ngoc_tho_ai_translator_v1";
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";

    private SecretStore() {}

    public static void put(Context context, String name, String value) {
        String clean = value == null ? "" : value.trim();
        SharedPreferences p = context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
        if (clean.isEmpty()) {
            p.edit().remove(name).apply();
            return;
        }
        try {
            SecretKey key = getOrCreateKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] iv = cipher.getIV();
            byte[] ciphertext = cipher.doFinal(clean.getBytes(StandardCharsets.UTF_8));
            String packed = Base64.encodeToString(iv, Base64.NO_WRAP) + "." +
                    Base64.encodeToString(ciphertext, Base64.NO_WRAP);
            p.edit().putString(name, packed).apply();
        } catch (Exception e) {
            // Fail closed: do not write plaintext if encryption is unavailable.
            throw new IllegalStateException("Không lưu được khóa bảo mật: " + safeMessage(e), e);
        }
    }

    public static String get(Context context, String name) {
        SharedPreferences p = context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
        String packed = p.getString(name, "");
        if (packed == null || packed.isEmpty()) return "";
        try {
            int dot = packed.indexOf('.');
            if (dot <= 0 || dot >= packed.length() - 1) return "";
            byte[] iv = Base64.decode(packed.substring(0, dot), Base64.NO_WRAP);
            byte[] ciphertext = Base64.decode(packed.substring(dot + 1), Base64.NO_WRAP);
            SecretKey key = getOrCreateKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] clear = cipher.doFinal(ciphertext);
            return new String(clear, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore store = KeyStore.getInstance(ANDROID_KEY_STORE);
        store.load(null);
        if (store.containsAlias(ALIAS)) {
            return ((KeyStore.SecretKeyEntry) store.getEntry(ALIAS, null)).getSecretKey();
        }

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEY_STORE);
        generator.init(new KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }

    private static String safeMessage(Exception e) {
        String m = e.getMessage();
        return m == null || m.trim().isEmpty() ? e.getClass().getSimpleName() : m;
    }
}
