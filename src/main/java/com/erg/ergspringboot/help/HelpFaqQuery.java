package com.erg.ergspringboot.help;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/** Reads the active FAQ rows of one portal. The table is global product help, not tenant data. */
@Service
public class HelpFaqQuery {

    public static final List<String> PORTALS = List.of("ADMIN", "TEACHER", "STUDENT", "CRM");

    public record HelpFaq(String id, String category, String question, String answer) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public HelpFaqQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<HelpFaq> activeFaqs(String portal) {
        return jdbc.query(
                "select id, category, question, answer from help_faqs"
                        + " where portal = :portal and active = true order by sort_order, id",
                Map.of("portal", portal),
                (rs, row) -> new HelpFaq(rs.getString("id"), rs.getString("category"),
                        rs.getString("question"), rs.getString("answer")));
    }
}
