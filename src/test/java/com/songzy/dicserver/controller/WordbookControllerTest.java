package com.songzy.dicserver.controller;

import com.songzy.dicserver.exception.BusinessException;
import com.songzy.dicserver.exception.GlobalExceptionHandler;
import com.songzy.dicserver.security.CurrentUserPrincipal;
import com.songzy.dicserver.service.WordbookService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WordbookControllerTest {

    @Mock
    private WordbookService wordbookService;

    @InjectMocks
    private WordbookController wordbookController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(wordbookController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @Test
    void createWordbookShouldPassAuthenticatedUserIdToService() throws Exception {
        runAsUser(() -> mockMvc.perform(post("/api/v1/wordbooks")
                .param("name", "Default"))
                .andExpect(status().isOk()));

        verify(wordbookService).createWordbook(7L, "Default");
    }

    @Test
    void deleteWordbookShouldReturnOk() throws Exception {
        runAsUser(() -> mockMvc.perform(delete("/api/v1/wordbooks/1"))
                .andExpect(status().isOk()));

        verify(wordbookService).deleteWordbook(7L, 1L);
    }

    @Test
    void deleteWordbookShouldReturnStableBusinessErrorResponse() throws Exception {
        doThrow(new BusinessException("单词本不存在", "WORDBOOK_NOT_FOUND"))
                .when(wordbookService)
                .deleteWordbook(7L, 1L);

        runAsUser(() -> mockMvc.perform(delete("/api/v1/wordbooks/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WORDBOOK_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("单词本不存在")));
    }

    @Test
    void addWordsShouldTrimInputsBeforeDelegating() throws Exception {
        runAsUser(() -> mockMvc.perform(post("/api/v1/wordbooks/1/words")
                .contentType("application/json")
                .content("[\"  Visible  \",\"test \"]"))
                .andExpect(status().isOk()));

        verify(wordbookService).addWordsToWordbook(7L, 1L, List.of("Visible", "test"));
        verifyNoMoreInteractions(wordbookService);
    }

    @Test
    void findWordbooksContainingWordShouldTrimQueryBeforeDelegating() throws Exception {
        runAsUser(() -> mockMvc.perform(get("/api/v1/wordbooks/containing")
                .param("word", "  Visible  "))
                .andExpect(status().isOk()));

        verify(wordbookService).findWordbooksContainingWord(7L, "Visible");
    }

    private void runAsUser(ThrowingRunnable action) throws Exception {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentUserPrincipal(7L, "alice"),
                "access-token",
                List.of()
        ));
        SecurityContextHolder.setContext(context);
        try {
            action.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
