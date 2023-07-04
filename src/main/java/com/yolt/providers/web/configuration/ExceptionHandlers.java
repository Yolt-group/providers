package com.yolt.providers.web.configuration;

import com.yolt.providers.common.exception.*;
import com.yolt.providers.common.pis.paymentexecutioncontext.exception.PaymentExecutionTechnicalException;
import com.yolt.providers.web.circuitbreaker.ProvidersCircuitBreakerException;
import com.yolt.providers.web.circuitbreaker.ProvidersNonCircuitBreakingTokenInvalidException;
import com.yolt.providers.web.errorhandling.ExtendedExceptionHandlingService;
import com.yolt.providers.web.exception.ClientConfigurationValidationException;
import com.yolt.providers.web.exception.ProviderNotFoundException;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.errorhandling.BaseErrorConstants;
import nl.ing.lovebird.errorhandling.ErrorDTO;
import nl.ing.lovebird.logging.SemaEventLogger;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.event.Level;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import static com.yolt.providers.web.configuration.ErrorConstants.*;
import static nl.ing.lovebird.errorhandling.BaseErrorConstants.GENERIC;
import static nl.ing.lovebird.errorhandling.BaseErrorConstants.METHOD_ARGUMENT_NOT_VALID;
import static org.slf4j.event.Level.*;

/**
 * Contains handlers for predefined exception.
 */
@RestControllerAdvice
@Slf4j
public class ExceptionHandlers {

    private static final String INVALID_INFO_EXCEPTION_TEMPLATE = "{} ({}): {}";
    final ExtendedExceptionHandlingService service;
    final String prefix;
    private final Map<Class<?>, BiFunction<HttpServletResponse, Throwable, ErrorDTO>> circuitBreakerExceptionHandlers = new HashMap<>();

