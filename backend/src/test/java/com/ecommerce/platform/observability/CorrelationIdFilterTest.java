package com.ecommerce.platform.observability;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorrelationIdFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void validCorrelationIdIsEchoedAndMdcCleared() throws Exception {
        String id = "req-abc_123";
        MvcResult result = mockMvc.perform(get("/actuator/health").header(CorrelationIdFilter.HEADER, id))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.HEADER, id))
                .andReturn();
        assertThat(result.getResponse().getHeader(CorrelationIdFilter.HEADER)).isEqualTo(id);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void invalidCorrelationIdIsIgnoredAndReplaced() throws Exception {
        MvcResult result = mockMvc.perform(get("/actuator/health")
                        .header(CorrelationIdFilter.HEADER, "bad id with spaces!!!"))
                .andExpect(status().isOk())
                .andReturn();
        String generated = result.getResponse().getHeader(CorrelationIdFilter.HEADER);
        assertThat(generated).isNotBlank();
        assertThat(generated).doesNotContain(" ");
        assertThat(CorrelationIdFilter.isValidCorrelationId(generated)).isTrue();
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void missingCorrelationIdGeneratesValidId() throws Exception {
        MvcResult result = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(CorrelationIdFilter.isValidCorrelationId(
                result.getResponse().getHeader(CorrelationIdFilter.HEADER))).isTrue();
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
