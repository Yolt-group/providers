package com.yolt.providers.web.controller;

import com.yolt.providers.web.service.ProviderInfoService;
import com.yolt.providers.web.service.domain.ProviderInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * End-point for getting provider info.
 */
@RestController
@RequestMapping(value = "provider-info", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
@RequiredArgsConstructor
public class ProviderInfoController {

    private final ProviderInfoService providerInfoService;

    @GetMapping
    public Map<String, ProviderInfo> getProvidersInfo() {
        return providerInfoService.getProvidersInfo();
    }

    @GetMapping("{providerKey}")
    public ResponseEntity<ProviderInfo> getProviderInfo(@PathVariable("providerKey") String providerKey) {
        return providerInfoService.getProviderInfo(providerKey)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}