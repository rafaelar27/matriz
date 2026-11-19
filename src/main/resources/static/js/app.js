/*
 * Matriz user interface (CMP-11). Plain JavaScript, no framework (Technology stack).
 * Every screen uses the real REST API in openapi.yaml; the backend is the authority for every rule,
 * the checks here only give early feedback. User text is always inserted as text, never as HTML.
 */
'use strict';

const NAME_MIN = 3;
const NAME_MAX = 100;
const DESCRIPTION_MAX = 1000;
const PAGE_SIZE = 10;
// The Projects screen shows the listProjects default of 20 per page; the dashboard keeps PAGE_SIZE (D-Q5, 2026-11-12).
const PROJECT_LIST_PAGE_SIZE = 20;
const SEARCH_MAX = 100; // listProjects `q` limit (openapi.yaml)
const TASK_TITLE_MIN = 3;
const TASK_TITLE_MAX = 150;
const TASK_DESCRIPTION_MAX = 2000;
const COMMENT_MAX = 2000;

// Task statuses. The API always sends and receives these technical values (openapi.yaml: TaskStatus); the interface
// shows the same English names (FR-12). This is the only place they are mapped.
const TASK_STATUSES = ['To do', 'In progress', 'Done'];
const STATUS_LABELS = { 'To do': 'To do', 'In progress': 'In progress', 'Done': 'Done' };

function statusLabel(value) {
  return STATUS_LABELS[value] || value;
}

const main = document.getElementById('main');
// returnTo: the screen to reopen after signing in again (assumption UI-A7, approved 2026-11-12).
const state = { profile: null, flash: null, returnTo: null };

// The first screen after sign-in (UI-A1, confirmed for the dashboard plan on 2026-11-12).
const HOME = '#/dashboard';

// ---------------------------------------------------------------------------------------------------------------
// API access: session cookie, CSRF header, and sign-in redirect on 401.
// ---------------------------------------------------------------------------------------------------------------

/**
 * The only place that knows where the backend is. The value comes from the MATRIZ_API_BASE_URL environment setting,
 * served by the backend as /config.js; empty means the server that serves this page. It must stay on the same site,
 * because the session cookie and the CSRF token are same-site only (TD-03).
 */
const API_BASE_URL = String((window.MATRIZ_CONFIG && window.MATRIZ_CONFIG.apiBaseUrl) || '').replace(/\/+$/, '');

function apiUrl(path) {
  return API_BASE_URL + path;
}

class ApiError extends Error {
  constructor(status, body) {
    super((body && body.message) || 'Something went wrong. Try again.');
    this.status = status;
    this.body = body || {};
  }
}

function csrfToken() {
  const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : null;
}

function fetchCsrfToken() {
  return fetch(apiUrl('/auth/csrf'), { credentials: 'same-origin' });
}

async function ensureCsrfToken() {
  if (!csrfToken()) {
    await fetchCsrfToken();
  }
}

/**
 * Calls the API. On 401 it sends the user to sign-in with a clear message and remembers the current screen, unless
 * options.quiet is set (used to check for an existing session without redirecting).
 */
async function api(method, path, body, options) {
  const opts = options || {};
  const request = { method, credentials: 'same-origin', headers: { Accept: 'application/json' } };
  if (method !== 'GET') {
    await ensureCsrfToken();
    request.headers['X-XSRF-TOKEN'] = csrfToken();
  }
  if (body !== undefined) {
    request.headers['Content-Type'] = 'application/json';
    request.body = JSON.stringify(body);
  }
  let response;
  try {
    response = await fetch(apiUrl(path), request);
  } catch (e) {
    throw new ApiError(0, { message: 'Matriz could not be reached. Check your connection and try again.' });
  }
  const text = await response.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch (e) {
    data = null; // not a JSON body, for example a proxy error page
  }
  if (response.ok) {
    return data;
  }
  if (response.status === 403 && data && data.code === 'csrf_invalid' && !opts.retried) {
    await fetchCsrfToken();
    return api(method, path, body, Object.assign({}, opts, { retried: true }));
  }
  if (response.status === 401 && path !== '/auth/sign-in' && !opts.quiet) {
    const previousAccount = state.profile ? state.profile.account_id : null;
    state.profile = null;
    // Only the first of several parallel 401s redirects; the others would re-render sign-in and lose the notice.
    if (!location.hash.startsWith('#/sign-in')) {
      state.flash = data && data.code === 'session_expired'
        ? 'Your session has ended. Sign in again.'
        : 'Sign in to continue.';
      if (location.hash) state.returnTo = { hash: location.hash, accountId: previousAccount };
      go('#/sign-in');
    }
    throw new ApiError(401, data);
  }
  throw new ApiError(response.status, data);
}

/**
 * Where to go right after signing in (UI-A7): the remembered screen, but only for the account that was on it. If
 * another account signs in, HOME (sign-in review finding 7, security audit finding 2; approved 2026-11-12). After a
 * full page reload the earlier account is not known yet, and the screen is kept: the API still decides what the new
 * account may see (404/403).
 */
function takeReturnTo(profile) {
  const remembered = state.returnTo;
  state.returnTo = null;
  if (!remembered) return HOME;
  if (remembered.accountId && profile && remembered.accountId !== profile.account_id) return HOME;
  return remembered.hash;
}

// The same codes are used by every screen, so that refusals read the same everywhere (403/404 never show data).
function errorText(err, notFound) {
  if (err.status === 404) {
    return notFound || 'This item was not found. It may have been deleted, or you no longer have access.';
  }
  if (err.status === 403) return err.message || 'You do not have permission to do this.';
  return err.message;
}

// ---------------------------------------------------------------------------------------------------------------
// Small DOM helpers.
// ---------------------------------------------------------------------------------------------------------------

function h(tag, attrs, ...children) {
  const el = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs || {})) {
    if (value === false || value === null || value === undefined) continue;
    if (key.startsWith('on')) el.addEventListener(key.slice(2), value);
    else if (key === 'text') el.textContent = value;
    else el.setAttribute(key, value === true ? '' : value);
  }
  for (const child of children.flat()) {
    if (child === null || child === undefined || child === false) continue;
    el.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return el;
}

function render(...nodes) {
  main.replaceChildren(...nodes.filter(node => node !== null && node !== undefined && node !== false));
  main.focus();
}

function message(text, kind) {
  return h('div', { class: 'message' + (kind ? ' ' + kind : ''), role: kind === 'info' ? 'status' : 'alert' }, text);
}

function takeFlash() {
  const text = state.flash;
  state.flash = null;
  return text ? message(text, 'info') : null;
}

function loading(label) {
  return h('div', { class: 'panel', 'aria-busy': 'true' },
    h('p', {}, label || 'Loading…'), h('div', { class: 'skel' }), h('div', { class: 'skel s' }), h('div', { class: 'skel' }));
}

function failure(error, retry) {
  return h('div', { class: 'panel', role: 'alert' },
    h('p', {}, h('strong', {}, 'This section could not be loaded. '), errorText(error)),
    h('button', { class: 'btn', type: 'button', onclick: retry }, 'Try again'));
}

// Increases on every screen change, so that a section's late answer never lands on another screen.
let screenSeq = 0;

/**
 * One screen section with its own states (E-19 loading, E-20 error with Try again, UI-A4). load() resolves to the
 * content to show (success or empty state). Try again is replaced by the loading state at once, so repeated clicks
 * send one request; a 401 is left to api(), which goes to sign-in.
 */
function section(title, aside, load) {
  const seq = screenSeq;
  const id = 'section-' + title.toLowerCase().replace(/[^a-z0-9]+/g, '-');
  const body = h('div', {});
  // The section is named by its title only, not by the total or actions beside it (NFR-06).
  const box = h('section', { class: 'dash-section', 'aria-labelledby': id },
    h('h2', {}, h('span', { id }, title), aside || null), body);
  let running = false;
  const run = async () => {
    if (running) return; // one load at a time, whatever is clicked
    running = true;
    body.replaceChildren(loading('Loading…'));
    try {
      const content = await load();
      if (seq === screenSeq) body.replaceChildren(content);
    } catch (err) {
      if (err.status !== 401 && seq === screenSeq) body.replaceChildren(failure(err, run));
    } finally {
      running = false;
    }
  };
  run();
  return box;
}

/** Counts per status as text tags (never colour alone, NFR-06), in the API's order, with Portuguese labels. */
const STATUS_CLASSES = { 'To do': 's-todo', 'In progress': 's-progress', 'Done': 's-done' };

/** A status label with its visual style; the text is always present (NFR-06). */
function statusTag(status, text) {
  return h('span', { class: 'tag ' + (STATUS_CLASSES[status] || '') }, text);
}

/** The progress text plus a decorative bar, hidden from assistive technology because the text carries the meaning (FR-18). */
function progressView(progress) {
  const pct = progress.total > 0 ? Math.round(progress.done / progress.total * 100) : 0;
  const fill = h('span', { class: 'bar-fill' });
  fill.style.width = pct + '%';
  return [h('span', { class: 'progress-text' }, progressLabel(progress)),
    h('span', { class: 'bar-track', 'aria-hidden': 'true' }, fill)];
}

function statusCountTags(statusCounts) {
  const by = {};
  (statusCounts || []).forEach(c => { by[c.status] = c.count; });
  return h('span', { class: 'status-counts' }, TASK_STATUSES.map(s => statusTag(s, statusLabel(s) + ': ' + (by[s] || 0))));
}

/**
 * One page of the member's projects with their progress (FR-08, FR-18), and optionally each project's task counts by
 * status from listTasks, which always counts all of the project's tasks whatever the page (decided 2026-11-12, Q1 B).
 */
async function loadProjectsWithProgress({ q, page, size, withCounts }) {
  const query = new URLSearchParams({ page: String(page || 0), size: String(size) });
  if (q) query.set('q', q);
  const data = await api('GET', '/projects?' + query);
  const progress = {};
  const counts = {};
  if (data.items.length) {
    const ids = new URLSearchParams();
    data.items.forEach(p => ids.append('project_id', p.project_id));
    const calls = [api('GET', '/progress?' + ids).then(list => list.forEach(r => { progress[r.project_id] = r; }))];
    if (withCounts) {
      // One project's counts failing (for example, deleted meanwhile) leaves "—" in its row, not a section error.
      data.items.forEach(p => calls.push(api('GET', '/projects/' + encodeURIComponent(p.project_id) + '/tasks?size=1')
        .then(t => { counts[p.project_id] = t.status_counts; })
        .catch(err => { if (err.status === 401) throw err; })));
    }
    await Promise.all(calls);
  }
  return { data, progress, counts: withCounts ? counts : null };
}

/** E-14 "+ New project": Project Managers and Workspace Administrators (FR-06; decided 2026-11-19, PD-03 in part). */
function newProjectLink() {
  return managedWorkspaces().length ? h('a', { class: 'btn primary', href: '#/projects/new' }, '+ New project') : null;
}

function projectCount(n) {
  return n + ' project' + (n === 1 ? '' : 's');
}

