package com.yolt.providers.web.encryption;


import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.AlgorithmParameterSpec;

public class AesEncryptionUtil {

    private static final String PROVIDER = "BC";
    static final String ALGORITHM = "AES";
    private static final String MODE = "GCM";
    private static final String PADDING = "NoPadding";
    private static final String TRANSFORMATION = ALGORITHM + "/" + MODE + "/" + PADDING;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private AesEncryptionUtil() {

    }

    public static String encrypt(String input, String secretKey) {
        try {
            byte[] secretBytes = Hex.decodeHex(secretKey.toCharArray());
            Key key = new SecretKeySpec(secretBytes, ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION, PROVIDER);
            SecureRandom secureRandom = new SecureRandom();
            byte[] iv = new byte[256 >> 3];
            secureRandom.nextBytes(iv);
            AlgorithmParameterSpec paramSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, paramSpec);
            byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);
            byte[] encryptedBytes = cipher.doFinal(inputBytes);
            return new String(Hex.encodeHex(iv)) + new String(Hex.encodeHex(encryptedBytes));
        } catch (DecoderException | NoSuchAlgorithmException |
                InvalidKeyException | NoSuchPaddingException |
                InvalidAlgorithmParameterException | NoSuchProviderException |
                BadPaddingException | IllegalBlockSizeException e) {
            throw new EncryptionException("Exception while encrypting", e);
        }
    }

    public static String decrypt(String encrypted, String secretKey) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            byte[] iv = Hex.decodeHex(encrypted.substring(0, 64).toCharArray());
            byte[] encryptedData = Hex.decodeHex(encrypted.substring(64).toCharArray());
            AlgorithmParameterSpec paramSpec = new IvParameterSpec(iv);
            Key key = new SecretKeySpec(Hex.decodeHex(secretKey.toCharArray()), ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, paramSpec);
            byte[] decryptedBytes = cipher.doFinal(encryptedData);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (DecoderException | NoSuchAlgorithmException | BadPaddingException | NoSuchPaddingException |
                InvalidAlgorithmParameterException | InvalidKeyException | IllegalBlockSizeException e) {
            throw new EncryptionException("Exception while decrypting", e);
        }
    }

}
