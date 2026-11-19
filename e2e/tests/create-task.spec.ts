// E2E: sign in -> open a project -> create a task -> see it in the task list.
// Covers US-10 (AC-10.1), FR-01, FR-08, FR-09 to FR-13, NFR-03. Not part of `mvn verify`.
//
// Needs the application running with the demo data loaded (README, Getting started, steps 4 and 5),
// preferably against the test database. Environment variables (never written in code):
//   E2E_USER_EMAIL     demo account to sign in with; the test expects the demo user Ana
//                      (ana@example.com, Project Manager of Acme Studio)
//   E2E_USER_PASSWORD  its password (the MATRIZ_DEMO_PASSWORD given to the seed command)
//   E2E_BASE_URL       optional, default http://localhost:8080; the test refuses any host other than
//                      localhost or 127.0.0.1 unless E2E_ALLOW_NON_LOCAL=1 is set (it creates and deletes a task)
//   E2E_ALLOW_NON_LOCAL optional, set to 1 to allow a non-local E2E_BASE_URL
// The task created by the test is deleted afterwards through the interface.
import { expect, test, type Page } from '@playwright/test';

const email = process.env.E2E_USER_EMAIL;
const password = process.env.E2E_USER_PASSWORD;

const PROJECT = 'Website redesign';
const OWNER = 'Ben Demo';          // an active member of the project, other than the signed-in user
const DUE_DATE = '2026-12-01';
const DEFAULT_STATUS_LABEL = 'To do'; // interface label for "To do"; status left at its default

let title = '';
let created = false;

function taskRow(page: Page) {
  return page.getByRole('row').filter({ has: page.getByRole('button', { name: title, exact: true }) });
}

// Column position of a header in the Tasks table, read from the table's own column headers, so the
// assertions do not depend on the column order.
async function columnIndex(page: Page, header: string) {
  const headers = await tasksTable(page).getByRole('columnheader').allTextContents();
  const index = headers.map((text) => text.trim()).indexOf(header);
  expect(index, `Tasks table has no "${header}" column; found: ${headers.join(', ')}`).toBeGreaterThanOrEqual(0);
  return index;
}

function tasksTable(page: Page) {
  return page.getByRole('table').filter({ has: page.getByRole('columnheader', { name: 'Task', exact: true }) });
}

test.beforeEach(() => {
  const host = new URL(process.env.E2E_BASE_URL ?? 'http://localhost:8080').hostname;
  expect(
    ['localhost', '127.0.0.1'].includes(host) || process.env.E2E_ALLOW_NON_LOCAL === '1',
    `E2E_BASE_URL host "${host}" is not localhost or 127.0.0.1; this test creates and deletes data. Set E2E_ALLOW_NON_LOCAL=1 to allow it.`,
  ).toBe(true);
  expect(email, 'E2E_USER_EMAIL must be set').toBeTruthy();
  expect(password, 'E2E_USER_PASSWORD must be set').toBeTruthy();
  title = `E2E task ${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  created = false;
});

test.afterEach(async ({ page }) => {
  // Remove only the task this test created, through the interface (Delete button and confirmation).
  if (!created) return;
  let row = taskRow(page);
  if ((await row.count()) === 0) {
    // Not on the project screen (for example after a failed step): open the project again, then look once more.
    await page.goto('/');
    await page.getByRole('region', { name: 'Projects' }).getByRole('link', { name: PROJECT, exact: true }).click();
    await expect(page.getByRole('heading', { level: 1, name: PROJECT })).toBeVisible();
    row = taskRow(page);
    await expect(row, `created task not found for cleanup: ${title}`).toHaveCount(1);
  }
  await row.getByRole('button', { name: `Delete “${title}”` }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Delete task' }).click();
  await expect(page.getByRole('status').filter({ hasText: `“${title}” was deleted.` })).toBeVisible();
  await expect(taskRow(page)).toHaveCount(0);
});

test('AC-10.1 · sign in, open a project, create a task and see it in the list', async ({ page }) => {
  await test.step('Sign in: dashboard, workspaces and account name (FR-01, UI-A1)', async () => {
    await page.goto('/');
    await page.getByLabel('Email').fill(email!);
    await page.getByLabel('Password').fill(password!);
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/#\/dashboard$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Dashboard' })).toBeVisible();
    await expect(page.getByRole('button', { name: /^Ana Demo/ })).toBeVisible();
  });

  await test.step('Open the project: Tasks section and + New task (FR-08, FR-13)', async () => {
    await page.getByRole('region', { name: 'Projects' }).getByRole('link', { name: PROJECT, exact: true }).click();
    await expect(page.getByRole('heading', { level: 1, name: PROJECT })).toBeVisible();
    await expect(page.getByRole('heading', { level: 2, name: /^Tasks/ })).toBeVisible();
    await expect(page.getByRole('button', { name: '+ New task' })).toBeVisible();
  });

  await test.step('Create the task (AC-10.1, FR-09 to FR-12)', async () => {
    await page.getByRole('button', { name: '+ New task' }).click();
    const form = page.getByRole('form', { name: 'New task' });
    await form.getByLabel('Title (required)').fill(title);
    await form.getByLabel('Owner').selectOption({ label: 'Ben Demo (Team Member)' });
    await form.getByLabel('Due date').fill(DUE_DATE);
    await expect(form.getByLabel('Status')).toHaveValue('To do'); // default, left unchanged
    await form.getByRole('button', { name: 'Create task' }).click();
    created = true;
    await expect(page.getByRole('status').filter({ hasText: `“${title}” was created.` })).toBeVisible();
    await expect(page.getByRole('alert')).toHaveCount(0);
  });

  await test.step('The task is in the list with title, owner, due date and status', async () => {
    const row = taskRow(page);
    await expect(row).toHaveCount(1);
    const cells = row.getByRole('cell');
    await expect(cells.nth(await columnIndex(page, 'Task'))).toHaveText(title);
    await expect(cells.nth(await columnIndex(page, 'Owner'))).toHaveText(OWNER);
    await expect(cells.nth(await columnIndex(page, 'Due date'))).toHaveText(DUE_DATE);
    await expect(cells.nth(await columnIndex(page, 'Status'))).toHaveText(DEFAULT_STATUS_LABEL);
  });

  await test.step('After a reload the task is still listed (NFR-03)', async () => {
    await page.reload();
    await expect(page.getByRole('heading', { level: 1, name: PROJECT })).toBeVisible();
    await expect(taskRow(page)).toHaveCount(1);
  });
});