/** The project table used by the Projects screen and the Dashboard (one variant). */
function projectGrid(items, progress, counts) {
  const headings = ['Project', 'Workspace', 'Owner', 'Due date', 'Progress'].concat(counts ? ['Tasks by status'] : []);
  const rows = items.map(p => {
    const pr = progress[p.project_id];
    return h('tr', {},
      h('td', {}, h('a', { href: '#/projects/' + p.project_id }, p.name),
        p.is_archived ? h('span', {}, ' ', h('span', { class: 'tag' }, 'Archived')) : null),
      h('td', {}, p.workspace_name),
      h('td', {}, p.owner_name || '—'),
      h('td', {}, formatDate(p.due_date)),
      h('td', {}, pr ? progressView(pr) : '—'),
      counts ? h('td', {}, counts[p.project_id] ? statusCountTags(counts[p.project_id]) : '—') : null);
  });
  return h('div', { class: 'wrap' }, h('table', {},
    h('caption', { class: 'skip' }, 'Projects'),
    h('thead', {}, h('tr', {}, headings.map(t => h('th', { scope: 'col' }, t)))),
    h('tbody', {}, rows)));
}

/**
 * Shared form parts, meant to be the one variant for every form: sign-in uses them now, and the project and task forms
 * move to them when their screens are reworked, which removes their own copies (sign-in review finding 5).
 * field() builds a labelled control with an optional hint and an error line tied to it by aria-describedby;
 * setError() marks a field; showServerErrors() maps the `details` of a 422 response to the fields. Values are never cleared here, so a failed submission keeps what the user typed.
 */
function formKit(prefix) {
  const fields = {};
  const field = (name, label, control, hint) => {
    const id = prefix + '-' + name;
    const error = h('div', { class: 'error', id: id + '-error', hidden: true });
    control.id = id;
    control.setAttribute('aria-describedby', error.id + (hint ? ' ' + id + '-hint' : ''));
    const wrapper = h('div', { class: 'field' }, h('label', { for: id }, label), control,
      hint ? h('div', { class: 'hint', id: id + '-hint' }, hint) : null, error);
    fields[name] = { control, error, wrapper };
    return wrapper;
  };
  const setError = (name, text) => {
    const f = fields[name];
    if (!f) return false;
    f.error.textContent = text || '';
    f.error.hidden = !text;
    f.wrapper.classList.toggle('invalid', Boolean(text));
    f.control.setAttribute('aria-invalid', text ? 'true' : 'false');
    return true;
  };
  const clear = () => Object.keys(fields).forEach(k => setError(k, ''));
  const focusFirstError = () => {
    const first = Object.values(fields).find(f => !f.error.hidden);
    if (first) first.control.focus();
    return Boolean(first);
  };
  // apiToField maps API field names to form field names where they differ.
  const showServerErrors = (err, apiToField) => {
    let shown = false;
    ((err.body && err.body.details) || []).forEach(d => {
      shown = setError((apiToField && apiToField[d.field]) || d.field, d.issue) || shown;
    });
    if (shown) focusFirstError();
    return shown;
  };
  return { field, setError, clear, focusFirstError, showServerErrors };
}

/**
 * Runs one submission at a time. The button is disabled and the form marked busy until the API answers, so repeated
 * clicks or Enter presses send a single request; the screen is then updated from the API's response only.
 */
const busyForms = new WeakSet();
async function guarded(form, button, run) {
  if (busyForms.has(form)) return undefined;
  busyForms.add(form);
  button.disabled = true;
  form.setAttribute('aria-busy', 'true');
  try {
    return await run();
  } finally {
    busyForms.delete(form);
    button.disabled = false;
    form.removeAttribute('aria-busy');
  }
}

function go(hash) {
  if (location.hash === hash) route();
  else location.hash = hash;
}

function formatDate(value) {
  return value || '—';
}

