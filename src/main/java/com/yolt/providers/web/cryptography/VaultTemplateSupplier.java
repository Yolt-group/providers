package com.yolt.providers.web.cryptography;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.vault.authentication.KubernetesAuthentication;
import org.springframework.vault.authentication.KubernetesAuthenticationOptions;
import org.springframework.vault.client.RestTemplateBuilder;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.Supplier;

@Service
@Slf4j
public class VaultTemplateSupplier implements Supplier<VaultTemplate> {

    private final VaultEndpoint vaultEndpoint;
    private final KubernetesAuthenticationOptions authOptions;
    private final String serviceAccountTokenFile;
    private final RestTemplate restOperations;

    public VaultTemplateSupplier(@Value("${yolt.vault.address}") String vaultAddress,
                                 @Value("${yolt.vault.authentication.role}") String role,
                                 @Value("${yolt.vault.authentication.kubernetes-path}") String kubernetesPath,
                                 @Value("${yolt.vault.authentication.service-account-token-file}") String serviceAccountTokenFile) {
        log.info("Creating vault template supplier for {}", vaultAddress);
        this.vaultEndpoint = VaultEndpoint.from(URI.create(vaultAddress));
        this.restOperations = RestTemplateBuilder.builder().endpoint(vaultEndpoint).build();
        this.authOptions = KubernetesAuthenticationOptions.builder()
                .path(kubernetesPath)
                .role(role)
                .jwtSupplier(serviceAccountTokenSupplier())
                .build();
        this.serviceAccountTokenFile = serviceAccountTokenFile;
    }

    /**
     * Creates a new VaultTemplate.
     * Spring Cloud Vault has issues around session management, which resulted in a lot of custom code in lovebird-commons before.
     * By recreating a new VaultTemplate everytime we need it, we can avoid these problems.
     * If this will have negative performance impact, we should look into checking if Spring has solved these problems.
     */
    @Override
    public VaultTemplate get() {
        return new VaultTemplate(vaultEndpoint, new KubernetesAuthentication(authOptions, restOperations));
    }

    private Supplier<String> serviceAccountTokenSupplier() {
        return () -> {
            try {
                byte[] encoded = Files.readAllBytes(Paths.get(serviceAccountTokenFile));
                return new String(encoded, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("IO error occurred while trying to resolve service account token file from: " + serviceAccountTokenFile);
            }
        };
    }
}
