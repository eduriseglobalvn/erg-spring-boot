package com.erg.ergspringboot.help.web;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.erg.ergspringboot.exception.GlobalExceptionHandler;
import com.erg.ergspringboot.help.HelpFaqQuery;
import com.erg.ergspringboot.help.HelpFaqQuery.HelpFaq;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HelpFaqControllerTest {

    private final HelpFaqQuery query = mock(HelpFaqQuery.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new HelpFaqController(query))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void returnsOnlyTheRequestedPortalsFaqs() throws Exception {
        when(query.activeFaqs("STUDENT")).thenReturn(List.of(
                new HelpFaq("faq-student-01", "Đăng nhập", "Em quên mật khẩu?", "1. Báo giáo viên.")));

        mockMvc.perform(get("/api/v1/help/faqs").param("portal", "student"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("faq-student-01"))
                .andExpect(jsonPath("$[0].question").value("Em quên mật khẩu?"));

        verify(query).activeFaqs("STUDENT");
        verify(query, never()).activeFaqs("ADMIN");
    }

    @Test
    void rejectsAnUnknownPortal() throws Exception {
        mockMvc.perform(get("/api/v1/help/faqs").param("portal", "ALL"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/help/faqs"))
                .andExpect(status().isBadRequest());

        verify(query, never()).activeFaqs(anyString());
    }
}
