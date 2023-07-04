package com.yolt.providers.web.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class NoSupportedAccountDTO {
    private final UUID userId;
    private final UUID userSiteId;
}
