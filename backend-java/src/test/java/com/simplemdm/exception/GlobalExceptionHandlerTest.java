package com.simplemdm.exception;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders
        .standaloneSetup(new ThrowingController())
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();

    @ParameterizedTest
    @CsvSource({
        "400, badRequest",
        "403, forbidden",
        "404, missing"
    })
    void mapsBusinessCodeToHttpStatus(int code, String path) throws Exception {
        mockMvc.perform(get("/errors/" + path))
            .andExpect(status().is(code))
            .andExpect(jsonPath("$.code").value(code));
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/errors/badRequest")
        void badRequest() {
            throw new BusinessException(400, "bad request");
        }

        @GetMapping("/errors/forbidden")
        void forbidden() {
            throw new BusinessException(403, "forbidden");
        }

        @GetMapping("/errors/missing")
        void missing() {
            throw new BusinessException(404, "missing");
        }
    }
}
