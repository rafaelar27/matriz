package com.matriz;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

/**
 * Shared helpers for the workspace administration API tests (INC-17 invitations, INC-18 roles, removal and rename,
 * INC-19 delete). Adds the demo ids the base class does not hold. Demo data (see DemoDataSeeder): Acme Studio has
 * Carla (Workspace Administrator, the only one), Ana (Project Manager), Ben (Team Member) and Vera (Viewer);
 * Northwind Lab has Carla (Workspace Administrator), Ana (Team Member) and Dan (Project Manager); Orchard Office has
 * Finn (Workspace Administrator) and Eva (Project Manager).
 */
abstract class WorkspaceAdminTestSupport extends ApiTestSupport {

    static final String ACME = "b0000000-0000-0000-0000-000000000001";
    static final String NORTHWIND = "b0000000-0000-0000-0000-000000000002";
    static final String ORCHARD = "b0000000-0000-0000-0000-000000000003";
    static final String ACME_CARLA = "c0000000-0000-0000-0000-000000000001";
    static final String NW_ANA = "c0000000-0000-0000-0000-000000000006";
    static final String NW_DAN = "c0000000-0000-0000-0000-000000000007";
    static final String ORCHARD_FINN = "c0000000-0000-0000-0000-000000000008";
    static final String ORCHARD_EVA = "c0000000-0000-0000-0000-000000000009";
    static final String T_KICKOFF = "e0000000-0000-0000-0000-000000000004";
    static final String T_PUBLISH = "e0000000-0000-0000-0000-000000000003";

    @PersistenceContext
    EntityManager entityManager;

    /** Signs in the demo account with this first name, for example {@code as("carla")}. */
    MockHttpSession as(String firstName) throws Exception {
        return signIn(firstName + "@example.com");
    }

    /** Sends a request with a session and the CSRF token; {@code json} may be null for no body. */
    ResultActions send(MockHttpServletRequestBuilder builder, MockHttpSession session, String json) throws Exception {
        MockHttpServletRequestBuilder request = builder.session(session).with(csrf());
        if (json != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(request);
    }

    /** Writes pending changes and empties the persistence context, so the next request reads what SQL changed. */
    void fresh() {
        entityManager.flush();
        entityManager.clear();
    }

    long count(String sql, Object... args) {
        entityManager.flush(); // the services save through JPA; write pending changes before SQL reads
        return jdbc.queryForObject(sql, Long.class, args);
    }

    String text(String sql, Object... args) {
        entityManager.flush();
        return jdbc.queryForObject(sql, String.class, args);
    }

    /** Rows of the table with this workspace id. */
    long rowsOf(String table, String workspaceId) {
        return count("SELECT count(*) FROM " + table + " WHERE workspace_id = ?::uuid", workspaceId);
    }

    String membershipRole(MockHttpSession session, String workspaceId) throws Exception {
        String body = mvc.perform(get("/auth/me").session(session)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
        java.util.List<String> roles = JsonPath.read(body, "$.memberships[?(@.workspace_id=='" + workspaceId
                + "')].role");
        return roles.isEmpty() ? null : roles.get(0);
    }

    static String id(String json, String path) {
        return JsonPath.read(json, path);
    }
}
