package com.yolt.providers.web.service;

import com.yolt.providers.web.cryptography.signing.JcaSigner;
import com.yolt.providers.web.cryptography.signing.JcaSignerFactory;
import com.yolt.providers.web.exception.InvalidInputException;
import com.yolt.securityutils.signing.SignatureAlgorithm;
import lombok.SneakyThrows;
import nl.ing.lovebird.clienttokens.ClientToken;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SigningServiceTest {

    private static final UUID SIGNING_KEY_ID = UUID.randomUUID();

    @Mock
    private JcaSignerFactory jcaSignerFactory;

    private SigningService service;

    @Test
    public void signNationwidePayload() {
        //given
        service = new SigningService(Clock.systemUTC(), jcaSignerFactory);
        ClientToken clientToken = mock(ClientToken.class);
        JcaSigner jcaSigner = mock(JcaSigner.class);
        when(jcaSignerFactory.getForClientToken(clientToken)).thenReturn(jcaSigner);
        String correct_payload = readFromClasspath("CORRECT_PAYLOAD");
        when(jcaSigner.sign(correct_payload.getBytes(), SIGNING_KEY_ID, SignatureAlgorithm.SHA256_WITH_RSA_PSS)).thenReturn("signature");

        //when
        String signature = service.signNationwidePayload(correct_payload, SIGNING_KEY_ID.toString(), SignatureAlgorithm.SHA256_WITH_RSA_PSS.getJvmAlgorithm(), clientToken);

        //then
        assertThat(signature).isEqualTo("signature");
    }

    @ParameterizedTest
    @MethodSource("nationwideValidationParameters")
    public void signNationwidePayloadWhenIncorrectPayload(String expectedErrorMessage, String payload) {
        //given
        service = new SigningService(Clock.systemUTC(), jcaSignerFactory);
        ClientToken clientToken = mock(ClientToken.class);

        //when
        ThrowableAssert.ThrowingCallable signCallable = () -> service.signNationwidePayload(payload, SIGNING_KEY_ID.toString(), SignatureAlgorithm.SHA256_WITH_RSA_PSS.getJvmAlgorithm(), clientToken);

        // then
        assertThatThrownBy(signCallable)
                .isInstanceOf(InvalidInputException.class)
                .hasMessage(expectedErrorMessage);
    }

    private static Stream<Arguments> nationwideValidationParameters() {
        return Stream.of(
                Arguments.of("Payload too big. Max 10000 characters", readFromClasspath("INVALID_SIZE")),
                Arguments.of("Invalid payload json form", readFromClasspath("INVALID_JSON_FORMAT")),
                Arguments.of("Invalid software_statement form, it should be jws", readFromClasspath("SSA_IN_WRONG_FORMAT")),
                Arguments.of("Missing ssa", readFromClasspath("SSA_MISSING")),
                Arguments.of("No grantTypes defined", readFromClasspath("GRANT_TYPES_EMPTY")),
                Arguments.of("Missing grantTypes", readFromClasspath("GRANT_TYPES_MISSING")),
                Arguments.of("Missing grantTypes", readFromClasspath("GRANT_TYPES_WRONG_TYPE")),
                Arguments.of("Unknown grantType: invalid_grant_type", readFromClasspath("INVALID_GRANT_TYPE")),
                Arguments.of("Missing scope", readFromClasspath("SCOPES_MISSING")),
                Arguments.of("scope is empty", readFromClasspath("SCOPES_EMPTY")),
                Arguments.of("iss is empty", readFromClasspath("EMPTY_ISS")),
                Arguments.of("Missing iss", readFromClasspath("MISSING_ISS")),
                Arguments.of("Exp should be future date", readFromClasspath("PAST_EXP")),
                Arguments.of("Missing exp", readFromClasspath("MISSING_EXP")),
                Arguments.of("Iat should be past date", readFromClasspath("FUTURE_IAT")),
                Arguments.of("Missing iat", readFromClasspath("MISSING_IAT")),
                Arguments.of("Missing token_endpoint_auth_method", readFromClasspath("MISSING_AUTH_METHOD")),
                Arguments.of("token_endpoint_auth_method is empty", readFromClasspath("EMPTY_AUTH_METHOD")),
                Arguments.of("Missing redirectUris", readFromClasspath("MISSING_REDIRECT_URIS")),
                Arguments.of("No redirect_uris defined", readFromClasspath("EMPTY_REDIRECT_URIS")),
                Arguments.of("Missing redirectUris", readFromClasspath("REDIRECT_URIS_WRONG_TYPE")),
                Arguments.of("Unknown redirectUri: not_a_uri", readFromClasspath("INVALID_REDIRECT_URI")),
                Arguments.of("Missing software_id", readFromClasspath("MISSING_SOFTWARE_ID")),
                Arguments.of("software_id is empty", readFromClasspath("EMPTY_SOFTWARE_ID")),
                Arguments.of("Missing software_id", readFromClasspath("SOFTWARE_ID_WRONG_TYPE")),
                Arguments.of("Missing ssa", readFromClasspath("SOFTWARE_STATEMENT_WRONG_TYPE")),
                Arguments.of("Payload is null", null)
        );
    }

    @SneakyThrows
    private static String readFromClasspath(String resource){
        return StreamUtils.copyToString(SigningServiceTest.class.getClassLoader().getResourceAsStream("payloadvalidation/" + resource + ".json"), StandardCharsets.UTF_8);
    }
}