/** A server timestamp shown as a readable local date and time, for example "12 Nov 2026, 17:05". */
function formatDateTime(value) {
  if (!value) return '—';
  const date = new Date(value);
  return isNaN(date) ? value : date.toLocaleString('en-GB', { day: 'numeric', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit' });
}

function chars(text) {
  return [...(text || '')].length;
}

// ---------------------------------------------------------------------------------------------------------------
// Session and top bar.
// ---------------------------------------------------------------------------------------------------------------

async function loadProfile() {
  if (!state.profile) {
    state.profile = await api('GET', '/auth/me');
  }
  return state.profile;
}

function managedWorkspaces() {
  // Workspaces where the member manages projects: Project Managers and Workspace Administrators (PD-03 in part).
  return (state.profile ? state.profile.memberships : [])
    .filter(m => m.role === 'Project Manager' || m.role === 'Workspace Administrator');
}

function canManage(workspaceId) {
  return managedWorkspaces().some(m => m.workspace_id === workspaceId);
}

// Top bar on every signed-in screen: E-01 workspace names (text only, PD-16), E-02/E-03 menu with the current item
// marked for screen readers too, E-07 account menu with the member's name and Sign out.
function showBar(visible) {
  const bar = document.getElementById('bar');
  bar.hidden = !visible;
  if (visible && state.profile) {
    document.getElementById('acct-button').textContent = state.profile.name + ' ▾';
  }
  const current = location.hash.startsWith('#/projects') ? 'nav-projects'
    : (location.hash.startsWith('#/dashboard') ? 'nav-dashboard'
      : (location.hash.startsWith('#/workspaces') && location.hash !== '#/workspaces/new'
        ? 'nav-workspaces' : null)); // the New workspace form belongs to the dashboard; Members (E-05) to Workspaces
  ['nav-dashboard', 'nav-projects', 'nav-workspaces'].forEach(id => {
    const link = document.getElementById(id);
    link.classList.toggle('on', id === current);
    if (id === current) link.setAttribute('aria-current', 'page');
    else link.removeAttribute('aria-current');
  });
}

const acctButton = document.getElementById('acct-button');
const acctMenu = document.getElementById('acct-menu');
acctButton.addEventListener('click', () => {
  acctMenu.hidden = !acctMenu.hidden;
  acctButton.setAttribute('aria-expanded', String(!acctMenu.hidden));
});
document.addEventListener('click', e => {
  if (!acctMenu.hidden && !e.target.closest('.acct')) {
    acctMenu.hidden = true;
    acctButton.setAttribute('aria-expanded', 'false');
  }
});
document.getElementById('sign-out').addEventListener('click', async () => {
  acctMenu.hidden = true;
  try {
    await api('POST', '/auth/sign-out');
  } catch (e) {
    // Signed out already: nothing else to do.
  }
  state.profile = null;
  state.returnTo = null; // a deliberate sign-out starts the next session at HOME
  state.flash = 'You have signed out.';
  go('#/sign-in');
});

// ---------------------------------------------------------------------------------------------------------------
// Screens.
// ---------------------------------------------------------------------------------------------------------------

// Sign in (US-01, FR-01, SP-01). Field messages for missing or malformed input; one generic message for wrong
// credentials, because the API gives the same 401 for every cause. After a failure the email stays and the password is
// cleared (approved 2026-11-12). After success: back to the screen the user was on, or HOME (UI-A1, UI-A7).
function signInScreen() {
  showBar(false);
  const kit = formKit('signin');
  const banner = h('div', { 'aria-live': 'assertive' });
  const email = h('input', { type: 'email', autocomplete: 'username', required: true, spellcheck: 'false' });
  const password = h('input', { type: 'password', autocomplete: 'current-password', required: true });
  const submit = h('button', { class: 'btn primary', type: 'submit' }, 'Sign in');

  const validate = () => {
    const value = email.value.trim();
    const emailError = !value ? 'Enter your email.' : (!value.includes('@') ? 'Enter a valid email address.' : '');
    kit.setError('email', emailError);
    kit.setError('password', password.value ? '' : 'Enter your password.');
    return !emailError && Boolean(password.value);
  };

  const notice = takeFlash(); // for example "Your session has ended."; replaced by the result of the next attempt
  const form = h('form', { class: 'panel', novalidate: true, onsubmit: e => {
    e.preventDefault();
    if (notice) notice.remove();
    banner.replaceChildren();
    kit.clear();
    if (!validate()) {
      kit.focusFirstError();
      return;
    }
    guarded(form, submit, async () => {
      try {
        await ensureCsrfToken();
        state.profile = await api('POST', '/auth/sign-in', { email: email.value.trim(), password: password.value });
        go(takeReturnTo(state.profile));
      } catch (err) {
        banner.append(message(err.status === 401 ? 'The email or password is incorrect.' : errorText(err)));
        password.value = '';
        password.focus();
      }
    });
  } },
  h('h1', {}, 'Sign in to Matriz'),
  banner,
  kit.field('email', 'Email', email),
  kit.field('password', 'Password', password),
  submit,
  h('p', { class: 'hint' }, 'New to Matriz? ', h('a', { href: '#/sign-up' }, 'Create an account')));
  render(h('div', { class: 'narrow' }, notice, form));
  email.focus();

  // Someone who is still signed in does not need this screen: always HOME, never the remembered screen, which is only
  // used right after signing in (UI-A7, doc-keeper question D-11, approved 2026-11-12). Quiet: a 401 keeps the form.
  api('GET', '/auth/me', undefined, { quiet: true }).then(profile => {
    if (location.hash.startsWith('#/sign-in') && !busyForms.has(form)) {
      state.profile = profile;
      state.returnTo = null;
      go(HOME);
    }
  }).catch(() => { /* not signed in */ });
}

// Sign up (US-22, FR-01, SP-01, NFR-11). The same form parts as sign-in. The browser checks only what the contract
// states (name 1 to 80 characters, an email with "@", password 12 to 64 characters and at most 72 bytes); the API
// decides. After a failure the name and email stay and the password is cleared. One request at a time (guarded).
// After success the person is signed in and sees the welcome message (decision log 2026-11-18).
const PRIVACY_NOTICE = 'To run your account, Matriz stores your name, your email address and a protected form of '
  + 'your password (a hash, never the password itself). Your name is shown to the other members of your workspaces, '
  + 'for example as a task owner or a comment author. Matriz uses this data to run your account and your work in '
  + 'Matriz. There is not yet a way inside Matriz to ask for a copy or the deletion of your data; it will come with '
  + 'personal-data requests (FR-21).';
const PASSWORD_MIN = 12;
const PASSWORD_MAX = 64;
const PASSWORD_MAX_BYTES = 72;
const ACCOUNT_NAME_MAX = 80;

function signUpScreen() {
  showBar(false);
  const kit = formKit('signup');
  const banner = h('div', { 'aria-live': 'assertive' });
  const name = h('input', { type: 'text', autocomplete: 'name', required: true });
  const email = h('input', { type: 'email', autocomplete: 'email', required: true, spellcheck: 'false' });
  const password = h('input', { type: 'password', autocomplete: 'new-password', required: true });
  const submit = h('button', { class: 'btn primary', type: 'submit' }, 'Create account');

  const validate = () => {
    const nameValue = name.value.trim();
    const nameLength = [...nameValue].length;
    const nameError = nameLength < 1 ? 'Enter your name.'
      : (nameLength > ACCOUNT_NAME_MAX ? 'The name must be at most ' + ACCOUNT_NAME_MAX + ' characters.' : '');
    const emailValue = email.value.trim();
    const emailError = !emailValue ? 'Enter your email.'
      : (!emailValue.includes('@') ? 'Enter a valid email address.' : '');
    const length = [...password.value].length;
    const passwordError = length < PASSWORD_MIN || length > PASSWORD_MAX
      ? 'The password must be ' + PASSWORD_MIN + ' to ' + PASSWORD_MAX + ' characters long.'
      : (new TextEncoder().encode(password.value).length > PASSWORD_MAX_BYTES
        ? 'The password is too long: at most ' + PASSWORD_MAX_BYTES + ' bytes.' : '');
    kit.setError('name', nameError);
    kit.setError('email', emailError);
    kit.setError('password', passwordError);
    return !nameError && !emailError && !passwordError;
  };

  const form = h('form', { class: 'panel', novalidate: true, 'aria-describedby': 'signup-privacy', onsubmit: e => {
    e.preventDefault();
    banner.replaceChildren();
    kit.clear();
    if (!validate()) {
      kit.focusFirstError();
      return;
    }
    guarded(form, submit, async () => {
      try {
        await ensureCsrfToken();
        state.profile = await api('POST', '/auth/sign-up',
          { name: name.value.trim(), email: email.value.trim(), password: password.value });
        state.returnTo = null;
        go('#/welcome');
      } catch (err) {
        password.value = '';
        if (err.status === 409 && err.body && err.body.code === 'conflict') {
          go(HOME); // already signed in
        } else if (!kit.showServerErrors(err)) {
          banner.append(message(errorText(err)));
          password.focus();
        }
      }
    });
  } },
  h('h1', {}, 'Create your Matriz account'),
  banner,
  kit.field('name', 'Name', name),
  kit.field('email', 'Email', email),
  kit.field('password', 'Password', password,
    PASSWORD_MIN + ' to ' + PASSWORD_MAX + ' characters. Any characters are allowed.'),
  h('p', { class: 'hint', id: 'signup-privacy' }, h('strong', {}, 'Privacy. '), PRIVACY_NOTICE),
  submit,
  h('p', { class: 'hint' }, 'Already have an account? ', h('a', { href: '#/sign-in' }, 'Sign in')));
  render(h('div', { class: 'narrow' }, form));
  name.focus();

  // Someone who is already signed in does not need this screen (the API would refuse the request, EC-15).
  api('GET', '/auth/me', undefined, { quiet: true }).then(profile => {
    if (location.hash.startsWith('#/sign-up') && !busyForms.has(form)) {
      state.profile = profile;
      go(HOME);
    }
  }).catch(() => { /* not signed in */ });
}

/** Text for a person with no workspace and no invitation (AC-22.8, PD-24 in part; decision QM, 2026-11-19). */
function noWorkspaceYet() {
  return h('div', { class: 'empty' },
    h('strong', {}, 'You’re not in a workspace yet.'),
    'Create one, or ask a Workspace Administrator to invite you.',
    h('a', { class: 'btn primary', href: '#/workspaces/new' }, '+ New workspace'));
}

/**
 * Pending invitations for the signed-in person, each with Accept (listMyInvitations, acceptInvitation; FR-03, PD-24
 * and QM decided 2026-11-19). Resolves to null when there are none, so screens show nothing extra.
 */
async function myInvitationsPanel() {
  const list = await api('GET', '/invitations');
  if (!list.length) return null;
  const banner = h('div', { 'aria-live': 'assertive' });
  const rows = list.map(inv => {
    const accept = h('button', { class: 'btn primary', type: 'button',
      'aria-label': 'Accept the invitation to ' + inv.workspace_name, onclick: async () => {
        accept.disabled = true;
        banner.replaceChildren();
        try {
          const membership = await api('POST', '/invitations/' + encodeURIComponent(inv.invitation_id) + '/accept');
          state.profile = null; // the new membership must be known everywhere
          state.flash = 'You joined “' + membership.workspace_name + '” as ' + membership.role + '.';
          go('#/workspaces');
        } catch (err) {
          if (err.status === 401) return;
          banner.append(message(errorText(err, 'This invitation is no longer available.')));
          accept.disabled = false;
        }
      } }, 'Accept');
    return h('tr', {},
      h('th', { scope: 'row' }, inv.workspace_name),
      h('td', {}, inv.role),
      h('td', {}, inv.invited_by_name),
      h('td', {}, accept));
  });
  return h('section', { class: 'dash-section', 'aria-labelledby': 'my-invitations' },
    h('h2', {}, h('span', { id: 'my-invitations' }, 'Pending invitations')),
    banner,
    h('div', { class: 'panel' }, h('div', { class: 'wrap' }, h('table', {},
      h('caption', { class: 'skip' }, 'Invitations waiting for your answer'),
      h('thead', {}, h('tr', {}, ['Workspace', 'Role', 'Invited by', 'Action'].map(t => h('th', { scope: 'col' }, t)))),
      h('tbody', {}, rows)))));
}

/** myInvitationsPanel() for screens where the invitations are an extra: a failure there does not block the screen. */
async function myInvitationsOrNothing() {
  try {
    return await myInvitationsPanel();
  } catch (err) {
    return err.status === 401 ? null : message('Your pending invitations could not be loaded. ' + errorText(err));
  }
}

// First screen right after sign-up (AC-22.8; PD-24 in part, decided 2026-11-19, QM): pending invitations with Accept,
// otherwise the suggestion to create a workspace or ask to be invited.
async function welcomeScreen() {
  const seq = screenSeq;
  await loadProfile();
  const invitations = await myInvitationsOrNothing();
  if (seq !== screenSeq) return;
  showBar(true);
  render(h('h1', {}, 'Welcome to Matriz, ' + state.profile.name),
    invitations || noWorkspaceYet());
}

// Dashboard (INC-06 screen 2; docs/ui/README.md E-01 to E-20). Built only from existing views (UI-A6): the Projects
// section E-12 to E-15 with progress and, per project, task counts by status (Q1 B). My tasks, notifications,
// members, feedback and personal-data requests have no API yet and are left out (Q2). Every visit reloads the data.
async function dashboardScreen() {
  const seq = screenSeq;
  await loadProfile();
  const invitations = await myInvitationsOrNothing(); // QM: pending invitations on the dashboard too
  if (seq !== screenSeq) return;
  showBar(true);
  const total = h('span', { class: 'hint' });
  const headAction = h('span', {}); // E-14 beside the heading, once the list is known to be non-empty

  const projects = section('Projects', h('span', { class: 'actions-inline' }, total, headAction), async () => {
    const { data, progress, counts } = await loadProjectsWithProgress({ page: 0, size: PAGE_SIZE, withCounts: true });
    // The total always comes from the API, not from the rows on screen.
    total.textContent = projectCount(data.total);
    if (!data.items.length) {
      headAction.replaceChildren(); // the empty state carries the one "+ New project" (wireframe Variant B)
      return h('div', { class: 'empty' }, h('strong', {}, 'No projects yet.'), newProjectLink()); // E-15
    }
    headAction.replaceChildren(...[newProjectLink()].filter(Boolean));
    return h('div', { class: 'panel' },
      projectGrid(data.items, progress, counts),
      h('div', { class: 'section-foot' },
        h('span', {}, 'Showing ' + data.items.length + ' of ' + data.total),
        h('a', { href: '#/projects' }, 'See all projects'))); // E-13
  });

  // E-21 "+ New workspace": any signed-in person (FR-02, US-02); moved here from the top bar on 2026-11-19.
  const newWorkspace = h('a', { class: 'btn secondary', href: '#/workspaces/new' }, '+ New workspace');
  const noWorkspace = !(state.profile.memberships || []).length && !invitations;
  render(takeFlash(), h('h1', {}, 'Dashboard', newWorkspace), invitations,
    noWorkspace ? noWorkspaceYet() : projects);
}

/** The Projects screen address for a search and a page (page 0 and an empty search are left out). */
function projectsHash(q, page) {
  const query = new URLSearchParams();
  if (q) query.set('q', q);
  if (page > 0) query.set('page', String(page));
  const qs = query.toString();
  return '#/projects' + (qs ? '?' + qs : '');
}

// Projects screen (INC-06 screen 3, part 3a; E-08, E-13, E-14, E-15). Every project of the member's workspaces
// (FR-08) with progress (FR-18) and task counts by status (FR-13, D-Q3), 20 per page (D-Q5), search by name (US-28).
// Keeps "Loading projects…" and its own error state with Try again (D-Q2).
async function projectsScreen(params) {
  const seq = screenSeq;
  const q = (params.get('q') || '').trim();
  const page = Math.max(0, parseInt(params.get('page') || '0', 10) || 0);
  await loadProfile();
  if (seq !== screenSeq) return; // the member moved on while the profile loaded
  showBar(true);

  const kit = formKit('projects');
  const search = h('input', { type: 'search', value: q, maxlength: SEARCH_MAX, placeholder: 'Part of a project name' });
  const searchForm = h('form', { class: 'toolbar', role: 'search', novalidate: true, onsubmit: e => {
    e.preventDefault();
    // Whitespace only clears the search (openapi.yaml listProjects `q`: spaces ignored, empty means no filter); the
    // trimmed text goes into the address, so a reload keeps it.
    go(projectsHash(search.value.trim(), 0));
  } },
  kit.field('search', 'Search projects by name', search),
  h('button', { class: 'btn', type: 'submit' }, 'Search'),
  q ? h('a', { class: 'btn secondary', href: '#/projects' }, 'Clear') : null);

  const headAction = h('span', {}); // "+ New project", unless the empty state already shows it
  const results = h('div', {}, loading('Loading projects…'));
  render(takeFlash(), h('h1', {}, 'Projects', headAction), searchForm, results);

  const load = async () => {
    results.replaceChildren(loading('Loading projects…'));
    try {
      const { data, progress, counts } = await loadProjectsWithProgress({ q, page, size: PROJECT_LIST_PAGE_SIZE,
        withCounts: true });
      if (seq !== screenSeq) return; // the member has moved on to another screen
      // A page past the end opens the last page that has projects, keeping the search (D-Q4, AC-28.3).
      if (!data.items.length && data.total > 0 && page > 0) {
        const last = Math.max(0, Math.ceil(data.total / data.size) - 1);
        if (last < page) {
          location.replace(projectsHash(q, last));
          return;
        }
      }
      const emptyStateHasButton = !data.items.length && !q;
      headAction.replaceChildren(...(emptyStateHasButton ? [] : [newProjectLink()].filter(Boolean)));
      results.replaceChildren(projectTable(data, progress, counts, q));
    } catch (err) {
      if (err.status !== 401 && seq === screenSeq) results.replaceChildren(failure(err, load));
    }
  };
  load();
}

function projectTable(data, progress, counts, q) {
  if (!data.items.length) {
    if (q) {
      return h('div', { class: 'empty' }, h('strong', {}, 'No projects match “' + q + '”.'), 'Try another part of the name.');
    }
    return h('div', { class: 'empty' }, h('strong', {}, 'No projects yet.'), newProjectLink());
  }
  const pages = Math.max(1, Math.ceil(data.total / data.size));
  const link = (target, label) => h('a', { class: 'btn secondary', href: projectsHash(q, target) }, label);
  return h('div', { class: 'panel' },
    projectGrid(data.items, progress, counts),
    h('nav', { class: 'pager', 'aria-label': 'Pages' },
      data.page > 0 ? link(data.page - 1, '← Previous') : null,
      h('span', {}, 'Page ' + (data.page + 1) + ' of ' + pages + ' · ' + projectCount(data.total)),
      data.page + 1 < pages ? link(data.page + 1, 'Next →') : null));
}

async function projectDetailScreen(id, params) {
  await loadProfile();
  showBar(true);
  render(loading('Loading the project…'));
  let project;
  let progress;
  try {
    project = await api('GET', '/projects/' + encodeURIComponent(id));
    progress = (await api('GET', '/progress?project_id=' + encodeURIComponent(id)))[0];
  } catch (err) {
    if (err.status === 401) return;
    if (err.status === 404) {
      render(message('This project was not found. It may have been deleted, or it belongs to another workspace.'),
        h('a', { class: 'btn', href: '#/projects' }, '← Back to projects'));
      return;
    }
    render(failure(err, () => projectDetailScreen(id, params)));
    return;
  }
  progress = progress || { project_id: id, done: 0, total: 0 };
  const manage = canManage(project.workspace_id);
  const deleteButton = manage ? h('button', { class: 'btn danger', type: 'button', onclick: () => confirmDelete(project, progress) },
    'Delete project') : null;
  // Updated in place by the task section after every task change, so the figure never needs a reload.
  const progressText = h('dd', { id: 'project-progress' }, progressView(progress));
  render(
    takeFlash(),
    h('p', {}, h('a', { href: '#/projects' }, '← Projects')),
    h('h1', {}, h('span', {}, project.name, project.is_archived ? h('span', {}, ' ', h('span', { class: 'tag' }, 'Archived')) : null)),
    h('div', { class: 'panel' },
      h('dl', { class: 'facts' },
        h('dt', {}, 'Workspace'), h('dd', {}, project.workspace_name),
        h('dt', {}, 'Owner'), h('dd', {}, project.owner_name || '—'),
        h('dt', {}, 'Start date'), h('dd', {}, formatDate(project.start_date)),
        h('dt', {}, 'Due date'), h('dd', {}, formatDate(project.due_date)),
        h('dt', {}, 'Progress'), progressText),
      h('h2', { class: 'skip' }, 'Description'),
      h('p', { class: 'description' }, project.description || 'No description.'),
      manage ? h('div', { class: 'actions' },
        h('a', { class: 'btn', href: '#/projects/' + project.project_id + '/edit' }, 'Edit project'), deleteButton) : null),
    taskSection(project, progress, progressText, params || new URLSearchParams()));
}

function progressLabel(progress) {
  return progress.done + ' of ' + progress.total + ' tasks done';
}

/** Shows the shared confirmation dialog; resolves true only when the delete button was chosen. */
function askConfirm(title, text, okLabel) {
  const dialog = document.getElementById('confirm');
  document.getElementById('confirm-title').textContent = title;
  document.getElementById('confirm-text').textContent = text;
  document.getElementById('confirm-ok').textContent = okLabel;
  dialog.returnValue = '';
  return new Promise(resolve => {
    dialog.onclose = () => resolve(dialog.returnValue === 'delete');
    dialog.showModal();
  });
}

async function confirmDelete(project, progress) {
  const tasks = progress ? progress.total : 0;
  const ok = await askConfirm('Delete project?', '“' + project.name + '” will be deleted' +
    (tasks ? ', together with its ' + tasks + ' task' + (tasks === 1 ? '' : 's') + ' and their comments' : '') +
    '. This cannot be undone.', 'Delete project');
  if (ok) {
    try {
      await api('DELETE', '/projects/' + encodeURIComponent(project.project_id));
      state.flash = '“' + project.name + '” was deleted.';
      go('#/projects');
    } catch (err) {
      if (err.status !== 401) {
        main.prepend(message(err.status === 404 ? 'This project no longer exists.' : err.message));
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------------------------
// Tasks of a project (INC-04; FR-09 to FR-13, FR-15). Shown inside the project view; every change refreshes the
// list, the counts per status and the project's progress without reloading the page.
// ---------------------------------------------------------------------------------------------------------------

function taskSection(project, progress, progressText, params) {
  const me = (state.profile.memberships || []).find(m => m.workspace_id === project.workspace_id) || {};
  const isWa = me.role === 'Workspace Administrator';
  const writer = me.role === 'Project Manager' || me.role === 'Team Member' || isWa;
  const isPm = me.role === 'Project Manager';
  // Mirrors AccessPolicy so that refused actions are hidden (CMP-11); the backend still decides. Workspace
  // Administrators create, assign, comment, change the owner and the status of their own tasks and delete the tasks
  // they created, but do not edit a task's content (decided 2026-11-19, PD-03 in part).
  const can = {
    create: writer,
    edit: t => isPm || isWa || (writer && t.owner_member_id === me.member_id),
    content: t => isPm || (writer && !isWa && t.owner_member_id === me.member_id),
    reassign: isPm || isWa,
    status: t => writer && t.owner_member_id === me.member_id,
    // A task whose creator was removed (no creator any more) can be deleted by Workspace Administrators and Project
    // Managers (decided 2026-11-19, QE).
    remove: t => writer && (t.created_by_member_id === me.member_id || (!t.created_by_member_id && (isPm || isWa))),
  };
  const filters = {
    status: TASK_STATUSES.includes(params.get('status')) ? params.get('status') : '',
    owner: params.get('owner') || '',
    page: Math.max(0, parseInt(params.get('page') || '0', 10) || 0),
  };
  const base = '/projects/' + encodeURIComponent(project.project_id) + '/tasks';
  const taskPath = t => base + '/' + encodeURIComponent(t.task_id);
  let members = [];

  const notice = h('div', { 'aria-live': 'polite' });
  const counts = h('p', { class: 'counts' });
  const formArea = h('div', {});
  const results = h('div', {}, loading('Loading tasks…'));
  const say = (text, isError) => notice.replaceChildren(message(text, isError ? null : 'info'));

  const statusFilter = h('select', { id: 'task-status-filter' }, h('option', { value: '' }, 'All statuses'),
    TASK_STATUSES.map(s => h('option', { value: s, selected: s === filters.status }, statusLabel(s))));
  const ownerFilter = h('select', { id: 'task-owner-filter' }, h('option', { value: '' }, 'All owners'));
  const onFilter = () => {
    filters.status = statusFilter.value;
    filters.owner = ownerFilter.value;
    filters.page = 0;
    refresh();
  };
  statusFilter.addEventListener('change', onFilter);
  ownerFilter.addEventListener('change', onFilter);
  const clearFilters = () => {
    statusFilter.value = '';
    ownerFilter.value = '';
    onFilter();
  };

  // Keeps filters and page in the address, so a reload shows the same list (no hashchange, so no re-render).
  const syncAddress = () => {
    const q = new URLSearchParams();
    if (filters.status) q.set('status', filters.status);
    if (filters.owner) q.set('owner', filters.owner);
    if (filters.page) q.set('page', String(filters.page));
    const qs = q.toString();
    history.replaceState(null, '', '#/projects/' + project.project_id + (qs ? '?' + qs : ''));
  };

  const showCounts = list => {
    const by = {};
    list.forEach(c => { by[c.status] = c.count; });
    counts.replaceChildren(statusCountTags(list));
    progress.done = by['Done'] || 0;
    progress.total = list.reduce((n, c) => n + c.count, 0);
    progressText.replaceChildren(...progressView(progress));
  };

  const refresh = async () => {
    syncAddress();
    results.setAttribute('aria-busy', 'true');
    try {
      const query = new URLSearchParams({ page: String(filters.page), size: String(PAGE_SIZE) });
      if (filters.status) query.set('status', filters.status);
      if (filters.owner) query.set('owner_member_id', filters.owner);
      const data = await api('GET', base + '?' + query);
      if (!data.items.length && data.page > 0 && data.total > 0) {
        filters.page = Math.ceil(data.total / data.size) - 1; // the last page became empty after a delete
        return refresh();
      }
      showCounts(data.status_counts);
      results.replaceChildren(table(data));
    } catch (err) {
      if (err.status !== 401) results.replaceChildren(failure(err, refresh));
    } finally {
      results.removeAttribute('aria-busy');
    }
  };

  const statusSelect = t => {
    const select = h('select', { 'aria-label': 'Status of ' + t.title },
      TASK_STATUSES.map(s => h('option', { value: s, selected: s === t.status }, statusLabel(s))));
    select.addEventListener('change', async () => {
      select.disabled = true;
      try {
        await api('PATCH', taskPath(t), { status: select.value });
        say('“' + t.title + '” is now ' + statusLabel(select.value) + '.');
        await refresh();
      } catch (err) {
        if (err.status === 401) return;
        select.value = t.status;
        select.disabled = false;
        say(err.status === 404 ? 'This task no longer exists.' : err.message, true);
        if (err.status === 404) refresh();
      }
    });
    return select;
  };

  const table = data => {
    if (!data.items.length) {
      const filtered = filters.status || filters.owner;
      return h('div', { class: 'empty' },
        h('strong', {}, filtered ? 'No tasks match these filters.' : 'No tasks in this project yet.'),
        filtered ? h('button', { class: 'btn secondary', type: 'button', onclick: clearFilters }, 'Clear filters')
          : (can.create ? h('button', { class: 'btn primary', type: 'button', onclick: () => openForm(null) }, '+ New task') : null));
    }
    const rows = data.items.map(t => h('tr', {},
      h('td', {}, h('button', { class: 'link', type: 'button', onclick: () => openDetails(t) }, t.title)),
      h('td', {}, t.owner_name || '—'),
      h('td', {}, formatDate(t.due_date)),
      h('td', {}, can.status(t) ? statusSelect(t) : statusTag(t.status, statusLabel(t.status))),
      h('td', {}, h('div', { class: 'row-actions' },
        can.edit(t) ? h('button', { class: 'btn secondary', type: 'button', 'aria-label': 'Edit “' + t.title + '”',
          onclick: () => openForm(t) }, 'Edit') : null,
        can.remove(t) ? h('button', { class: 'btn secondary', type: 'button', 'aria-label': 'Delete “' + t.title + '”',
          onclick: () => removeTask(t) }, 'Delete') : null))));
    const pages = Math.max(1, Math.ceil(data.total / data.size));
    const pageButton = (target, label) => h('button', { class: 'btn secondary', type: 'button', onclick: () => {
      filters.page = target;
      refresh();
    } }, label);
    return h('div', {},
      h('div', { class: 'wrap' }, h('table', {},
        h('caption', { class: 'skip' }, 'Tasks of ' + project.name),
        h('thead', {}, h('tr', {}, ['Task', 'Owner', 'Due date', 'Status', 'Actions'].map(t => h('th', { scope: 'col' }, t)))),
        h('tbody', {}, rows))),
      h('nav', { class: 'pager', 'aria-label': 'Task pages' },
        data.page > 0 ? pageButton(data.page - 1, '← Previous') : null,
        h('span', {}, 'Page ' + (data.page + 1) + ' of ' + pages + ' · ' + data.total + ' task' + (data.total === 1 ? '' : 's')),
        data.page + 1 < pages ? pageButton(data.page + 1, 'Next →') : null));
  };

  const removeTask = async t => {
    const ok = await askConfirm('Delete task?',
      '“' + t.title + '” will be deleted, together with its comments. This cannot be undone.', 'Delete task');
    if (!ok) return;
    try {
      await api('DELETE', taskPath(t));
      formArea.replaceChildren();
      say('“' + t.title + '” was deleted.');
      await refresh();
    } catch (err) {
      if (err.status === 401) return;
      say(err.status === 404 ? 'This task no longer exists.' : err.message, true);
      refresh();
    }
  };

  // Read-only view of one task (getTask), for every member.
  const openDetails = async t => {
    formArea.replaceChildren(loading('Loading the task…'));
    let task;
    try {
      task = await api('GET', taskPath(t));
    } catch (err) {
      if (err.status === 401) return;
      formArea.replaceChildren();
      say(err.status === 404 ? 'This task no longer exists.' : err.message, true);
      if (err.status === 404) refresh();
      return;
    }
    const close = h('button', { class: 'btn secondary', type: 'button', onclick: () => formArea.replaceChildren() }, 'Close');
    formArea.replaceChildren(h('section', { class: 'panel task-panel', 'aria-label': 'Task details' },
      h('h3', {}, task.title),
      h('dl', { class: 'facts' },
        h('dt', {}, 'Owner'), h('dd', {}, task.owner_name || '—'),
        h('dt', {}, 'Due date'), h('dd', {}, formatDate(task.due_date)),
        h('dt', {}, 'Status'), h('dd', {}, statusTag(task.status, statusLabel(task.status)))),
      h('p', { class: 'description' }, task.description || 'No description.'),
      assigneeArea(task),
      commentArea(task),
      h('div', { class: 'actions' },
        can.edit(task) ? h('button', { class: 'btn', type: 'button', onclick: () => openForm(task) }, 'Edit') : null,
        close)));
    close.focus();
  };

  // Comments of one task (INC-05; FR-16, US-15), oldest first. Every member reads them; Project Managers and Team
  // Members post (the backend also requires project membership). Author and date always come from the server.
  const commentArea = task => {
    const path = taskPath(task) + '/comments';
    const list = h('div', { 'aria-live': 'polite' }, loading('Loading comments…'));
    const box = h('section', { class: 'comments', 'aria-labelledby': 'comments-title' },
      h('h4', { id: 'comments-title' }, 'Comments'), list);

    const load = async () => {
      try {
        const items = await api('GET', path);
        list.replaceChildren(items.length
          ? h('ol', { class: 'comment-list' }, items.map(c => h('li', {},
            h('div', { class: 'comment-meta' }, h('strong', {}, c.author_name), ' · ',
              h('time', { datetime: c.created_at }, formatDateTime(c.created_at))),
            h('p', { class: 'description' }, c.body))))
          : h('p', { class: 'hint' }, 'No comments yet.'));
      } catch (err) {
        if (err.status === 401) return;
        list.replaceChildren(failure(err, load));
      }
    };

    if (writer) {
      const text = h('textarea', { id: 'comment-body', rows: 3, 'aria-describedby': 'comment-hint comment-error' });
      const count = h('span', {});
      const error = h('div', { class: 'error', id: 'comment-error', hidden: true });
      const wrapper = h('div', { class: 'field' }, h('label', { for: 'comment-body' }, 'Add a comment'), text,
        h('div', { class: 'hint', id: 'comment-hint' }, 'Up to 2000 characters. ', count), error);
      const showError = message => {
        error.textContent = message || '';
        error.hidden = !message;
        wrapper.classList.toggle('invalid', Boolean(message));
        text.setAttribute('aria-invalid', message ? 'true' : 'false');
      };
      const counter = () => { count.textContent = chars(text.value.trim()) + ' / ' + COMMENT_MAX; };
      text.addEventListener('input', counter);
      counter();
      const submit = h('button', { class: 'btn', type: 'submit' }, 'Post comment');
      box.append(h('form', { class: 'comment-form', novalidate: true, onsubmit: async e => {
        e.preventDefault();
        const n = chars(text.value.trim());
        if (n === 0 || n > COMMENT_MAX) {
          showError(n === 0 ? 'A comment cannot be empty.' : 'A comment can be at most 2000 characters long.');
          text.focus();
          return;
        }
        showError('');
        submit.disabled = true;
        try {
          await api('POST', path, { body: text.value });
          text.value = '';
          counter();
          await load();
        } catch (err) {
          if (err.status === 401) return;
          const detail = ((err.body && err.body.details) || []).find(d => d.field === 'body');
          showError(detail ? detail.issue : (err.status === 404 ? 'This task no longer exists.' : err.message));
        } finally {
          submit.disabled = false;
        }
      } }, wrapper, submit));
    }

    load();
    return box;
  };

  // Assignees of one task (INC-05; FR-22, US-29). Shown to every member; Project Managers assign and remove.
  // The choice offers only eligible members (active project members); the backend checks every id again.
  const assigneeArea = task => {
    const path = taskPath(task) + '/assignees';
    const box = h('section', { class: 'assignees', 'aria-labelledby': 'assignees-title' },
      h('h4', { id: 'assignees-title' }, 'Assignees'));
    const list = h('div', { 'aria-live': 'polite' }, loading('Loading assignees…'));
    const feedback = h('div', { 'aria-live': 'assertive' });
    const picker = h('div', {});
    box.append(list, feedback, picker);

    const load = async () => {
      try {
        const [assigned, eligible] = await Promise.all([api('GET', path),
          can.reassign ? api('GET', taskPath(task) + '/eligible-assignees') : Promise.resolve([])]);
        list.replaceChildren(assigned.length
          ? h('ul', { class: 'people' }, assigned.map(a => h('li', {},
            h('span', {}, a.name), h('span', { class: 'hint' }, ' · since ' + formatDateTime(a.assigned_at)),
            can.reassign ? h('button', { class: 'btn secondary', type: 'button', 'aria-label': 'Remove ' + a.name,
              onclick: () => remove(a) }, 'Remove') : null)))
          : h('p', { class: 'hint' }, 'No one is assigned besides the owner.'));
        picker.replaceChildren(can.reassign ? chooser(eligible.filter(m => !assigned.some(a => a.member_id === m.member_id))) : '');
      } catch (err) {
        if (err.status === 401) return;
        list.replaceChildren(failure(err, load));
      }
    };

    const chooser = available => {
      if (!available.length) return h('p', { class: 'hint' }, 'Every member of this project is assigned.');
      const boxes = available.map(m => ({ m, input: h('input', { type: 'checkbox', value: m.member_id, id: 'assign-' + m.member_id }) }));
      const submit = h('button', { class: 'btn', type: 'submit' }, 'Assign selected');
      return h('form', { class: 'assign-form', onsubmit: async e => {
        e.preventDefault();
        feedback.replaceChildren();
        const ids = boxes.filter(b => b.input.checked).map(b => b.m.member_id);
        if (!ids.length) {
          feedback.append(message('Choose at least one member.'));
          return;
        }
        submit.disabled = true;
        try {
          await api('POST', path, { member_ids: ids });
          feedback.append(message('Assigned.', 'info'));
          await load();
        } catch (err) {
          if (err.status === 401) return;
          feedback.append(message(err.message));
          await load(); // shows what is actually stored, for example after someone else assigned the same member
        } finally {
          submit.disabled = false;
        }
      } },
      h('fieldset', {}, h('legend', {}, 'Assign members of this project'),
        boxes.map(b => h('label', { class: 'check', for: b.input.id }, b.input, ' ' + b.m.name + ' (' + b.m.role + ')'))),
      submit);
    };

    const remove = async a => {
      feedback.replaceChildren();
      try {
        await api('DELETE', path + '/' + encodeURIComponent(a.member_id));
        feedback.append(message(a.name + ' was removed.', 'info'));
      } catch (err) {
        if (err.status === 401) return;
        feedback.append(message(err.status === 404 ? a.name + ' was no longer assigned.' : err.message));
      }
      await load();
    };

    load();
    return box;
  };

  // Create (task null) or edit. Only fields the caller may change are enabled, and only changed fields are sent.
  const openForm = async task => {
    const editing = Boolean(task);
    let eligible = null; // project members, for the owner choice when editing (AC-10.3)
    if (editing) {
      try {
        task = await api('GET', taskPath(task)); // latest values
        if (can.reassign) eligible = await api('GET', taskPath(task) + '/eligible-assignees');
      } catch (err) {
        if (err.status === 401) return;
        say(err.status === 404 ? 'This task no longer exists.' : err.message, true);
        if (err.status === 404) refresh();
        return;
      }
    }
    const fields = {};
    const field = (name, label, control, hint) => {
      const error = h('div', { class: 'error', id: 'task-' + name + '-error', hidden: true });
      control.id = 'task-' + name;
      control.setAttribute('aria-describedby', error.id + (hint ? ' task-' + name + '-hint' : ''));
      const wrapper = h('div', { class: 'field' }, h('label', { for: control.id }, label), control,
        hint ? h('div', { class: 'hint', id: 'task-' + name + '-hint' }, hint) : null, error);
      fields[name] = { control, error, wrapper };
      return wrapper;
    };
    const setError = (name, text) => {
      const f = fields[name];
      if (!f) return false;
      f.error.textContent = text || '';
      f.error.hidden = !text;
      f.wrapper.classList.toggle('invalid', Boolean(text));
      f.control.setAttribute('aria-invalid', text ? 'true' : 'false');
      return true;
    };

    const title = h('input', { type: 'text', required: true, maxlength: TASK_TITLE_MAX + 20, value: editing ? task.title : '' });
    const description = h('textarea', {});
    description.value = editing && task.description ? task.description : '';
    const dueDate = h('input', { type: 'date', value: editing && task.due_date ? task.due_date : '' });
    const status = h('select', {}, TASK_STATUSES.map(s =>
      h('option', { value: s, selected: s === (editing ? task.status : 'To do') }, statusLabel(s))));
    const owner = h('select', { required: true });
    const titleCount = h('span', {});
    const descriptionCount = h('span', {});
    const counters = () => {
      titleCount.textContent = chars(title.value.trim()) + ' / ' + TASK_TITLE_MAX;
      descriptionCount.textContent = chars(description.value) + ' / ' + TASK_DESCRIPTION_MAX;
    };
    title.addEventListener('input', counters);
    description.addEventListener('input', counters);

    const contentAllowed = !editing || can.content(task);
    title.disabled = !contentAllowed;
    description.disabled = !contentAllowed;
    dueDate.disabled = !contentAllowed;
    status.disabled = editing && !can.status(task);
    const ownerChoices = can.reassign ? (eligible || members) : members.filter(m => m.member_id === me.member_id);
    const currentOwner = editing ? task.owner_member_id : me.member_id;
    owner.replaceChildren(...(ownerChoices.length ? ownerChoices : [{ member_id: currentOwner, name: editing ? task.owner_name : state.profile.name, role: '' }])
      .map(m => h('option', { value: m.member_id, selected: m.member_id === currentOwner }, m.name + (m.role ? ' (' + m.role + ')' : ''))));
    if (editing && ![...owner.options].some(o => o.value === currentOwner)) {
      owner.prepend(h('option', { value: currentOwner, selected: true }, task.owner_name || 'Current owner'));
    }
    owner.value = currentOwner;
    owner.disabled = !can.reassign;

    const banner = h('div', { 'aria-live': 'assertive' });
    const submit = h('button', { class: 'btn primary', type: 'submit' }, editing ? 'Save task' : 'Create task');
    const cancel = h('button', { class: 'btn secondary', type: 'button', onclick: () => formArea.replaceChildren() }, 'Cancel');

    const validate = () => {
      const n = chars(title.value.trim());
      const titleError = n === 0 ? 'A title is required.'
        : (n < TASK_TITLE_MIN || n > TASK_TITLE_MAX ? 'The title must be 3 to 150 characters long.' : '');
      const descriptionError = chars(description.value) > TASK_DESCRIPTION_MAX
        ? 'The description can be at most 2000 characters long.' : '';
      setError('title', titleError);
      setError('description', descriptionError);
      return !titleError && !descriptionError;
    };

    const form = h('form', { class: 'panel task-panel', novalidate: true, 'aria-label': editing ? 'Edit task' : 'New task',
      onsubmit: async e => {
        e.preventDefault();
        banner.replaceChildren();
        notice.replaceChildren(); // B-01: an earlier success notice must not stay next to this save's result
        Object.keys(fields).forEach(k => setError(k, ''));
        if (!validate()) {
          banner.append(message('Check the highlighted fields.'));
          const first = Object.values(fields).find(f => !f.error.hidden);
          if (first) first.control.focus();
          return;
        }
        const values = {
          title: title.value.trim(),
          description: description.value === '' ? null : description.value,
          owner_member_id: owner.value,
          due_date: dueDate.value || null,
          status: status.value,
        };
        let body = values;
        if (editing) {
          const before = { title: task.title, description: task.description || null, owner_member_id: task.owner_member_id,
            due_date: task.due_date || null, status: task.status };
          body = {};
          Object.keys(values).forEach(k => { if (values[k] !== before[k]) body[k] = values[k]; });
          if (!Object.keys(body).length) {
            formArea.replaceChildren();
            return;
          }
        } else {
          if (!body.description) delete body.description;
          if (!body.due_date) delete body.due_date;
        }
        submit.disabled = true;
        try {
          const saved = editing ? await api('PATCH', taskPath(task), body) : await api('POST', base, body);
          formArea.replaceChildren();
          say(editing ? '“' + saved.title + '” was saved.' : '“' + saved.title + '” was created.');
          await refresh();
        } catch (err) {
          if (err.status === 401) return;
          const details = (err.body && err.body.details) || [];
          let shown = false;
          const formField = { title: 'title', description: 'description', owner_member_id: 'owner', due_date: 'due', status: 'status' };
          details.forEach(d => { shown = setError(formField[d.field], d.issue) || shown; });
          banner.append(message(shown ? 'Check the highlighted fields.'
            : (err.status === 404 ? 'This task or project no longer exists.' : err.message)));
        } finally {
          submit.disabled = false;
        }
      } },
    h('h3', {}, editing ? 'Edit task' : 'New task'),
    banner,
    field('title', 'Title (required)', title, h('span', {}, '3 to 150 characters. ', titleCount)),
    field('description', 'Description', description, h('span', {}, 'Up to 2000 characters. ', descriptionCount)),
    h('div', { class: 'row' },
      field('owner', 'Owner', owner, can.reassign ? 'Members of this project can own its tasks.' : 'You own the tasks you create.'),
      field('due', 'Due date', dueDate)),
    field('status', 'Status', status, editing && !can.status(task) ? 'Only the task’s owner can change its status.' : null),
    h('div', { class: 'actions' }, submit, cancel));

    formArea.replaceChildren(form);
    counters();
    (contentAllowed ? title : owner).focus();
  };

  // Owners for the filter and the form (workspace members; the backend refuses owners outside the project).
  (async () => {
    try {
      members = await api('GET', '/workspaces/' + encodeURIComponent(project.workspace_id) + '/members');
      ownerFilter.append(...members.map(m => h('option', { value: m.member_id, selected: m.member_id === filters.owner }, m.name)));
      ownerFilter.value = filters.owner;
    } catch (err) {
      // The owner filter stays at "All owners"; the list itself still loads.
    }
  })();
  refresh();

  return h('section', { class: 'tasks', 'aria-labelledby': 'tasks-title' },
    h('h2', { id: 'tasks-title' }, h('span', {}, 'Tasks'),
      can.create ? h('button', { class: 'btn primary', type: 'button', onclick: () => openForm(null) }, '+ New task') : null),
    counts,
    notice,
    formArea,
    h('div', { class: 'toolbar' },
      h('div', { class: 'field' }, h('label', { for: 'task-status-filter' }, 'Status'), statusFilter),
      h('div', { class: 'field' }, h('label', { for: 'task-owner-filter' }, 'Owner'), ownerFilter)),
    h('div', { class: 'panel' }, results));
}

/**
 * Create and edit project form (INC-06 screen 3, part 3b; FR-06, US-06, US-24, AC-24.1), on the shared form parts.
 * Owner: active members only, no "No owner" choice (decided 2026-11-12, D-QF2); the creator by default; on edit an owner
 * who is no longer an active member shows as "Former member", without a name, and is kept unless someone else is
 * chosen (D-QF3, PD-10 in part). A placeholder or a member list that failed never sends an owner. Edit sends only the
 * changed fields; with no change it opens the project with "Changes saved." and sends nothing (decided 2026-11-16, QC1).
 */
async function projectFormScreen(id) {
  const seq = screenSeq;
  await loadProfile();
  if (seq !== screenSeq) return; // the member moved on while the profile loaded
  showBar(true);
  const editing = Boolean(id);
  let project = null;
  if (editing) {
    render(loading('Loading the project…'));
    try {
      project = await api('GET', '/projects/' + encodeURIComponent(id));
    } catch (err) {
      if (err.status !== 401 && seq === screenSeq) render(message(errorText(err, 'This project was not found.')));
      return;
    }
    if (seq !== screenSeq) return;
    if (!canManage(project.workspace_id)) {
      render(message('Only Project Managers and Workspace Administrators can edit projects.'), h('a', { class: 'btn', href: '#/projects/' + id }, '← Back'));
      return;
    }
  } else if (!managedWorkspaces().length) {
    render(message('Only Project Managers and Workspace Administrators can create projects.'), h('a', { class: 'btn', href: '#/projects' }, '← Back'));
    return;
  }

  // Field names follow the API, so a 422's details map to the fields without translation.
  const kit = formKit('project');

  const workspace = h('select', { required: true },
    (editing ? [{ workspace_id: project.workspace_id, workspace_name: project.workspace_name }] : managedWorkspaces())
      .map(m => h('option', { value: m.workspace_id }, m.workspace_name)));
  workspace.disabled = editing;
  const name = h('input', { type: 'text', required: true, maxlength: NAME_MAX + 20, value: project ? project.name : '' });
  const description = h('textarea', {});
  description.value = project && project.description ? project.description : '';
  const owner = h('select', {});
  const startDate = h('input', { type: 'date', value: project && project.start_date ? project.start_date : '' });
  const dueDate = h('input', { type: 'date', value: project && project.due_date ? project.due_date : '' });
  const nameCount = h('span', {});
  const descriptionCount = h('span', {});
  const banner = h('div', { 'aria-live': 'assertive' });
  const submit = h('button', { class: 'btn primary', type: 'submit' }, editing ? 'Save changes' : 'Create project');

  const counts = () => {
    nameCount.textContent = chars(name.value.trim()) + ' / ' + NAME_MAX;
    descriptionCount.textContent = chars(description.value) + ' / ' + DESCRIPTION_MAX;
  };
  name.addEventListener('input', counts);
  description.addEventListener('input', counts);

  // Member ids from the last member list that loaded; only these are ever sent as the owner. A placeholder, the
  // "Former member" choice or a list that failed never sends an owner, so the server keeps it (or, on create, makes
  // the caller the owner).
  let loadedMemberIds = new Set();
  let ownersRequest = 0;
  const FORMER = 'former-member';
  const loadOwners = async () => {
    const request = ++ownersRequest;
    loadedMemberIds = new Set();
    owner.replaceChildren(h('option', { value: '' }, 'Loading members…'));
    try {
      const list = await api('GET', '/workspaces/' + encodeURIComponent(workspace.value) + '/members');
      if (request !== ownersRequest || seq !== screenSeq) return; // an answer for an earlier workspace or screen
      loadedMemberIds = new Set(list.map(m => m.member_id));
      const me = state.profile.memberships.find(m => m.workspace_id === workspace.value);
      const options = list.map(m => h('option', { value: m.member_id }, m.name + ' (' + m.role + ')'));
      let selected = me ? me.member_id : '';
      if (editing) {
        selected = project.owner_member_id;
        if (!loadedMemberIds.has(selected)) {
          options.unshift(h('option', { value: FORMER }, 'Former member')); // never shows the name (D-QF3)
          selected = FORMER;
        }
      }
      owner.replaceChildren(...options);
      owner.value = selected;
    } catch (err) {
      if (err.status !== 401 && request === ownersRequest && seq === screenSeq) {
        owner.replaceChildren(h('option', { value: '' }, 'Members could not be loaded'));
      }
    }
  };
  workspace.addEventListener('change', loadOwners);

  // A date field holding an incomplete or impossible date reads as empty, so it is refused rather than sent as empty,
  // which would clear a stored date (decided 2026-11-16, QC2).
  // A year that is not 4 digits (some browsers accept 10000) is refused too, so the date order is always checked here
  // and the API never gets a value it cannot read (decided 2026-11-16, review finding R-04).
  const dateError = input => ((input.validity && input.validity.badInput) || (input.value && !/^\d{4}-\d{2}-\d{2}$/.test(input.value))
    ? 'Enter a valid date.' : '');
  const validate = () => {
    const n = chars(name.value.trim());
    kit.setError('name', n < NAME_MIN || n > NAME_MAX ? 'The name must be 3 to 100 characters long.' : '');
    kit.setError('description', chars(description.value) > DESCRIPTION_MAX
      ? 'The description can be at most 1000 characters long.' : '');
    kit.setError('start_date', dateError(startDate));
    let dueError = dateError(dueDate);
    if (!dueError && startDate.value && dueDate.value && startDate.value > dueDate.value) {
      dueError = 'The due date cannot be before the start date.'; // D-QF1, the API's wording
    }
    kit.setError('due_date', dueError);
    return ![name, description, startDate, dueDate].some(c => c.getAttribute('aria-invalid') === 'true');
  };

  const createBody = () => {
    const body = { workspace_id: workspace.value, name: name.value.trim() };
    if (description.value !== '') body.description = description.value;
    if (startDate.value) body.start_date = startDate.value;
    if (dueDate.value) body.due_date = dueDate.value;
    if (loadedMemberIds.has(owner.value)) body.owner_member_id = owner.value;
    return body;
  };
  const changedFields = () => {
    const body = {};
    if (name.value.trim() !== project.name) body.name = name.value.trim();
    if (description.value !== (project.description || '')) body.description = description.value === '' ? null : description.value;
    if (startDate.value !== (project.start_date || '')) body.start_date = startDate.value || null;
    if (dueDate.value !== (project.due_date || '')) body.due_date = dueDate.value || null;
    if (loadedMemberIds.has(owner.value) && owner.value !== project.owner_member_id) body.owner_member_id = owner.value;
    return body;
  };

  const form = h('form', { class: 'panel', novalidate: true, onsubmit: e => {
    e.preventDefault();
    guarded(form, submit, async () => {
      banner.replaceChildren();
      kit.clear();
      if (!validate()) {
        banner.append(message('Check the highlighted fields.'));
        kit.focusFirstError();
        return;
      }
      const body = editing ? changedFields() : createBody();
      if (editing && !Object.keys(body).length) {
        state.flash = 'Changes saved.'; // nothing changed, so no request (QC1)
        go('#/projects/' + id);
        return;
      }
      try {
        const saved = editing
          ? await api('PATCH', '/projects/' + encodeURIComponent(id), body)
          : await api('POST', '/projects', body);
        state.flash = editing ? 'Changes saved.' : 'Project created.';
        go('#/projects/' + saved.project_id);
      } catch (err) {
        if (err.status === 401) return;
        const shown = err.status === 422 && kit.showServerErrors(err);
        banner.append(message(shown ? 'Check the highlighted fields.' : errorText(err, 'This project was not found.')));
      }
    });
  } },
  banner,
  kit.field('workspace_id', 'Workspace', workspace, editing ? 'A project stays in its workspace.' : null),
  kit.field('name', 'Name (required)', name, h('span', {}, '3 to 100 characters. ', nameCount)),
  kit.field('description', 'Description', description, h('span', {}, 'Up to 1000 characters. ', descriptionCount)),
  // The hint is true only on create, where the caller is the default owner (decided 2026-11-16, QC3).
  kit.field('owner_member_id', 'Owner', owner, editing ? null : 'You are the owner unless you choose someone else.'),
  h('div', { class: 'row' }, kit.field('start_date', 'Start date', startDate), kit.field('due_date', 'Due date', dueDate)),
  h('div', { class: 'actions' }, submit,
    h('a', { class: 'btn secondary', href: editing ? '#/projects/' + id : '#/projects' }, 'Cancel')));

  render(h('p', {}, h('a', { href: editing ? '#/projects/' + id : '#/projects' }, '← Back')),
    h('h1', {}, editing ? 'Edit project' : 'New project'), form);
  counts();
  loadOwners();
  name.focus();
}

/** Rank of each role on the Workspaces screen: the workspaces the member administers (usually created) come first. */
const ROLE_ORDER = { 'Workspace Administrator': 0, 'Project Manager': 1, 'Team Member': 2, 'Viewer': 3 };

/** Order of the Workspaces screen rows (E-22): by role, then by name. a and b are Membership objects from /auth/me. */
function compareMemberships(a, b) {
  const rank = r => (r in ROLE_ORDER ? ROLE_ORDER[r] : 99); // an unknown role goes last instead of breaking the sort
  return rank(a.role) - rank(b.role)
    || a.workspace_name.localeCompare(b.workspace_name, undefined, { sensitivity: 'base' });
}

// Workspaces screen (E-22; added 2026-11-19): every workspace the member belongs to, including those they created,
// with their role (from /auth/me), pending invitations with Accept (QM), and a link to each workspace's Members
// screen (E-05, Q9).
async function workspacesScreen() {
  const seq = screenSeq;
  state.profile = null; // always reload /auth/me here, so roles changed by someone else are current
  await loadProfile();
  const invitations = await myInvitationsOrNothing();
  if (seq !== screenSeq) return;
  showBar(true);
  const memberships = (state.profile.memberships || []).slice().sort(compareMemberships);
  if (!memberships.length) {
    render(takeFlash(), h('h1', {}, 'Workspaces'), invitations, invitations ? null : noWorkspaceYet());
    return;
  }
  const rows = memberships.map(m => {
    const members = h('td', {});
    // Each button names its workspace (NFR-06). After loading, focus moves to the list, which replaces the button.
    const show = h('button', { class: 'btn secondary', type: 'button',
      'aria-label': 'Show members of ' + m.workspace_name, onclick: async () => {
        show.disabled = true;
        members.replaceChildren(h('span', { role: 'status' }, 'Loading…'));
        try {
          const list = await api('GET', '/workspaces/' + encodeURIComponent(m.workspace_id) + '/members');
          if (seq !== screenSeq) return;
          const ul = h('ul', { class: 'plain', tabindex: '-1', 'aria-label': 'Members of ' + m.workspace_name },
            list.map(x => h('li', {}, x.name + ' — ' + x.role)));
          members.replaceChildren(ul);
          ul.focus();
        } catch (err) {
          if (err.status === 401 || seq !== screenSeq) return;
          members.replaceChildren(
            message(errorText(err, 'This workspace was not found, or you are no longer a member of it.')), show);
          show.disabled = false;
          show.focus();
        }
      } }, 'Show members');
    members.append(show);
    // E-05 Members screen (Q9): invitations and, for administrators, managing members. Named per workspace (NFR-06).
    const manage = h('td', { class: 'actions-inline' },
      h('a', { class: 'btn secondary', href: '#/workspaces/' + encodeURIComponent(m.workspace_id) + '/members',
        'aria-label': 'Open the Members screen of ' + m.workspace_name }, 'Members screen'));
    if (m.role === 'Workspace Administrator') { // Rename and Delete on E-22 (Q9), administrators only (Q1)
      manage.append(
        h('button', { class: 'btn secondary', type: 'button', 'aria-label': 'Rename ' + m.workspace_name,
          onclick: () => renameWorkspaceDialog(m) }, 'Rename'),
        h('button', { class: 'btn danger', type: 'button', 'aria-label': 'Delete ' + m.workspace_name,
          onclick: () => deleteWorkspaceDialog(m) }, 'Delete'));
    }
    return h('tr', {},
      h('th', { scope: 'row' }, m.workspace_name),
      h('td', {}, m.role),
      members,
      manage);
  });
  render(takeFlash(), h('h1', {}, 'Workspaces'), invitations,
    h('div', { class: 'panel' }, h('div', { class: 'wrap' }, h('table', {},
      h('caption', { class: 'skip' }, 'Your workspaces'),
      h('thead', {}, h('tr', {}, ['Workspace', 'Your role', 'Members', 'Manage'].map(t => h('th', { scope: 'col' }, t)))),
      h('tbody', {}, rows)))));
}

const ROLES = ['Workspace Administrator', 'Project Manager', 'Team Member', 'Viewer'];

/**
 * A modal dialog built for one use and removed when closed (same look as #confirm). run() is called on submit; when
 * it resolves to true the dialog closes, otherwise it stays open (for example to show an error from the API).
 * Escape and Cancel close it without changes (NFR-06: native <dialog> keeps focus inside and restores it).
 */
function openDialog(title, content, okLabel, okClass, run) {
  const titleId = 'dlg-' + Math.random().toString(36).slice(2);
  const error = h('div', { 'aria-live': 'assertive' });
  const ok = h('button', { type: 'submit', class: 'btn ' + (okClass || 'primary') }, okLabel);
  const cancel = h('button', { type: 'button', class: 'btn secondary', onclick: () => dialog.close() }, 'Cancel');
  const form = h('form', { novalidate: true, onsubmit: e => {
    e.preventDefault();
    guarded(form, ok, async () => {
      error.replaceChildren();
      try {
        if (await run(error)) dialog.close();
      } catch (err) {
        if (err.status === 401) { dialog.close(); return; }
        error.append(message(errorText(err)));
      }
    });
  } }, h('h2', { id: titleId }, title), content, error, h('div', { class: 'actions' }, cancel, ok));
  const dialog = h('dialog', { 'aria-labelledby': titleId }, form);
  dialog.addEventListener('close', () => dialog.remove());
  document.body.append(dialog);
  dialog.showModal();
  return { dialog, ok };
}

/**
 * Remove a member permanently (US-31, FR-03; decisions QA to QG, QR). What they own goes to the removing
 * administrator; administrators removing themselves choose another active Workspace Administrator to take it over.
 */
function removeMemberDialog(base, me, member, list) {
  const self = member.member_id === me.member_id;
  const kit = formKit('remove');
  const others = list.filter(x => x.role === 'Workspace Administrator' && x.member_id !== me.member_id);
  const successor = h('select', { required: true }, h('option', { value: '' }, 'Choose an administrator'),
    others.map(x => h('option', { value: x.member_id }, x.name)));
  const content = h('div', {},
    h('p', {}, self
      ? 'You will leave “' + me.workspace_name + '” and lose access to it at once.'
      : member.name + ' will be removed from “' + me.workspace_name + '”. Their tasks and projects will be '
        + 'given to you, and what they wrote will be shown as “Former member”.'),
    h('p', {}, 'This cannot be undone.'),
    self && others.length ? kit.field('new_owner_member_id', 'Who takes over your tasks and projects (required)', successor) : null,
    self && !others.length ? message('You are the only Workspace Administrator. Make another member a Workspace '
      + 'Administrator first.') : null);
  openDialog(self ? 'Leave this workspace?' : 'Remove ' + member.name + '?', content,
    self ? 'Leave workspace' : 'Remove member', 'danger', async () => {
      kit.clear();
      let query = '';
      if (self) {
        if (!successor.value) {
          kit.setError('new_owner_member_id', 'Choose the Workspace Administrator who takes over what you own.');
          kit.focusFirstError();
          return false;
        }
        query = '?new_owner_member_id=' + encodeURIComponent(successor.value);
      }
      try {
        await api('DELETE', base + '/members/' + encodeURIComponent(member.member_id) + query);
      } catch (err) {
        if (err.status === 422 && kit.showServerErrors(err)) return false;
        throw err;
      }
      state.profile = null;
      state.flash = self ? 'You left “' + me.workspace_name + '”.' : member.name + ' was removed.';
      if (self) go('#/workspaces');
      else route();
      return true;
    });
}

/** Rename (US-30, FR-23): BR-01, same rule and messages as New workspace. */
function renameWorkspaceDialog(m) {
  const kit = formKit('rename');
  const name = h('input', { type: 'text', required: true, maxlength: NAME_MAX + 20, value: m.workspace_name });
  openDialog('Rename workspace', kit.field('name', 'Name (required)', name, NAME_MIN + ' to ' + NAME_MAX + ' characters.'),
    'Save name', 'primary', async () => {
      kit.clear();
      const length = chars(name.value.trim());
      if (length < NAME_MIN || length > NAME_MAX) {
        kit.setError('name', 'The name must be ' + NAME_MIN + ' to ' + NAME_MAX + ' characters long.');
        kit.focusFirstError();
        return false;
      }
      try {
        await api('PATCH', '/workspaces/' + encodeURIComponent(m.workspace_id), { name: name.value.trim() });
      } catch (err) {
        if (err.status === 422 && kit.showServerErrors(err)) return false;
        throw err;
      }
      state.profile = null;
      state.flash = 'Workspace renamed.';
      go('#/workspaces');
      return true;
    });
  name.select();
}

/**
 * Delete (US-32, FR-24; decisions Q13, QN, QO): soft delete, confirmed by typing the exact name. The Delete button
 * stays disabled until the typed text, trimmed, equals the name; the API checks it again.
 */
function deleteWorkspaceDialog(m) {
  const kit = formKit('delete-ws');
  const typed = h('input', { type: 'text', required: true, autocomplete: 'off' });
  const content = h('div', {},
    h('p', {}, '“' + m.workspace_name + '” and everything in it will be hidden from all members. '
      + 'This cannot be undone in Matriz.'),
    kit.field('confirm_name', 'Type the workspace name to confirm', typed, m.workspace_name));
  const { ok } = openDialog('Delete workspace?', content, 'Delete workspace', 'danger', async () => {
    try {
      await api('DELETE', '/workspaces/' + encodeURIComponent(m.workspace_id), { confirm_name: typed.value.trim() });
    } catch (err) {
      if (err.status === 422 && kit.showServerErrors(err)) return false;
      throw err;
    }
    state.profile = null;
    state.flash = '“' + m.workspace_name + '” was deleted.';
    go('#/workspaces');
    return true;
  });
  ok.disabled = true;
  typed.addEventListener('input', () => { ok.disabled = typed.value.trim() !== m.workspace_name; });
  typed.focus();
}

// Members screen (E-05, opened from E-22; Q9 decided 2026-11-19). Every active member sees the list (AC-03.4); only
// Workspace Administrators see Invite and the pending invitations with Cancel (FR-03, Q1, Q4).
async function membersScreen(workspaceId) {
  const seq = screenSeq;
  state.profile = null;
  await loadProfile();
  if (seq !== screenSeq) return;
  showBar(true);
  const me = (state.profile.memberships || []).find(m => m.workspace_id === workspaceId);
  const back = h('p', {}, h('a', { href: '#/workspaces' }, '← Workspaces'));
  if (!me) {
    render(back, h('h1', {}, 'Members'),
      message('This workspace was not found, or you are no longer a member of it.'));
    return;
  }
  const admin = me.role === 'Workspace Administrator';
  const base = '/workspaces/' + encodeURIComponent(workspaceId);

  const memberList = section('Members', null, async () => {
    const list = await api('GET', base + '/members');
    if (!admin) {
      return h('div', { class: 'panel' }, h('div', { class: 'wrap' }, h('table', {},
        h('caption', { class: 'skip' }, 'Members of ' + me.workspace_name),
        h('thead', {}, h('tr', {}, ['Name', 'Role'].map(t => h('th', { scope: 'col' }, t)))),
        h('tbody', {}, list.map(x => h('tr', {}, h('th', { scope: 'row' }, x.name), h('td', {}, x.role)))))));
    }
    // Administrators: change role (US-04) and remove (US-31) per member, also themselves (QG).
    const status = h('div', { 'aria-live': 'polite' });
    const rows = list.map(x => {
      const self = x.member_id === me.member_id;
      const label = x.name + (self ? ' (you)' : '');
      const select = h('select', { 'aria-label': 'Role of ' + label },
        ROLES.map(r => h('option', { value: r, selected: r === x.role }, r)));
      const save = h('button', { class: 'btn secondary', type: 'button', 'aria-label': 'Save the role of ' + label,
        onclick: async () => {
          status.replaceChildren();
          save.disabled = true;
          try {
            await api('PATCH', base + '/members/' + encodeURIComponent(x.member_id), { role: select.value });
            state.flash = 'The role of ' + x.name + ' is now ' + select.value + '.';
            route(); // reload: the caller may have changed their own role
          } catch (err) {
            if (err.status === 401) return;
            status.append(message(errorText(err)));
            select.value = x.role;
            save.disabled = false;
          }
        } }, 'Save role');
      const remove = h('button', { class: 'btn danger', type: 'button', 'aria-label': 'Remove ' + label,
        onclick: () => removeMemberDialog(base, me, x, list) }, 'Remove');
      return h('tr', {}, h('th', { scope: 'row' }, label), h('td', {}, select),
        h('td', { class: 'actions-inline' }, save, remove));
    });
    return h('div', {}, status, h('div', { class: 'panel' }, h('div', { class: 'wrap' }, h('table', {},
      h('caption', { class: 'skip' }, 'Members of ' + me.workspace_name),
      h('thead', {}, h('tr', {}, ['Name', 'Role', 'Actions'].map(t => h('th', { scope: 'col' }, t)))),
      h('tbody', {}, rows)))));
  });

  if (!admin) {
    render(back, takeFlash(), h('h1', {}, 'Members of ' + me.workspace_name), memberList);
    return;
  }

  const pendingBox = h('div', {});
  const loadPending = async () => {
    pendingBox.replaceChildren(loading('Loading…'));
    try {
      const list = await api('GET', base + '/invitations');
      if (seq !== screenSeq) return;
      if (!list.length) {
        pendingBox.replaceChildren(h('p', { class: 'hint' }, 'No pending invitations.'));
        return;
      }
      pendingBox.replaceChildren(h('div', { class: 'panel' }, h('div', { class: 'wrap' }, h('table', {},
        h('caption', { class: 'skip' }, 'Pending invitations'),
        h('thead', {}, h('tr', {}, ['Email', 'Role', 'Invited by', 'Sent', 'Action'].map(t => h('th', { scope: 'col' }, t)))),
        h('tbody', {}, list.map(inv => {
          const cancel = h('button', { class: 'btn secondary', type: 'button',
            'aria-label': 'Cancel the invitation to ' + inv.email, onclick: async () => {
              cancel.disabled = true;
              try {
                await api('DELETE', base + '/invitations/' + encodeURIComponent(inv.invitation_id));
                await loadPending();
              } catch (err) {
                if (err.status === 401) return;
                pendingBox.prepend(message(errorText(err, 'This invitation is no longer pending.')));
                cancel.disabled = false;
              }
            } }, 'Cancel');
          return h('tr', {}, h('th', { scope: 'row' }, inv.email), h('td', {}, inv.role),
            h('td', {}, inv.invited_by_name), h('td', {}, formatDateTime(inv.created_at)), h('td', {}, cancel));
        }))))));
    } catch (err) {
      if (err.status !== 401 && seq === screenSeq) pendingBox.replaceChildren(failure(err, loadPending));
    }
  };

  // Invite form (US-03): email and role, with messages at each field (AC-03.2, AC-03.3, EC-01).
  const kit = formKit('invite');
  const email = h('input', { type: 'email', required: true, maxlength: 254, autocomplete: 'off' });
  const role = h('select', { required: true }, h('option', { value: '' }, 'Choose a role'),
    ROLES.map(r => h('option', { value: r }, r)));
  const banner = h('div', { 'aria-live': 'assertive' });
  const submit = h('button', { class: 'btn primary', type: 'submit' }, 'Send invitation');
  const form = h('form', { class: 'panel', novalidate: true, onsubmit: e => {
    e.preventDefault();
    guarded(form, submit, async () => {
      banner.replaceChildren();
      kit.clear();
      const value = email.value.trim();
      if (!value || !/^[^@\s]+@[^@\s]+$/.test(value)) kit.setError('email', 'Enter a valid email address.');
      if (!role.value) kit.setError('role', 'Choose one of the four roles.');
      if (kit.focusFirstError()) {
        banner.append(message('Check the highlighted fields.'));
        return;
      }
      try {
        const inv = await api('POST', base + '/invitations', { email: value, role: role.value });
        banner.append(message('Invitation sent to ' + inv.email + '.', 'info'));
        email.value = '';
        role.value = '';
        await loadPending();
        email.focus();
      } catch (err) {
        if (err.status === 401) return;
        const shown = (err.status === 422 || err.status === 409) && kit.showServerErrors(err);
        banner.append(message(shown ? 'Check the highlighted fields.' : errorText(err, 'The workspace was not found.')));
      }
    });
  } },
  banner,
  kit.field('email', 'Email address (required)', email),
  kit.field('role', 'Role (required)', role),
  h('div', { class: 'actions' }, submit));

  render(back, takeFlash(), h('h1', {}, 'Members of ' + me.workspace_name), memberList,
    h('section', { class: 'dash-section', 'aria-labelledby': 'invite-title' },
      h('h2', {}, h('span', { id: 'invite-title' }, 'Invite a person')), form),
    h('section', { class: 'dash-section', 'aria-labelledby': 'pending-title' },
      h('h2', {}, h('span', { id: 'pending-title' }, 'Pending invitations')), pendingBox));
  loadPending();
}

// New workspace (US-02, FR-02, BR-01): any signed-in person. The API makes the creator its Workspace Administrator.
async function workspaceFormScreen() {
  const seq = screenSeq;
  await loadProfile();
  if (seq !== screenSeq) return;
  showBar(true);
  const kit = formKit('workspace');
  const name = h('input', { type: 'text', required: true, maxlength: NAME_MAX + 20 });
  const nameCount = h('span', {});
  const banner = h('div', { 'aria-live': 'assertive' });
  const submit = h('button', { class: 'btn primary', type: 'submit' }, 'Create workspace');
  name.addEventListener('input', () => {
    nameCount.textContent = chars(name.value.trim()) + ' / ' + NAME_MAX;
  });
  const form = h('form', { class: 'panel', novalidate: true, onsubmit: e => {
    e.preventDefault();
    guarded(form, submit, async () => {
      banner.replaceChildren();
      kit.clear();
      const length = chars(name.value.trim());
      if (length < NAME_MIN || length > NAME_MAX) {
        kit.setError('name', 'The name must be ' + NAME_MIN + ' to ' + NAME_MAX + ' characters long.');
        banner.append(message('Check the highlighted fields.'));
        kit.focusFirstError();
        return;
      }
      try {
        await api('POST', '/workspaces', { name: name.value.trim() });
        state.profile = null; // reload it so the new membership is known (Workspaces screen, "+ New project")
        state.flash = 'Workspace created.';
        go('#/dashboard');
      } catch (err) {
        if (err.status === 401) return;
        const shown = err.status === 422 && kit.showServerErrors(err);
        banner.append(message(shown ? 'Check the highlighted fields.' : errorText(err, 'The workspace could not be saved.')));
      }
    });
  } },
  banner,
  kit.field('name', 'Name (required)', name, h('span', {}, NAME_MIN + ' to ' + NAME_MAX + ' characters. ', nameCount)),
  h('div', { class: 'actions' }, submit, h('a', { class: 'btn secondary', href: '#/dashboard' }, 'Cancel')));
  render(h('p', {}, h('a', { href: '#/dashboard' }, '← Back')), h('h1', {}, 'New workspace'), form);
  name.focus();
}

// ---------------------------------------------------------------------------------------------------------------
// Routing (hash based, so the server only serves index.html).
// ---------------------------------------------------------------------------------------------------------------

async function route() {
  screenSeq += 1;
  const [path, query] = (location.hash.slice(1) || HOME.slice(1)).split('?');
  const params = new URLSearchParams(query || '');
  const parts = path.split('/').filter(Boolean);
  try {
    if (parts[0] === 'sign-in') return signInScreen();
    if (parts[0] === 'sign-up') return signUpScreen();
    if (parts[0] === 'welcome' && parts.length === 1) return await welcomeScreen();
    if (parts[0] === 'dashboard' && parts.length === 1) return await dashboardScreen();
    if (parts[0] === 'projects' && parts.length === 1) return await projectsScreen(params);
    if (parts[0] === 'projects' && parts[1] === 'new') return await projectFormScreen(null);
    if (parts[0] === 'projects' && parts.length === 2) return await projectDetailScreen(parts[1], params);
    if (parts[0] === 'projects' && parts[2] === 'edit') return await projectFormScreen(parts[1]);
    if (parts[0] === 'workspaces' && parts.length === 1) return await workspacesScreen();
    if (parts[0] === 'workspaces' && parts[1] === 'new') return await workspaceFormScreen();
    if (parts[0] === 'workspaces' && parts.length === 3 && parts[2] === 'members') {
      return await membersScreen(decodeURIComponent(parts[1]));
    }
    go(HOME);
  } catch (err) {
    if (err.status !== 401) render(failure(err, route));
  }
}

window.addEventListener('hashchange', route);
route();