    public ExceptionHandlers(
            final ExtendedExceptionHandlingService service,
            @Value("${yolt.commons.error-handling.prefix}") final String prefix) {
        this.service = service;
        this.prefix = prefix;

        circuitBreakerExceptionHandlers.put(TokenInvalidException.class, (response, exception) -> {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            String code = prefix + ACCESS_MEANS_INVALID.getCode();
            log.info(INVALID_INFO_EXCEPTION_TEMPLATE, ACCESS_MEANS_INVALID.getMessage(), code, exception.getMessage(), exception);
            return new ErrorDTO(code, ACCESS_MEANS_INVALID.getMessage());
        });

        circuitBreakerExceptionHandlers.put(FormDecryptionFailedException.class, (response, exception) -> {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            var stackTrace = ExceptionUtils.getStackTrace(exception);
            SemaEventLogger.log(new FormDecryptionFailedSEMaEvent("Decryption of an encrypted form failed" + stackTrace, null));
            return service.logAndConstruct(Level.WARN, FORM_COULD_NOT_BE_DECRYPTED, new MessageSuppressingException(exception));
        });

        circuitBreakerExceptionHandlers.put(CreationFailedException.class, (response, exception) -> {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            return service.logAndConstruct(Level.WARN, ErrorConstants.PAYMENT_CREATION_FAILED, exception);
        });

        circuitBreakerExceptionHandlers.put(ConfirmationRequestFailedException.class, (response, exception) -> {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return service.logAndConstruct(Level.WARN, ErrorConstants.PAYMENT_CONFIRMATION_BAD_REQUEST, exception);
        });

        circuitBreakerExceptionHandlers.put(ConfirmationFailedException.class, (response, exception) -> {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            return service.logAndConstruct(Level.WARN, ErrorConstants.PAYMENT_CONFIRMATION_FAILED, exception);
        });

        circuitBreakerExceptionHandlers.put(PaymentCancelledException.class, (response, exception) -> {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return service.logAndConstruct(Level.WARN, ErrorConstants.USER_CANCELLED_PAYMENT, exception);
        });

        circuitBreakerExceptionHandlers.put(PaymentExecutionTechnicalException.class, (response, exception) -> {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            String code = prefix + PAYMENT_EXECUTION_TECHNICAL_ERROR.getCode();
            log.warn(INVALID_INFO_EXCEPTION_TEMPLATE, PAYMENT_EXECUTION_TECHNICAL_ERROR.getMessage(), code, exception.getMessage(), exception);
            return new ErrorDTO(code, exception.getMessage());
        });

        circuitBreakerExceptionHandlers.put(MissingAuthenticationMeansException.class, (response, exception) -> {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            return service.logAndConstruct(Level.WARN, MISSING_AUTHENTICATION_MEANS, exception);
        });

        circuitBreakerExceptionHandlers.put(ClientConfigurationValidationException.class, (response, exception) -> {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            String code = prefix + CLIENT_CONFIGURATION_VALIDATION_ERROR.getCode();
            log.error(INVALID_INFO_EXCEPTION_TEMPLATE, CLIENT_CONFIGURATION_VALIDATION_ERROR.getMessage(), code, exception.getMessage(), exception);
            // Do not present details to external clients, but do show message
            return new ErrorDTO(code, exception.getMessage());
        });

        circuitBreakerExceptionHandlers.put(UnsupportedProviderException.class, (response, exception) -> {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return service.logAndConstruct(Level.WARN, ErrorConstants.UNSUPPORTED_PROVIDER, exception);
        });

        circuitBreakerExceptionHandlers.put(UnknownProviderVersionException.class, (response, exception) -> {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return service.logAndConstruct(Level.WARN, ErrorConstants.UNKNOWN_PROVIDER_VERSION, exception);
        });

        circuitBreakerExceptionHandlers.put(AutoOnboardingException.class, (response, exception) -> {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return service.logAndConstruct(Level.WARN, AUTO_ONBOARDING_ERROR, exception);
        });

        circuitBreakerExceptionHandlers.put(AuthenticationMeanValidationException.class, (response, exception) -> {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return service.logAndConstruct(Level.WARN, AUTHENTICATION_MEANS_VALIDATION_ERROR, exception);
        });

        circuitBreakerExceptionHandlers.put(UnrecognizableAuthenticationMeanKey.class, (response, exception) -> {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return service.logAndConstruct(Level.WARN, AUTHENTICATION_MEANS_UNRECOGNIZED_KEY, exception);
        });

        circuitBreakerExceptionHandlers.put(UnsupportedOperationException.class, (response, exception) -> {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return service.logAndConstruct(Level.WARN, GENERIC, exception);
        });

        circuitBreakerExceptionHandlers.put(TypeMismatchException.class, (response, exception) -> {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return service.logAndConstruct(ERROR, BaseErrorConstants.GENERIC, exception);
        });

        circuitBreakerExceptionHandlers.put(MissingServletRequestPartException.class, (response, exception) -> {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return service.logAndConstruct(ERROR, BaseErrorConstants.GENERIC, exception);
        });

        circuitBreakerExceptionHandlers.put(BindException.class, (response, exception) -> {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return service.logAndConstruct(ERROR, BaseErrorConstants.GENERIC, exception);
        });

        circuitBreakerExceptionHandlers.put(BackPressureRequestException.class, (response, exception) -> {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            return service.logAndConstruct(INFO, BACK_PRESSURE_REQUEST, exception);
        });

        circuitBreakerExceptionHandlers.put(HttpClientErrorException.class, (response, exception) -> {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            return service.logAndConstruct(WARN, BACK_PRESSURE_REQUEST, exception);
        });

        circuitBreakerExceptionHandlers.put(ProviderNotFoundException.class, (response, exception) -> {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return service.logAndConstruct(WARN, BaseErrorConstants.METHOD_ARGUMENT_NOT_VALID, exception);
        });

        circuitBreakerExceptionHandlers.put(ExternalUserSiteDoesNotExistException.class, (response, exception) -> {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            return service.logAndConstruct(ERROR, ErrorConstants.USER_DOES_NOT_EXIST, exception);
        });

        circuitBreakerExceptionHandlers.put(FormProviderRequestFailedException.class, (response, exception) -> {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            return service.logAndConstruct(WARN, BaseErrorConstants.GENERIC, exception);
        });

        circuitBreakerExceptionHandlers.put(PaymentValidationException.class, (response, exception) -> {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return service.logAndConstruct(WARN, METHOD_ARGUMENT_NOT_VALID, ((PaymentValidationException) exception).getInfo(), exception);
        });

    }

