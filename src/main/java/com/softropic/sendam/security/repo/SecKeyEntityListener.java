package com.softropic.sendam.security.repo;





import com.softropic.sendam.security.contract.PermutedSecretKey;

import jakarta.persistence.PostLoad;

/**
 *
 */
public class SecKeyEntityListener {

    @PostLoad
    public void derivePermKey(final SecKey secKey) {
        final String encrPermKey = secKey.getEncrPermKey();

        final String sequence = secKey.getSeq();

        String permKey = PermutedSecretKey.instanceFromEncryptedKey(encrPermKey, sequence).getPermKey();

        secKey.setPermKey(permKey);
    }
}
