// E2E: main-menu Workspaces screen (E-22) and "+ New workspace" on the dashboard (E-21), decided 2026-11-19.
// Covers FR-02, FR-03 (reading members), US-02, NFR-06. Not part of `mvn verify`. Changes no data.
//
// Same setup as create-task.spec.ts: the application running with the demo data loaded, and
//   E2E_USER_EMAIL / E2E_USER_PASSWORD  the demo user Ana (ana@example.com): Project Manager of Acme Studio and
//                                       Team Member of Northwind Lab
//   E2E_BASE_URL                        optional, default http://localhost:8080
import { expect, test } from '@playwright/test';

const email = process.env.E2E_USER_EMAIL;
const password = process.env.E2E_USER_PASSWORD;

test.beforeEach(async ({ page }) => {
  expect(email, 'E2E_USER_EMAIL must be set').toBeTruthy();
  expect(password, 'E2E_USER_PASSWORD must be set').toBeTruthy();
  await page.goto('/');
  await page.getByLabel('Email').fill(email!);
  await page.getByLabel('Password').fill(password!);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { level: 1, name: /^Dashboard/ })).toBeVisible();
});

test('E-21 · "+ New workspace" is on the dashboard, not in the main menu', async ({ page }) => {
  const menu = page.getByRole('navigation', { name: 'Main menu' });
  await expect(menu.getByRole('link', { name: 'New workspace' })).toHaveCount(0);
  await page.getByRole('link', { name: '+ New workspace' }).click();
  await expect(page).toHaveURL(/#\/workspaces\/new$/);
  await expect(page.getByRole('heading', { level: 1, name: 'New workspace' })).toBeVisible();
  // The form is not part of the Workspaces menu item (it is reached from the dashboard).
  await expect(menu.getByRole('link', { name: 'Workspaces' })).not.toHaveAttribute('aria-current', 'page');
});

test('E-22 · Workspaces lists the member\'s workspaces with role, role first, and members on request', async ({ page }) => {
  const menu = page.getByRole('navigation', { name: 'Main menu' });
  await menu.getByRole('link', { name: 'Workspaces' }).click();
  await expect(page).toHaveURL(/#\/workspaces$/);
  await expect(page.getByRole('heading', { level: 1, name: 'Workspaces' })).toBeVisible();
  await expect(menu.getByRole('link', { name: 'Workspaces' })).toHaveAttribute('aria-current', 'page');

  // Project Manager (Acme Studio) before Team Member (Northwind Lab).
  const rowHeaders = page.getByRole('table', { name: 'Your workspaces' }).getByRole('rowheader');
  await expect(rowHeaders).toHaveText(['Acme Studio', 'Northwind Lab']);
  const acme = page.getByRole('row').filter({ has: page.getByRole('rowheader', { name: 'Acme Studio' }) });
  await expect(acme.getByRole('cell', { name: 'Project Manager' })).toBeVisible();

  await acme.getByRole('button', { name: 'Show members of Acme Studio' }).click();
  const list = page.getByRole('list', { name: 'Members of Acme Studio' });
  await expect(list).toBeFocused();
  await expect(list.getByRole('listitem')).toContainText(['Ana Demo — Project Manager', 'Ben Demo — Team Member']);
});
