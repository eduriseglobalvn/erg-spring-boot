/**
 * @author : name
 * */
package com.erg.ergspringboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;
import com.erg.ergspringboot.platform.nativeimage.ApiResponseNativeRuntimeHints;
import com.erg.ergspringboot.platform.nativeimage.ModulithNativeRuntimeHints;

@SpringBootApplication
@ImportRuntimeHints({ModulithNativeRuntimeHints.class, ApiResponseNativeRuntimeHints.class})
public class ErgSpringApplication {

    public static void main(String[] args) {
        SpringApplication.run(ErgSpringApplication.class, args);
    }

}
