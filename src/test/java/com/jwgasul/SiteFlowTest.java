// SiteFlowTest.java — 현장 목록/등록/중복/종료·재개 통합 검증(F-05)
package com.jwgasul;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jwgasul.dto.SiteForm;
import com.jwgasul.service.SiteService;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class SiteFlowTest {

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SiteService siteService;

    private String uniqueName() {
        return "테스트현장-" + SEQ.incrementAndGet();
    }

    @Test
    void listRenders() throws Exception {
        mockMvc.perform(get("/sites"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("sites"));
    }

    @Test
    void createSiteRedirectsToDetail() throws Exception {
        mockMvc.perform(post("/sites").with(csrf())
                        .param("name", uniqueName())
                        .param("clientName", "OO건설")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/sites/*"));
    }

    @Test
    void duplicateNameReturnsForm() throws Exception {
        String name = uniqueName();
        SiteForm f = new SiteForm();
        f.setName(name);
        siteService.create(f); // 먼저 등록

        mockMvc.perform(post("/sites").with(csrf()).param("name", name))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("siteForm", "name"));
    }

    @Test
    void deactivateThenActivate() throws Exception {
        SiteForm f = new SiteForm();
        f.setName(uniqueName());
        Long id = siteService.create(f).getId();

        mockMvc.perform(post("/sites/" + id + "/deactivate").with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertFalse(siteService.get(id).isActive());

        mockMvc.perform(post("/sites/" + id + "/activate").with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertTrue(siteService.get(id).isActive());
    }

    @Test
    void activeTabFiltersByStatus() {
        SiteForm a = new SiteForm();
        a.setName(uniqueName());
        a.setActive(true);
        Long activeId = siteService.create(a).getId();

        SiteForm i = new SiteForm();
        i.setName(uniqueName());
        i.setActive(false);
        Long inactiveId = siteService.create(i).getId();

        var actives = siteService.list(true);
        assertTrue(actives.stream().anyMatch(s -> s.getId().equals(activeId)));
        assertFalse(actives.stream().anyMatch(s -> s.getId().equals(inactiveId)));
    }
}
