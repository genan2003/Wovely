package com.wovely.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The shared error contract: correct status codes and never a leaked internal message. */
class GlobalExceptionHandlerTest {

    @RestController
    static class Probe {
        @GetMapping("/thing") String get(@RequestParam String id) { return id; }
        @PostMapping(value = "/thing", consumes = "application/json") String post(@RequestBody String b) { return b; }
        @GetMapping("/conflict") String conflict() { throw ApiException.conflict("Insufficient stock"); }
        @GetMapping("/boom") String boom() { throw new IllegalStateException("secret internals"); }
    }

    final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Probe()).setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void mapsFrameworkErrorsToProperStatuses() throws Exception {
        mvc.perform(get("/thing")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Missing request parameter: id"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/thing"))
            .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.status").value(405));
        mvc.perform(post("/thing").contentType("text/plain").content("x")).andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void apiExceptionsKeepTheirMessageUnexpectedOnesDoNot() throws Exception {
        mvc.perform(get("/conflict")).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Insufficient stock"));
        mvc.perform(get("/boom")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }
}
