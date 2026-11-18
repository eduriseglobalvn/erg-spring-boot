package com.erg.ergspringboot.help.web;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.help.HelpFaqQuery;
import com.erg.ergspringboot.help.HelpFaqQuery.HelpFaq;
import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FAQ for the «Hướng dẫn» page. Any signed-in user may read it (SecurityConfig: /api/v1/** authenticated);
 * the content is the same product help text for everyone, so nothing beyond sign-in is checked.
 */
@RestController
@RequestMapping("/api/v1/help")
public class HelpFaqController {

    private final HelpFaqQuery query;

    public HelpFaqController(HelpFaqQuery query) {
        this.query = query;
    }

    @GetMapping("/faqs")
    public List<HelpFaq> faqs(@RequestParam String portal) {
        String normalized = portal.trim().toUpperCase(Locale.ROOT);
        if (!HelpFaqQuery.PORTALS.contains(normalized)) {
            throw new BadRequestException("portal must be one of " + HelpFaqQuery.PORTALS);
        }
        return query.activeFaqs(normalized);
    }
}
