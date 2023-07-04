import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSADecrypter;
import com.nimbusds.jose.crypto.RSAEncrypter;
import com.nimbusds.jose.jwk.RSAKey;
import org.jose4j.jwe.ContentEncryptionAlgorithmIdentifiers;
import org.jose4j.jwe.JsonWebEncryption;
import org.jose4j.jwe.KeyManagementAlgorithmIdentifiers;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.PublicJsonWebKey;
import org.jose4j.lang.JoseException;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.util.Base64;
import java.util.Map;

public class EncryptDecryptJweToken {

    final static String publicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtixrPVyrHFwff8Tv6dppJ6Pw1CJdMhWzMr7QqJg76/adNUQMl5f6C7b6/9l9nOeqdBqxyOycf3mianK0gHrVN3rwyPmh98yJiA/BeMzunMOe115vZew2d8XpEDXx8mbLSMnm7bKprKgvRKYf6B93ZfPTLosP6kabv12Pz9LR5NCx6joT55ii6uSFrvCilsvZRoeNxxOPPBCvAgd9sOxwROBUsXrgsgmpUzSuLxtdoRfNX7bPBi+znofAxChenDOcSGCv5lZ+0TKVhNzg0nsijjqOe5skwhRFBNGHj6Dg70tS5IcsQtz0hNRQCxBzDRrbn5TTBNo0So5G22g4l6cYJwIDAQAB";
    final static String privateKey = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC2LGs9XKscXB9/xO/p2mkno/DUIl0yFbMyvtComDvr9p01RAyXl/oLtvr/2X2c56p0GrHI7Jx/eaJqcrSAetU3evDI+aH3zImID8F4zO6cw57XXm9l7DZ3xekQNfHyZstIyebtsqmsqC9Eph/oH3dl89Muiw/qRpu/XY/P0tHk0LHqOhPnmKLq5IWu8KKWy9lGh43HE488EK8CB32w7HBE4FSxeuCyCalTNK4vG12hF81fts8GL7Oeh8DEKF6cM5xIYK/mVn7RMpWE3ODSeyKOOo57myTCFEUE0YePoODvS1LkhyxC3PSE1FALEHMNGtuflNME2jRKjkbbaDiXpxgnAgMBAAECggEAM18pSp+EPTYZR92QTiDiQGRxuFCMeWA3LVsz2ic71pmv0WKELoeT4pPSCi4ZxxJpNd6FPgTDSQtS6rO4BPceg0uu9O24Z/mM2wqeY2Ne8mQTueYOge5vmaz8wS6FMPcd4kPVVDhqsp6m3bP/EWU2NVDhv6FFdqo8p2VJ9bAcsmpQ4E+2ZJxPgSHqtrhn8W96FRRBecNcM7rpgfmEqa3pq9IdAdtw+XvWsqoig6NLHDg65tW6hkV2WbZkBr56R2fledlXczlXkVPZ3Od+U0gwQqwNkUYKvZvfgPcKANvHdYfBsEIm+2VRt31QE8ataYGQbdIHm0l5e0zJzMTfg3zmSQKBgQDzxbIsnry9fasgclgAa8pGwvL0y47QuqnGwguaMtzKHYbi7jSz5rBx5PJxKD8wthjPXKRyqC1UOwz7fjZ99MT1JdIDbQ/LSkKldb4ouUSU1lU3RVR9kBcQrgMGyR5i3Xi2zw9e6P4r9o1klPECK0vJ3fd03txrAHDuhrqLH6YelQKBgQC/T7owx3iPu01te9RI/Zfft7qSf90JptQkLO/evQnaoQdmp2I6TRbrBAeSierHbj1aQILAYQ6RUqckcupekicth/+ei2lSCnTxTyOpQI9ISzHCWmvjI1M5ZbfCJQ2XKk9mYRMiyHJ1bqN1IsdBj5VVuRprFPPCtoDX0US3c3t4ywKBgQCaRVvL9y1U8mnRH+vnYE/j1k4xc31PRUJagcUb8eJemq8ZgEykKXMysQRpbmIHLsamvGdqFFqTesdthWNw9O2Mg0HUXznmmnlxAwGz/gOT+cx2LQ8aY4zlRmqt6ausP6K8dm+wzdzE78RtigC4MbRF7Y5ETSHLKb1Ohr8Zeo8DvQKBgAvvtIVAnNQS8qTHGhqnv+cUdo6Xbbohb5EGQL0b/FZov6Z3ARj0IF7vdG1/L2fcB/XumnnYVGlax9TtWpQl+E3N83P37M1Sm7NGpcn0njv7fRJMQ/j7BkFJiGqTl0J8QFH58pC0Avgyu/4d+mKry7x6fRx7RS475tQQWYI8sVJ7AoGASAbtR9PXyJxkPOFYA0OkO6TCjkKtrwNt2J/ozZqTvydnV4RZMFNMy6XlHEqJvghvzbPIAz7g0UCA8Gln4eC/zpFAJdarPz54AdYSdsRk6m7jroASQ5+BxxtV6nSi4ljgYhNy81QNB35Jt5sgOrRIsib4Qv74LC6NcoFHAMIauMA=";

