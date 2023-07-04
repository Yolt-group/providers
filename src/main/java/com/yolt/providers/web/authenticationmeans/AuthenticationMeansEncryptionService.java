package com.yolt.providers.web.authenticationmeans;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yolt.providers.common.domain.authenticationmeans.BasicAuthenticationMean;
import com.yolt.providers.common.domain.authenticationmeans.types.JwtType;
import com.yolt.providers.common.domain.authenticationmeans.types.NoWhiteCharacterStringType;
import com.yolt.providers.common.domain.authenticationmeans.types.PemType;
import com.yolt.providers.common.domain.authenticationmeans.types.PrefixedHexadecimalType;
import com.yolt.providers.common.domain.authenticationmeans.types.StringType;
import com.yolt.providers.common.domain.authenticationmeans.types.UuidType;
import com.yolt.providers.common.exception.JsonParseException;
import com.yolt.providers.web.encryption.AesEncryptionUtil;
import com.yolt.providers.web.service.ProviderVaultKeys;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.yolt.providers.web.configuration.ApplicationConfiguration.OBJECT_MAPPER;

@Service
public class AuthenticationMeansEncryptionService {

    private final ObjectMapper objectMapper;
    private final ProviderVaultKeys vaultKeys;

    private static final TypeReference<Map<String, BasicAuthenticationMean>> TYPE_REF_AUTHENTICATION_MEANS_LIST =
            new TypeReference<>() {
            };

    public AuthenticationMeansEncryptionService(ProviderVaultKeys vaultKeys, @Qualifier(OBJECT_MAPPER) ObjectMapper objectMapper) {
        this.vaultKeys = vaultKeys;
        this.objectMapper = objectMapper;
        registerTypedAuthenticationMeans(objectMapper);
    }

    public Map<String, BasicAuthenticationMean> decryptAuthenticationMeans(String authenticationMeans) {
        String decrypt = AesEncryptionUtil.decrypt(authenticationMeans, vaultKeys.getAuthEncryptionKey());
        try {
            return objectMapper.readValue(decrypt, TYPE_REF_AUTHENTICATION_MEANS_LIST);
        } catch (IOException e) {
            throw new JsonParseException(e, "Unable to deserialize authentication means");
        }
    }

    public String encryptAuthenticationMeans(Map<String, BasicAuthenticationMean> authenticationMeans) {
        try {
            String setAsJson = objectMapper.writeValueAsString(authenticationMeans);
            return AesEncryptionUtil.encrypt(setAsJson, vaultKeys.getAuthEncryptionKey());
        } catch (JsonProcessingException e) {
            throw new JsonParseException(e, "Unable to serialize authentication means");
        }
    }

    private void registerTypedAuthenticationMeans(ObjectMapper objectMapper) {
        Set<Class<?>> authenticationMeanClassesToRegister = new HashSet<>();
        authenticationMeanClassesToRegister.add(UuidType.class);
        authenticationMeanClassesToRegister.add(NoWhiteCharacterStringType.class);
        authenticationMeanClassesToRegister.add(PemType.class);
        authenticationMeanClassesToRegister.add(StringType.class);
        authenticationMeanClassesToRegister.add(PrefixedHexadecimalType.class);
        authenticationMeanClassesToRegister.add(JwtType.class);
        //TODO C4PO-6656 Add this class after JsonTypeName will be fixed in it
        //authenticationMeanClassesToRegister.add(CertificatesChainPemType.class);

        objectMapper.registerSubtypes(authenticationMeanClassesToRegister);
    }
}
