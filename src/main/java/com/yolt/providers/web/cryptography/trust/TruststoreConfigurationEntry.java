package com.yolt.providers.web.cryptography.trust;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;

@Data
@Valid
@NoArgsConstructor
@AllArgsConstructor
class TruststoreConfigurationEntry {
    /**
     * hostname
     */
    @NotEmpty
    private String hostname;

    /**
     * base64 encoded der
     */
    @NotEmpty
    private String cert;

    /**
     * 0 = leaf cert
     * 1 = intermediate
     * n = intermediate or root
     */
    private int depth;
}