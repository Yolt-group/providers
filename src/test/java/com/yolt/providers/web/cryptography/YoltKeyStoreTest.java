package com.yolt.providers.web.cryptography;

import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


@ExtendWith(MockitoExtension.class)
public class YoltKeyStoreTest {

    static {
        Security.insertProviderAt(new YoltSecurityProvider(), 2);
    }

    @Mock
    private PrivateKey privateKey;

    @Mock
    private Certificate certificate;

    @Test
    public void shouldSetProperKeyEntryForSetKeyEntryWithCorrectData() throws Exception {
        // given
        KeyStore yolt = KeyStore.getInstance("Yolt");
        yolt.load(null, null);

        // when
        yolt.setKeyEntry("alias", privateKey, null, new Certificate[]{certificate});

        // then
        Key key = yolt.getKey("alias", null);
        assertThat(key).isEqualTo(privateKey);
        Certificate[] certificateChain = yolt.getCertificateChain("alias");
        assertThat(certificateChain).contains(certificate);
    }

    @Test
    public void shouldReturnTheSameCertificateForSetCertificateEntryWithTheSameAlias() throws Exception {
        // given
        KeyStore yolt = KeyStore.getInstance("Yolt");
        yolt.load(null, null);

        // when
        yolt.setCertificateEntry("alias", certificate);

        // then
        Certificate cert = yolt.getCertificate("alias");
        assertThat(cert).isEqualTo(certificate);
    }

    @Test
    public void shouldThrowUnsupportedOperationExceptionForDeleteEntryAsThisFeatureIsUnsupportedForYoltKeyStore() throws Exception {
        // given
        KeyStore yolt = KeyStore.getInstance("Yolt");
        yolt.load(null, null);

        // when
        ThrowableAssert.ThrowingCallable deleteEntryCallable = () -> yolt.deleteEntry("alias");

        // then
        assertThatThrownBy(deleteEntryCallable)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void shouldThrowUnsupportedOperationExceptionForStoreAsThisFeatureIsUnsupportedForYoltKeyStore() throws Exception {
        // given
        KeyStore yolt = KeyStore.getInstance("Yolt");
        yolt.load(null, null);

        // when
        ThrowableAssert.ThrowingCallable storeCallable = () -> yolt.store(new ByteArrayOutputStream(), new char[]{'p', 'a', 's', 's'});

        // then
        assertThatThrownBy(storeCallable)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void shouldThrowUnsupportedOperationExceptionForSetKeyEntryWithByteArrayAsThisFeatureIsUnsupportedForYoltKeyStore() throws Exception {
        // given
        KeyStore yolt = KeyStore.getInstance("Yolt");
        yolt.load(null, null);

        // when
        ThrowableAssert.ThrowingCallable storeCallable = () -> yolt.setKeyEntry("alias", new byte[]{}, new Certificate[]{certificate});

        // then
        assertThatThrownBy(storeCallable)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void shouldSetProperAliasesForKeyEntryAndCertificateEntryThatAreNotMixedUpAndAreReturnedCorreclty() throws Exception {
        // given
        KeyStore yolt = KeyStore.getInstance("Yolt");
        yolt.load(null, null);

        // when
        yolt.setKeyEntry("key", privateKey, null, new Certificate[]{certificate});
        yolt.setCertificateEntry("cert", certificate);

        // then
        assertThat(yolt.size()).isEqualTo(1);
        assertThat(yolt.containsAlias("key")).isTrue();
        assertThat(yolt.containsAlias("cert")).isTrue();
        assertThat(yolt.isKeyEntry("key")).isTrue();
        assertThat(yolt.isKeyEntry("cert")).isFalse();
        assertThat(yolt.isCertificateEntry("cert")).isTrue();
        assertThat(yolt.isCertificateEntry("key")).isFalse();
        assertThat(yolt.getCertificateAlias(certificate)).isEqualTo("cert");
    }
}