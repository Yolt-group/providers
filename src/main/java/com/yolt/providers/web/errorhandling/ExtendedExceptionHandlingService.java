package com.yolt.providers.web.errorhandling;


import com.yolt.providers.common.exception.dto.DetailedErrorInformation;
import lombok.RequiredArgsConstructor;
import nl.ing.lovebird.errorhandling.ErrorDTO;
import nl.ing.lovebird.errorhandling.ErrorInfo;
import nl.ing.lovebird.errorhandling.ExceptionHandlingService;
import org.slf4j.event.Level;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExtendedExceptionHandlingService {

    private final ExceptionHandlingService exceptionHandlingService;

    public ExtendedErrorDTO logAndConstruct(Level errorLogLevel, ErrorInfo error, DetailedErrorInformation detailedErrorInformation, Throwable exception) {
        exceptionHandlingService.logAndConstruct(errorLogLevel, error, exception);
        String message = error.getMessage();
        return new ExtendedErrorDTO(exceptionHandlingService.code(error), message, detailedErrorInformation);
    }

    public ErrorDTO logAndConstruct(Level errorLogLevel, ErrorInfo error, Throwable exception) {
        return exceptionHandlingService.logAndConstruct(errorLogLevel, error, exception);
    }
}
