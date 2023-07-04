package com.yolt.providers.web.configuration;

import com.datastax.driver.core.Session;
import com.datastax.driver.extras.codecs.enums.EnumNameCodec;
import com.yolt.providers.common.ProviderKey;
import lombok.AllArgsConstructor;
import nl.ing.lovebird.providerdomain.ServiceType;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * This config class makes sure all the generically used enums are only registered once, which prevents nasty overrides when not intended.
 */
@Configuration
@AllArgsConstructor
public class CassandraCodecsConfiguration {

    private final Session session;

    @PostConstruct
    public void onInit() {
        registerEnum(ProviderKey.class);
        registerEnum(ServiceType.class);
    }

    private <E extends Enum<E>> void registerEnum(Class<E> clazz) {
        this.session.getCluster().getConfiguration().getCodecRegistry().register(new EnumNameCodec<>(clazz));
    }
}