    @ExceptionHandler(UnsupportedProviderException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDTO handle(UnsupportedProviderException ex) {
        return service.logAndConstruct(Level.WARN, ErrorConstants.UNSUPPORTED_PROVIDER, ex);
    }

    @ExceptionHandler(UnknownProviderVersionException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDTO handle(UnknownProviderVersionException ex) {
        return service.logAndConstruct(Level.WARN, ErrorConstants.UNKNOWN_PROVIDER_VERSION, ex);
    }

    @ExceptionHandler(ClientConfigurationValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDTO handle(ClientConfigurationValidationException ex) {
        String code = prefix + CLIENT_CONFIGURATION_VALIDATION_ERROR.getCode();
        log.error(INVALID_INFO_EXCEPTION_TEMPLATE, CLIENT_CONFIGURATION_VALIDATION_ERROR.getMessage(), code, ex.getMessage(), ex);
        // Do not present details to external clients, but do show message
        return new ErrorDTO(code, ex.getMessage());
    }

    @ExceptionHandler(AutoOnboardingException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDTO handle(AutoOnboardingException ex) {
        return service.logAndConstruct(Level.WARN, ErrorConstants.AUTO_ONBOARDING_ERROR, ex);
    }

    @ExceptionHandler(MissingAuthenticationMeansException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorDTO handle(MissingAuthenticationMeansException ex) {
        return service.logAndConstruct(Level.WARN, ErrorConstants.MISSING_AUTHENTICATION_MEANS, ex);
    }

    @ExceptionHandler(ProvidersCircuitBreakerException.class)
    public ErrorDTO handle(ProvidersCircuitBreakerException ex, final HttpServletResponse response) {
        return handleCircuitBreakerExceptionTree(ex, response);
    }

    @ExceptionHandler(ProvidersNonCircuitBreakingTokenInvalidException.class)
    public ErrorDTO handle(ProvidersNonCircuitBreakingTokenInvalidException ex, final HttpServletResponse response) {
        return handleCircuitBreakerExceptionTree(ex, response);
    }

    @ExceptionHandler(AuthenticationMeanValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDTO handle(AuthenticationMeanValidationException ex) {
        return service.logAndConstruct(Level.WARN, ErrorConstants.AUTHENTICATION_MEANS_VALIDATION_ERROR, ex);
    }

    @ExceptionHandler(UnrecognizableAuthenticationMeanKey.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDTO handle(UnrecognizableAuthenticationMeanKey ex) {
        return service.logAndConstruct(Level.WARN, ErrorConstants.AUTHENTICATION_MEANS_UNRECOGNIZED_KEY, ex);
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDTO handle(UnsupportedOperationException ex) {
        return service.logAndConstruct(Level.WARN, GENERIC, ex);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(TypeMismatchException.class)
    public ErrorDTO handleTypeMismatchException(final TypeMismatchException ex) {
        return service.logAndConstruct(ERROR, BaseErrorConstants.GENERIC, ex);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ErrorDTO handleMissingServletRequestPartException(final MissingServletRequestPartException ex) {
        return service.logAndConstruct(ERROR, BaseErrorConstants.GENERIC, ex);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(BindException.class)
    public ErrorDTO handleBindException(final BindException ex) {
        return service.logAndConstruct(ERROR, BaseErrorConstants.GENERIC, ex);
    }

    /**
     * ExternalUserSiteDoesNotExistException is only thrown on form-provider/refresh-access-means. This doesn't make sense because for form-providres the accessmeans are not tight to a
     * user-site. It should actually be 'externalUserDoesNotExist'..
     * We make this clear in the external interface by just saying that the user does not exist.
     * So in short: The API is fine, the dataProvider interface isn't.
     */
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(ExternalUserSiteDoesNotExistException.class)
    public ErrorDTO handleExternalUserSiteDoesNotExistException(final ExternalUserSiteDoesNotExistException ex) {
        return service.logAndConstruct(ERROR, ErrorConstants.USER_DOES_NOT_EXIST, ex);
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(FormProviderRequestFailedException.class)
    public ErrorDTO handleFormProviderRequestFailedException(final FormProviderRequestFailedException ex) {
        return service.logAndConstruct(Level.WARN, BaseErrorConstants.GENERIC, ex);
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(ProviderNotFoundException.class)
    public ErrorDTO handleProviderNotFoundException(final ProviderNotFoundException ex) {
        return service.logAndConstruct(Level.WARN, BaseErrorConstants.METHOD_ARGUMENT_NOT_VALID, ex);
    }

    @ExceptionHandler(HttpClientErrorException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorDTO handle(HttpClientErrorException ex) {
        return service.logAndConstruct(Level.WARN, GENERIC, ex);
    }

    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    @ExceptionHandler(BackPressureRequestException.class)
    public ErrorDTO handleBackPressureRequestException(final BackPressureRequestException ex) {
        return service.logAndConstruct(INFO, BACK_PRESSURE_REQUEST, ex);
    }

    private ErrorDTO handleCircuitBreakerExceptionTree(Throwable ex, HttpServletResponse response) {
        BiFunction<HttpServletResponse, Throwable, ErrorDTO> defaultHandler = (httpServletResponse, e) -> {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            return service.logAndConstruct(Level.WARN, GENERIC, e);
        };

        while (ex.getCause() != null && !circuitBreakerExceptionHandlers.containsKey(ex.getClass())) {
            ex = ex.getCause();
        }

        return circuitBreakerExceptionHandlers.getOrDefault(ex.getClass(), defaultHandler).apply(response, ex);
    }
}
