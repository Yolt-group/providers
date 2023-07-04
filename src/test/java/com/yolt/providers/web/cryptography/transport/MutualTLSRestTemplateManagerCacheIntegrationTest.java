package com.yolt.providers.web.cryptography.transport;

import com.yolt.providers.common.cryptography.RestTemplateManager;
import com.yolt.providers.web.configuration.IntegrationTestContext;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clienttokens.ClientGroupToken;
import nl.ing.lovebird.clienttokens.ClientToken;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static com.yolt.providers.common.versioning.ProviderVersion.*;
import static nl.ing.lovebird.providerdomain.ServiceType.AIS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


@Slf4j
@IntegrationTestContext
public class MutualTLSRestTemplateManagerCacheIntegrationTest {

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final ClientToken CLIENT_TOKEN = mock(ClientToken.class);
    private static final UUID CLIENT_GROUP_ID = UUID.randomUUID();
    private static final ClientGroupToken CLIENT_GROUP_TOKEN = mock(ClientGroupToken.class);
    private static final String LLOYDS_BANK_NAME = "LLOYDS_BANK";

    @Autowired
    private MutualTLSRestTemplateManagerCache mutualTLSRestTemplateManagerCache;

    @Test
    public void shouldReturnTheSameRestTemplateManagerInstanceForGetForClientProviderWhenCalledAgainWithTheSameData() {
        // given
        when(CLIENT_TOKEN.getClientIdClaim()).thenReturn(CLIENT_ID);
        RestTemplateManager expected = mutualTLSRestTemplateManagerCache.getForClientProvider(CLIENT_TOKEN, AIS, LLOYDS_BANK_NAME, false, VERSION_1);

        // when
        RestTemplateManager actual = mutualTLSRestTemplateManagerCache.getForClientProvider(CLIENT_TOKEN, AIS, LLOYDS_BANK_NAME, false, VERSION_1);

        // then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void shouldReturnTheSameRestTemplateManagerInstanceForGetForClientGroupProviderWhenCalledAgainWithTheSameData() {
        // given
        when(CLIENT_GROUP_TOKEN.getClientGroupIdClaim()).thenReturn(CLIENT_GROUP_ID);
        RestTemplateManager expected = mutualTLSRestTemplateManagerCache.getForClientGroupProvider(CLIENT_GROUP_TOKEN, AIS, LLOYDS_BANK_NAME, false, VERSION_1);

        // when
        RestTemplateManager actual = mutualTLSRestTemplateManagerCache.getForClientGroupProvider(CLIENT_GROUP_TOKEN, AIS, LLOYDS_BANK_NAME, false, VERSION_1);

        // then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void shouldReturnTheSameRestTemplateManagerInstanceForProperVersionForGetForClientProviderWhenCalledAgainWithTheSameData() {
        // given
        when(CLIENT_GROUP_TOKEN.getClientGroupIdClaim()).thenReturn(CLIENT_GROUP_ID);
        RestTemplateManager expectedVersion1 = mutualTLSRestTemplateManagerCache.getForClientGroupProvider(CLIENT_GROUP_TOKEN, AIS, LLOYDS_BANK_NAME, false, VERSION_1);
        RestTemplateManager expectedVersion2 = mutualTLSRestTemplateManagerCache.getForClientGroupProvider(CLIENT_GROUP_TOKEN, AIS, LLOYDS_BANK_NAME, false, VERSION_2);
        RestTemplateManager expectedVersion3 = mutualTLSRestTemplateManagerCache.getForClientGroupProvider(CLIENT_GROUP_TOKEN, AIS, LLOYDS_BANK_NAME, false, VERSION_3);

        // when
        RestTemplateManager actualVersion1 = mutualTLSRestTemplateManagerCache.getForClientGroupProvider(CLIENT_GROUP_TOKEN, AIS, LLOYDS_BANK_NAME, false, VERSION_1);
        RestTemplateManager actualVersion2 = mutualTLSRestTemplateManagerCache.getForClientGroupProvider(CLIENT_GROUP_TOKEN, AIS, LLOYDS_BANK_NAME, false, VERSION_2);
        RestTemplateManager actualVersion3 = mutualTLSRestTemplateManagerCache.getForClientGroupProvider(CLIENT_GROUP_TOKEN, AIS, LLOYDS_BANK_NAME, false, VERSION_3);

        // then
        assertThat(actualVersion1)
                .isEqualTo(expectedVersion1)
                .isNotEqualTo(expectedVersion2)
                .isNotEqualTo(expectedVersion3);
        assertThat(actualVersion2)
                .isEqualTo(expectedVersion2)
                .isNotEqualTo(expectedVersion1)
                .isNotEqualTo(expectedVersion3);
        assertThat(actualVersion3)
                .isEqualTo(expectedVersion3)
                .isNotEqualTo(expectedVersion1)
                .isNotEqualTo(expectedVersion2);
    }
}