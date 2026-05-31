package com.erg.ergspringboot.service.hoclieu;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "erg.lifecycle", name = "hoclieu-seed-on-startup", havingValue = "true")
public class HocLieuBootstrapSeedInitializer implements ApplicationRunner {

    private final HocLieuBootstrapSeedService hocLieuBootstrapSeedService;

    public HocLieuBootstrapSeedInitializer(HocLieuBootstrapSeedService hocLieuBootstrapSeedService) {
        this.hocLieuBootstrapSeedService = hocLieuBootstrapSeedService;
    }

    @Override
    public void run(ApplicationArguments args) {
        hocLieuBootstrapSeedService.seedDefaultLibrary();
    }
}
