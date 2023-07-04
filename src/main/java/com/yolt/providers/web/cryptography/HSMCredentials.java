package com.yolt.providers.web.cryptography;

import lombok.Value;

@Value
public class HSMCredentials {
    private String partition;
    private String username;
    private String password;
}
