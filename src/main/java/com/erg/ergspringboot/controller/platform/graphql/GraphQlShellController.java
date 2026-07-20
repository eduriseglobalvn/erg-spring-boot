package com.erg.ergspringboot.controller.platform.graphql;

import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class GraphQlShellController {

    @QueryMapping
    public LmsQuery lms() {
        return new LmsQuery("lms", "ready");
    }

    @QueryMapping
    public LcmsQuery lcms() {
        return new LcmsQuery("lcms", "ready");
    }

    @QueryMapping
    public PlatformNamespace crm() {
        return new PlatformNamespace("crm", "ready");
    }

    @QueryMapping
    public PlatformNamespace elearning() {
        return new PlatformNamespace("elearning", "ready");
    }

    public record LmsQuery(String namespace, String status) {
    }

    public record LcmsQuery(String namespace, String status) {
    }

    public record PlatformNamespace(String namespace, String status) {
    }
}
