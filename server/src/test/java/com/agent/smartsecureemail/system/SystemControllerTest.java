package com.agent.smartsecureemail.system;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Each domain profile must report its own identity: the basis of two-domain deployment. */
class SystemControllerTest {

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles({"test", "domain-a"})
    class DomainA {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void reportsDomainA() throws Exception {
            mockMvc.perform(get("/api/v1/system/info"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.domain").value("domain-a.test"))
                    .andExpect(jsonPath("$.data.apiVersion").value("v1"))
                    .andExpect(jsonPath("$.error").doesNotExist());
        }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles({"test", "domain-b"})
    class DomainB {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void reportsDomainB() throws Exception {
            mockMvc.perform(get("/api/v1/system/info"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.domain").value("domain-b.test"))
                    .andExpect(jsonPath("$.data.displayName").value("Domain B Corp"));
        }
    }
}
