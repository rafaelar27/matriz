package com.matriz.seed;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Loads fictitious demo data. Active only with the "seed" profile.
 *
 * <p>Idempotent: every row has a fixed id and is inserted with {@code ON CONFLICT DO NOTHING}, so running
 * the command again adds nothing, changes nothing and deletes nothing. All rows are inserted in one
 * transaction. Demo passwords come from {@code MATRIZ_DEMO_PASSWORD} and are stored as BCrypt hashes only.
 *
 * <p>The data follows the documented rules: every workspace has a Workspace Administrator (BR-04), task
 * owners are members of the task's project (BR-09), tasks are created by Project Managers or Team Members
 * (BR-12), and no record references another workspace (BR-18).
 */
@Component
@Profile("seed")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    // Accounts
    static final UUID ANA = id("a0000000-0000-0000-0000-000000000001");
    static final UUID BEN = id("a0000000-0000-0000-0000-000000000002");
    static final UUID VERA = id("a0000000-0000-0000-0000-000000000003");
    static final UUID CARLA = id("a0000000-0000-0000-0000-000000000004");
    /** Member of Northwind Lab only: cannot see any Acme Studio project (NFR-01). Added 2026-11-12 (INC-03). */
    static final UUID DAN = id("a0000000-0000-0000-0000-000000000005");
    /** Project Manager of Orchard Office only, which has no projects on purpose (E-15, INC-06). Added 2026-11-12. */
    static final UUID EVA = id("a0000000-0000-0000-0000-000000000006");
    /** Workspace Administrator of Orchard Office only, so the workspace keeps an administrator (BR-04, SP-07). Orchard Office has no projects, so the Dashboard empty state (E-15) can be checked with a Workspace Administrator, who sees "+ New project" since the decision of 2026-11-19 (PD-03 in part); Eva is the Project Manager. Added 2026-11-12. */
    static final UUID FINN = id("a0000000-0000-0000-0000-000000000007");

    // Workspaces
    static final UUID ACME = id("b0000000-0000-0000-0000-000000000001");
    static final UUID NORTHWIND = id("b0000000-0000-0000-0000-000000000002");
    static final UUID ORCHARD = id("b0000000-0000-0000-0000-000000000003");

    // Members (one per account per workspace)
    static final UUID ACME_CARLA = id("c0000000-0000-0000-0000-000000000001");
    static final UUID ACME_ANA = id("c0000000-0000-0000-0000-000000000002");
    static final UUID ACME_BEN = id("c0000000-0000-0000-0000-000000000003");
    static final UUID ACME_VERA = id("c0000000-0000-0000-0000-000000000004");
    static final UUID NW_CARLA = id("c0000000-0000-0000-0000-000000000005");
    static final UUID NW_ANA = id("c0000000-0000-0000-0000-000000000006");
    static final UUID NW_DAN = id("c0000000-0000-0000-0000-000000000007");
    static final UUID ORCHARD_FINN = id("c0000000-0000-0000-0000-000000000008");
    static final UUID ORCHARD_EVA = id("c0000000-0000-0000-0000-000000000009");

    // Projects
    static final UUID WEBSITE = id("d0000000-0000-0000-0000-000000000001");
    static final UUID TRADE_FAIR = id("d0000000-0000-0000-0000-000000000002");
    static final UUID LAB = id("d0000000-0000-0000-0000-000000000003");

    // Tasks
    static final UUID T_IMAGES = id("e0000000-0000-0000-0000-000000000001");
    static final UUID T_LAYOUT = id("e0000000-0000-0000-0000-000000000002");
    static final UUID T_PUBLISH = id("e0000000-0000-0000-0000-000000000003");
    static final UUID T_KICKOFF = id("e0000000-0000-0000-0000-000000000004");
    static final UUID T_EQUIPMENT = id("e0000000-0000-0000-0000-000000000005");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final String demoPassword;

    public DemoDataSeeder(JdbcTemplate jdbc, TransactionTemplate tx,
                          @Value("${MATRIZ_DEMO_PASSWORD:}") String demoPassword) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(String... args) {
        if (demoPassword.isBlank()) {
            throw new IllegalStateException("MATRIZ_DEMO_PASSWORD is not set; see .env.example.");
        }
        String hash = new BCryptPasswordEncoder().encode(demoPassword);
        int inserted = tx.execute(status -> seed(hash));
        log.info("Demo data: {} new rows inserted (0 means all demo data already existed).", inserted);
    }

    private int seed(String hash) {
        int n = 0;

        n += account(ANA, "Ana Demo", "ana@example.com", hash);
        n += account(BEN, "Ben Demo", "ben@example.com", hash);
        n += account(VERA, "Vera Demo", "vera@example.com", hash);
        n += account(CARLA, "Carla Demo", "carla@example.com", hash);
        n += account(DAN, "Dan Demo", "dan@example.com", hash);
        n += account(EVA, "Eva Demo", "eva@example.com", hash);
        n += account(FINN, "Finn Demo", "finn@example.com", hash);

        n += workspace(ACME, "Acme Studio");
        n += workspace(NORTHWIND, "Northwind Lab");
        n += workspace(ORCHARD, "Orchard Office");

        n += member(ACME_CARLA, ACME, CARLA, "WORKSPACE_ADMINISTRATOR");
        n += member(ACME_ANA, ACME, ANA, "PROJECT_MANAGER");
        n += member(ACME_BEN, ACME, BEN, "TEAM_MEMBER");
        n += member(ACME_VERA, ACME, VERA, "VIEWER");
        n += member(NW_CARLA, NORTHWIND, CARLA, "WORKSPACE_ADMINISTRATOR");
        n += member(NW_ANA, NORTHWIND, ANA, "TEAM_MEMBER");
        n += member(NW_DAN, NORTHWIND, DAN, "PROJECT_MANAGER");
        n += member(ORCHARD_FINN, ORCHARD, FINN, "WORKSPACE_ADMINISTRATOR");
        n += member(ORCHARD_EVA, ORCHARD, EVA, "PROJECT_MANAGER");

        n += project(WEBSITE, ACME, "Website redesign", "New company homepage.", ACME_ANA,
                LocalDate.parse("2026-11-01"), LocalDate.parse("2026-12-15"));
        n += project(TRADE_FAIR, ACME, "Trade fair stand", "Stand for the spring trade fair.", ACME_ANA,
                LocalDate.parse("2026-11-10"), LocalDate.parse("2027-03-01"));
        n += project(LAB, NORTHWIND, "Lab inventory", "Yearly equipment count.", NW_CARLA,
                LocalDate.parse("2026-11-15"), LocalDate.parse("2026-12-20"));

        n += projectMember(WEBSITE, ACME_ANA);
        n += projectMember(WEBSITE, ACME_BEN);
        n += projectMember(WEBSITE, ACME_VERA);
        n += projectMember(TRADE_FAIR, ACME_ANA);
        n += projectMember(TRADE_FAIR, ACME_BEN);
        n += projectMember(LAB, NW_CARLA);
        n += projectMember(LAB, NW_ANA);

        n += task(T_IMAGES, WEBSITE, "Review homepage images", ACME_BEN, LocalDate.parse("2026-11-04"),
                "IN_PROGRESS", ACME_ANA);
        n += task(T_LAYOUT, WEBSITE, "Approve page layout", ACME_ANA, LocalDate.parse("2026-11-20"),
                "TO_DO", ACME_ANA);
        n += task(T_PUBLISH, WEBSITE, "Publish new homepage", ACME_BEN, LocalDate.parse("2026-11-10"),
                "DONE", ACME_BEN);
        n += task(T_KICKOFF, TRADE_FAIR, "Write kickoff notes", ACME_BEN, null, "TO_DO", ACME_BEN);
        n += task(T_EQUIPMENT, LAB, "Count lab equipment", NW_ANA, LocalDate.parse("2026-11-30"),
                "TO_DO", NW_ANA);

        n += comment(id("f0000000-0000-0000-0000-000000000001"), T_IMAGES, ACME_ANA,
                "Please use the photos from the shared folder.", "2026-11-03T09:40:00Z");
        n += comment(id("f0000000-0000-0000-0000-000000000002"), T_IMAGES, ACME_BEN,
                "The first two sections are done.", "2026-11-03T14:05:00Z");
        n += comment(id("f0000000-0000-0000-0000-000000000003"), T_EQUIPMENT, NW_CARLA,
                "Start with the storage room.", "2026-11-16T10:00:00Z");

        return n;
    }

    private int account(UUID id, String name, String email, String hash) {
        return jdbc.update("INSERT INTO account (account_id, name, email, password_hash) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT DO NOTHING", id, name, email, hash);
    }

    private int workspace(UUID id, String name) {
        return jdbc.update("INSERT INTO workspace (workspace_id, name) VALUES (?, ?) ON CONFLICT DO NOTHING",
                id, name);
    }

    private int member(UUID id, UUID workspace, UUID account, String role) {
        return jdbc.update("INSERT INTO member (member_id, workspace_id, account_id, role, status) "
                + "VALUES (?, ?, ?, ?, 'ACTIVE') ON CONFLICT DO NOTHING", id, workspace, account, role);
    }

    private int project(UUID id, UUID workspace, String name, String description, UUID owner,
                        LocalDate start, LocalDate due) {
        return jdbc.update("INSERT INTO project (project_id, workspace_id, name, description, owner_member_id, "
                + "start_date, due_date, is_archived) VALUES (?, ?, ?, ?, ?, ?, ?, FALSE) ON CONFLICT DO NOTHING",
                id, workspace, name, description, owner, start, due);
    }

    private int projectMember(UUID project, UUID member) {
        return jdbc.update("INSERT INTO project_member (project_id, member_id) VALUES (?, ?) "
                + "ON CONFLICT DO NOTHING", project, member);
    }

    private int task(UUID id, UUID project, String title, UUID owner, LocalDate due, String status,
                     UUID createdBy) {
        return jdbc.update("INSERT INTO task (task_id, project_id, title, owner_member_id, due_date, status, "
                + "created_by_member_id) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                id, project, title, owner, due, status, createdBy);
    }

    private int comment(UUID id, UUID task, UUID author, String body, String createdAt) {
        return jdbc.update("INSERT INTO comment (comment_id, task_id, author_member_id, body, created_at) "
                + "VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                id, task, author, body, OffsetDateTime.parse(createdAt));
    }

    private static UUID id(String value) {
        return UUID.fromString(value);
    }
}
