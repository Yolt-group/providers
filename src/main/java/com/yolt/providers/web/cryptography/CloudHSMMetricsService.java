package com.yolt.providers.web.cryptography;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAKey;
import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty("yolt.providers.cloudHSM.enabled")
public class CloudHSMMetricsService implements MeterBinder, SignatureSpiEventListener {

    private final CloudHSMKeyService keyService;
    private MeterRegistry registry;

    @Override
    public void bindTo(MeterRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    public void addListener(){
        YoltDelegatingSignature.addListener(this);
    }

    @PreDestroy
    public void removeListener(){
        YoltDelegatingSignature.removeListener(this);
    }

    @Override
    public void engineSign(PrivateKey privateKey) {
        if (registry == null) {
            return;
        }

        keyService.getPrivateKeyReference(privateKey)
                .map(privateKeyReference -> createTags(privateKey, privateKeyReference))
                .ifPresent(tags -> registry.counter("cloudhsm_sign_operation", tags).increment());
    }

    private List<Tag> createTags(PrivateKey privateKey, CloudHSMKeyService.PrivateKeyReference privateKeyReference) {
        return List.of(
                Tag.of("clientgroup", privateKeyReference.getClientGroupId().toString()),
                Tag.of("keyid", privateKeyReference.getKid().toString()),
                Tag.of("algorithm", privateKey.getAlgorithm()),
                Tag.of("size", Integer.toString(keySize(privateKey))));

    }

    private int keySize(PrivateKey key) {
        if (!(key instanceof RSAKey)) {
            return -1;
        }
        RSAKey rsaKey = (RSAKey) key;
        // the modulus is a big integer without any leading zeros.
        // so while the key size may have been generated as 1024,
        // the actual bit length may be 1023.
        //
        // rounding ensures we can group the keys into nice buckets.
        BigInteger modulus = rsaKey.getModulus();
        int bitLength = modulus.bitLength();
        double nextPowerOf2 = Math.ceil(Math.log(bitLength) / Math.log(2));
        return (int) Math.floor(Math.pow(2, nextPowerOf2));
    }

    @Override
    public void engineVerify(PublicKey publicKey) {

    }
}