    record JWK(String kty, String n, String e, String alg) {
        public Map<String, Object> asMap() {
            return Map.of("kty", kty, "n", n, "e", e, "alg", alg);
        }
    }

    record EncryptionDetails(String encryptionMethod, JWK jwk) {
    }

    public static String encryptSensitiveFieldValue(String fieldValue, EncryptionDetails encryptionDetails) throws JOSEException, ParseException {
        var encrypter = new RSAEncrypter(RSAKey.parse(encryptionDetails.jwk().asMap()));
        var header = new JWEHeader(JWEAlgorithm.parse(encryptionDetails.jwk().alg()), EncryptionMethod.parse(encryptionDetails.encryptionMethod));
        var jweObject = new JWEObject(header, new Payload(fieldValue));
        jweObject.encrypt(encrypter);
        return jweObject.serialize();
    }

    public static String decryptSensitiveFieldValue(String encryptedFieldValue, String privateKey) throws NoSuchAlgorithmException, InvalidKeySpecException, JOSEException, ParseException {
        var kf = KeyFactory.getInstance("RSA");
        var privateKeyByteArray = Base64.getDecoder().decode(privateKey);
        var rsaPrivateKey = (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(privateKeyByteArray));
        var decrypter = new RSADecrypter(rsaPrivateKey);
        var jweObject = JWEObject.parse(encryptedFieldValue);
        jweObject.decrypt(decrypter);
        return jweObject.getPayload().toString();
    }

    public static String encryptSensitiveFieldValueWithJose4j(String fieldValue, EncryptionDetails encryptionDetails) throws JoseException {
        PublicJsonWebKey jwk = (PublicJsonWebKey)JsonWebKey.Factory.newJwk(encryptionDetails.jwk().asMap());
        JsonWebEncryption senderJwe = new JsonWebEncryption();
        senderJwe.setPlaintext(fieldValue);
        senderJwe.setAlgorithmHeaderValue(encryptionDetails.jwk().alg());
        senderJwe.setEncryptionMethodHeaderParameter(encryptionDetails.encryptionMethod());
        senderJwe.setJwkHeader(jwk);
        senderJwe.setKey(jwk.getPublicKey());
        return senderJwe.getCompactSerialization();
    }

