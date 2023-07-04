package com.yolt.providers.web.errorhandling;

import com.yolt.providers.common.exception.dto.DetailedErrorInformation;
import lombok.Getter;
import nl.ing.lovebird.errorhandling.ErrorDTO;

import java.beans.ConstructorProperties;

public class ExtendedErrorDTO extends ErrorDTO {
    @Getter
    private DetailedErrorInformation detailedInformation;

    @ConstructorProperties({"code", "message", "detailedErrorInformation"})
    public ExtendedErrorDTO(String code, String message, DetailedErrorInformation detailedErrorInformation) {
        super(code, message);
        this.detailedInformation = detailedErrorInformation;
    }

}
