package com.yolt.providers.web.controller.dto;

import lombok.Value;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotNull;
import java.util.UUID;

@Value
public class RabobankTppEnrollmentDTO {
    @NotNull @Email String email;
    @NotNull UUID kid;
    @NotNull String qseal;
}
