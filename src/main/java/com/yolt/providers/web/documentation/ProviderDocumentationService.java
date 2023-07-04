package com.yolt.providers.web.documentation;

import com.yolt.providers.common.providerinterface.Provider;
import com.yolt.providers.web.service.ProviderFactoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.providerdomain.ServiceType;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static com.yolt.providers.web.service.ProviderService.PROVIDER_MDC_KEY;

@Service
@RequiredArgsConstructor
@Slf4j
class ProviderDocumentationService {

    private static final String ALLOWED_EXTENSION = "md";
    private static final char LINE_SEPARATOR = '\n';

    private final ProviderFactoryService providerFactoryService;

    public List<ProviderDocumentation> getProvidersDocumentation() {
        List<ProviderDocumentation> result = new LinkedList<>();
        for (Provider provider : providerFactoryService.getAllStableProviders()) {
            getProviderDocumentation(provider.getProviderIdentifier(), provider.getServiceType())
                    .ifPresent(result::add);
        }

        return result;
    }

    public Optional<ProviderDocumentation> getProviderDocumentation(String providerIdentifier, ServiceType serviceType) {
        if (StringUtils.isBlank(providerIdentifier) || serviceType == null) {
            return Optional.empty();
        }

        MDC.put(PROVIDER_MDC_KEY, providerIdentifier);
        try {
            String documentation = readDocumentation(providerIdentifier, serviceType);
            if (documentation == null) {
                log.info("Didn't find documentation \"{}\"", constructProvidersDocumentationPath(providerIdentifier, serviceType));
                return Optional.empty();
            }

            String encodedDocumentation = encodeDocumentation(documentation);
            return Optional.of(new ProviderDocumentation(providerIdentifier, serviceType, encodedDocumentation));
        } catch (Exception e) {
            log.warn("Found unexpected error when processing provider internal documentation", e);
            return Optional.empty();
        }
    }

    private String readDocumentation(String providerIdentifier, ServiceType serviceType) {
        try {
            String documentationPath = constructProvidersDocumentationPath(providerIdentifier, serviceType);
            // Tries to find copied documentation in resources.
            // In case of absence of documentation, the null is returned.
            try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(documentationPath);
                 BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
                return readAllLinesFromDocumentation(bufferedReader);
            }
        } catch (NullPointerException e) {
            return null;
        } catch (IOException e) {
            throw new IllegalArgumentException("Found unexpected error when reading documentation", e);
        }
    }

    private String readAllLinesFromDocumentation(BufferedReader bufferedReader) throws IOException {
        StringBuilder resultStringBuilder = new StringBuilder();
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            resultStringBuilder.append(line).append(LINE_SEPARATOR);
        }
        return resultStringBuilder.toString();
    }

    /**
     * Construct documentation path that is copied via providers-parent build plugin.
     * https://git.yolt.io/providers/providers-parent/-/blob/master/pom.xml#L189
     */
    private String constructProvidersDocumentationPath(String providerIdentifier, ServiceType serviceType) {
        return "documentation/" + providerIdentifier + "_ReadMe_" + serviceType + "." + ALLOWED_EXTENSION;
    }

    private String encodeDocumentation(String documentation) {
        byte[] encode = Base64.getEncoder().encode(documentation.getBytes(StandardCharsets.UTF_8));
        return new String(encode, StandardCharsets.UTF_8);
    }
}