    public static String decryptSensitiveFieldValueWithJose4j(String fieldValue, String privateKey) throws JoseException, NoSuchAlgorithmException, InvalidKeySpecException {
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey));
        KeyFactory kf = KeyFactory.getInstance("RSA");
        var key = kf.generatePrivate(spec);
        JsonWebEncryption receiverJwe = new JsonWebEncryption();
        receiverJwe.setCompactSerialization(fieldValue);
        receiverJwe.setKey(key);
        return receiverJwe.getPlaintextString();
    }


    public static void main(String[] args) throws NoSuchAlgorithmException, InvalidKeySpecException, ParseException, JOSEException, JsonProcessingException, JoseException {
        var encryptionDetailsJson = """
                {
                  "encryptionMethod": "A256GCM",
                  "jwk": {
                    "kty": "RSA",
                    "n": "tixrPVyrHFwff8Tv6dppJ6Pw1CJdMhWzMr7QqJg76_adNUQMl5f6C7b6_9l9nOeqdBqxyOycf3mianK0gHrVN3rwyPmh98yJiA_BeMzunMOe115vZew2d8XpEDXx8mbLSMnm7bKprKgvRKYf6B93ZfPTLosP6kabv12Pz9LR5NCx6joT55ii6uSFrvCilsvZRoeNxxOPPBCvAgd9sOxwROBUsXrgsgmpUzSuLxtdoRfNX7bPBi-znofAxChenDOcSGCv5lZ-0TKVhNzg0nsijjqOe5skwhRFBNGHj6Dg70tS5IcsQtz0hNRQCxBzDRrbn5TTBNo0So5G22g4l6cYJw",
                    "e": "AQAB",
                    "alg": "RSA-OAEP-256"
                  }
                }
                """;

        var encryptionDetails = new ObjectMapper().readValue(encryptionDetailsJson, EncryptionDetails.class);
        var encryptedValueWithJose4j = encryptSensitiveFieldValueWithJose4j("my$ecretPassword", encryptionDetails);
        var decryptedValueFromJose4j = decryptSensitiveFieldValueWithJose4j(encryptedValueWithJose4j, privateKey);
        System.out.println("encryptedValueWithJose4j = " + encryptedValueWithJose4j);
        System.out.println("decryptedValueFromJose4j = " + decryptedValueFromJose4j);

        var encryptedValue = encryptSensitiveFieldValue("my$ecretPassword", encryptionDetails);
        var decryptedValue = decryptSensitiveFieldValue(encryptedValue, privateKey);
        System.out.println("encryptedValue = " + encryptedValue);
        System.out.println("decryptedValue = " + decryptedValue);


        var fromJS = decryptSensitiveFieldValue("eyJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.IdrFauXttl9k7Y5q2_HdpKszHdlFJeYjFl3zNFELUNc7wqAIilsIf_33sCk0IbY5rwbog3waSGfDuJoKPowL-dLii8wK7wwtXetwKH8mRKrj6KhgficDUOb3I8NTsPSbbMUq7hH9uIR5I3K_J8bb-Y-9VCFAudQCFQ4s9HEa5rZpUpTDycvEiJKWgRBBVXddUsDHH0U2yIPASGyHdtyqWXOWinRaI7CMh7MUYSK4YjlGZ-ncMf06m61l2D8ftXhVwmNzV1bnhsOIBbXGzbzOsWLjEBuIF3DRmzjrh5v_O02Ckz5XcPXndnu9TASTmxBu-P2Lc3MWtuRWq-inSrAQ7Q.cMUj_Bdw8S5KYp00.3l8X8G9fOIsNKXjr.3g4uPO1l_Jh7CZ1PCg5l_Q", privateKey);
        System.out.println("fromJS = " + fromJS);
        var fromPython = decryptSensitiveFieldValue("eyJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.fDbu5jJXKIoF_ybA31I19pY_otc_DR2Wtq22AHPR3_p8vixBRj3ki2JWTNSnKu2w_GN5UY-lxkTUvgeAoUGa1ciCqP7_sz9Fvf2n38ENcjh3iq-yO9F9Us3ehYokAM2WHA84nHgUyipZVl-CTd7YcoMZkkMUWWcUUt2-S8oZbxwJT-Ps6qBPKSLqGWxzoUSGz0MkoOsiCwObGP-ul1d0TAHfkcyvmOs-g-pUpId1NuwBmxA8jaRA51tiQErlGknQacwcypMhbyLxyIoZUYZ4oVZbt5YRX6zTaKfulSltV9p5J273gqOz8qFS0mzJB_aD7apwF3bm1dJAQaeB0uo7Uw.kv9eWKP3yQjCfY2P.j-Gmza3HKB4byrqDat1onmbHU0JCvRSK3RRE-oZzRA.g4vJdNLYEoLdMTbwK8Vu7g", privateKey);
        System.out.println("fromPython = " + fromPython);
    }

    private static String createJwkObject() throws NoSuchAlgorithmException, InvalidKeySpecException, JOSEException {
        var kf = KeyFactory.getInstance("RSA");
        var publicKeyBytes = Base64.getDecoder().decode(publicKey);
        var publicKey = (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
        var jwk = new RSAKey.Builder(publicKey).build();
        return jwk.toJSONString();
    }
}