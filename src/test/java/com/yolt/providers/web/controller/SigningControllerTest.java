package com.yolt.providers.web.controller;

import com.yolt.providers.web.configuration.TestConfiguration;
import com.yolt.providers.web.errorhandling.ExtendedExceptionHandlingService;
import com.yolt.providers.web.service.SigningService;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.clienttokens.constants.ClientTokenConstants;
import nl.ing.lovebird.clienttokens.test.TestClientTokens;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.function.Consumer;

import static nl.ing.lovebird.clienttokens.constants.ClientTokenConstants.EXTRA_CLAIM_ISSUED_FOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@WebMvcTest(controllers = SigningController.class)
@Import({
        TestConfiguration.class,
        ExtendedExceptionHandlingService.class
})
public class SigningControllerTest {

    private static final String PATTERN = "\\{\"algorithm\":\"[A-Z0-9]{5}\",\"alias\":\\{\"realmId\":\"\\w+:\\w+:\\w+\"," +
            "\"type\":\"EIDAS\",\"value\":\".*\"},\"certificate\":\"" +
            "[A-Za-z0-9+/]*={0,2}\",\"memberId\":\"\\w+:\\w+:\\w+\"}";
    private static final String SIGNED_PAYLOAD = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
    private static final String PAYLOAD = "{\"algorithm\":\"RS256\",\"alias\":{\"realmId\":\"m:AaBb123:Cc22\",\"type\":\"EIDAS\",\"value\":\"ABCDE-FGH-I1234\"},\"certificate\":\"MIIJbTCCByWgAwIBAgIQGWpI79LT3O2r1XH/tXkpSzA9Bg=\",\"memberId\":\"m:AAAbbCC:55AAbb\"}";
    private static final String NATIONWIDE_PAYLOAD = "{\"software_statement\": \"eyJhbGciOiJQUzI1NiIsImtpZCI6Ikh6YTl2NWJnR...vlsIOLNPA\", \"aud\": \"https://api.nationwide.co.uk/open-banking\", \"grant_types\": [\"authorization_code\", \"client_credentials\", \"refresh_token\"], \"scope\": \"openid accounts payments\", \"iss\": \"0015800001HQQrFAAL\", \"exp\": 1579520051, \"iat\": 1579170789, \"token_endpoint_auth_method\": \"tls_client_auth\", \"software_id\": \"VBgoz0UEnwpUoS07JDEzVL\", \"jti\": \"aff03404-b404-44c6-99f9-b994d0fdf611\", \"redirect_uris\": [\"https://example-tpp.com/callback\"], \"application_type\": \"web\", \"id_token_signed_response_alg\": \"PS256\", \"request_object_signing_alg\": \"PS256\", \"response_types\": [\"code id_token\"]}";
    private static final String INVALID_PAYLOAD = "{\"algorithm\":\"RS256\",\"alias\":{\"realmId\":\"m:AaBb123:Cc22\",\"type\":\"EIDAS\",\"value\":\"ABCDE-FGH-I1234\"},\"certificate\":\"NotBase64.value\",\"memberId\":\"m:AAAbbCC:55AAbb\"}";
    private static final String SIGNING_KEY_ID = "91a724ff-5784-4393-88c1-5fd875992de3";
    private static final String JVM_ALGORITHM = "SHA256withRSA";
    private static final String JVM_ALGORITHM_FOR_NATIONWIDE = "SHA256withRSA/PSS";
    private static final String SIGNING_URL = "/N26/sign";
    private static final String NATIONWIDE_SIGNING_URL = "/Nationwide/sign";
    private static final String SIGNING_KEY_ID_NAME = "signing_key_id";
    private static final String DEV_PORTAL = "dev-portal";
    private static final String ASSISTANCE_PORTAL = "assistance-portal-yts";

    final UUID clientGroupId = UUID.randomUUID();
    final UUID clientId = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private SigningService signingService;

    @Autowired
    private TestClientTokens testClientTokens;

    @Test
    public void shouldReturnSignedValue() throws Exception {
        // given
        when(signingService.signPayload(eq(PAYLOAD), eq(SIGNING_KEY_ID), eq(JVM_ALGORITHM), any(ClientToken.class)))
                .thenReturn(SIGNED_PAYLOAD);

        // when
        Consumer<JwtClaims> mutator = claims -> claims.setClaim(EXTRA_CLAIM_ISSUED_FOR, DEV_PORTAL);
        ClientToken devPortalToken = testClientTokens.createClientToken(clientGroupId, clientId, mutator);
        MockHttpServletResponse response = this.mockMvc.perform(post(SIGNING_URL)
                        .content(PAYLOAD)
                        .queryParam(SIGNING_KEY_ID_NAME, SIGNING_KEY_ID)
                        .header(ClientTokenConstants.CLIENT_TOKEN_HEADER_NAME, devPortalToken.getSerialized()))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(canParseJwt(response.getContentAsString())).isTrue();
        assertThat(PAYLOAD.matches(PATTERN)).isTrue();
        assertThat(INVALID_PAYLOAD.matches(PATTERN)).isFalse();
    }

    @Test
    public void shouldReturnSignedNationwideValue() throws Exception {
        // given
        when(signingService.signNationwidePayload(eq(NATIONWIDE_PAYLOAD), eq(SIGNING_KEY_ID), eq(JVM_ALGORITHM_FOR_NATIONWIDE), any(ClientToken.class)))
                .thenReturn(SIGNED_PAYLOAD);

        // when
        Consumer<JwtClaims> mutator = claims -> claims.setClaim(EXTRA_CLAIM_ISSUED_FOR, ASSISTANCE_PORTAL);
        ClientToken assistancePortalToken = testClientTokens.createClientToken(clientGroupId, clientId, mutator);
        MockHttpServletResponse response = this.mockMvc.perform(post(NATIONWIDE_SIGNING_URL)
                        .content(NATIONWIDE_PAYLOAD)
                        .queryParam(SIGNING_KEY_ID_NAME, SIGNING_KEY_ID)
                        .header(ClientTokenConstants.CLIENT_TOKEN_HEADER_NAME, assistancePortalToken.getSerialized()))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(canParseJwt(response.getContentAsString())).isTrue();
    }

    private boolean canParseJwt(String jwt) {
        try {
            JsonWebSignature.fromCompactSerialization(jwt);
            return true;
        } catch (JoseException e) {
            return false;
        }
    }
}
