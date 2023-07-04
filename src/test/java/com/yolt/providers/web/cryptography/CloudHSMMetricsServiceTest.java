package com.yolt.providers.web.cryptography;

import com.yolt.providers.web.cryptography.CloudHSMKeyService.PrivateKeyReference;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.security.interfaces.RSAPrivateKey;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudHSMMetricsServiceTest {

    final MeterRegistry registry = new SimpleMeterRegistry();

    @Mock
    RSAPrivateKey privateKey;

    @Mock
    CloudHSMKeyService keyService;

    @InjectMocks
    CloudHSMMetricsService metricsService;

    @BeforeEach
    void setup(){
        metricsService.bindTo(registry);
    }

    @Test
    void test() {
        var clientGroupId = UUID.randomUUID();
        var kid = UUID.randomUUID();
        var reference = new PrivateKeyReference(clientGroupId, kid);

        when(keyService.getPrivateKeyReference(privateKey)).thenReturn(Optional.of(reference));
        when(privateKey.getAlgorithm()).thenReturn("RSA");
        when(privateKey.getModulus()).thenReturn(BigInteger.valueOf(4294967296L));

        metricsService.engineSign(privateKey);

        var meter = registry.getMeters().stream().findFirst().get();
        assertThat(meter.getId().getName()).isEqualTo("cloudhsm_sign_operation");
        assertThat(meter.getId().getTags()).contains(
                Tag.of("clientgroup", clientGroupId.toString()),
                Tag.of("keyid", kid.toString()),
                Tag.of("algorithm", "RSA"),
                // 2^32 = 4294967296L, so goes into 64 bit bucket
                Tag.of("size", Integer.toString(64)));

    }
}