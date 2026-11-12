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
const TASK_TITLE_MIN = 3;
const TASK_TITLE_MAX = 150;
const TASK_DESCRIPTION_MAX = 2000;
const COMMENT_MAX = 2000;

// Task statuses. The API always sends and receives these technical values (openapi.yaml: TaskStatus); the interface
// shows Portuguese labels (PD-13, decided 2026-11-12 for task statuses). This is the only place they are mapped.
const TASK_STATUSES = ['To do', 'In progress', 'Done'];
const STATUS_LABELS = { 'To do': 'Por fazer', 'In progress': 'Em curso', 'Done': 'Concluída' };

function statusLabel(value) {
  return STATUS_LABELS[value] || value;
}

const main = document.getElementById('main');
const state = { profile: null, flash: null };

// ---------------------------------------------------------------------------------------------------------------
// API access: session cookie, CSRF header, and sign-in redirect on 401.
// ---------------------------------------------------------------------------------------------------------------

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

async function ensureCsrfToken() {
  if (!csrfToken()) {
    await fetch('/auth/csrf', { credentials: 'same-origin' });
  }
}

async function api(method, path, body, retried) {
  const options = { method, credentials: 'same-origin', headers: { Accept: 'application/json' } };
  if (method !== 'GET') {
    await ensureCsrfToken();
    options.headers['X-XSRF-TOKEN'] = csrfToken();
  }
  if (body !== undefined) {
    options.headers['Content-Type'] = 'application/json';
    options.body = JSON.stringify(body);
  }
  let response;
  try {
    response = await fetch(path, options);
  } catch (e) {
    throw new ApiError(0, { message: 'Matriz could not be reached. Check that it is running and try again.' });
  }
  const text = await response.text();
  const data = text ? JSON.parse(text) : null;
  if (response.ok) {
    return data;
  }
  if (response.status === 403 && data && data.code === 'csrf_invalid' && !retried) {
    await fetch('/auth/csrf', { credentials: 'same-origin' });
    return api(method, path, body, true);
  }
  if (response.status === 401 && path !== '/auth/sign-in') {
    state.profile = null;
    state.flash = data && data.code === 'session_expired'
      ? 'Your session has ended. Sign in again.'
      : 'Sign in to continue.';
    go('#/sign-in');
    throw new ApiError(401, data);
  }
  throw new ApiError(response.status, data);
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
    h('p', {}, h('strong', {}, 'This section could not be loaded. '), error.message),
    h('button', { class: 'btn', type: 'button', onclick: retry }, 'Try again'));
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
  return (state.profile ? state.profile.memberships : []).filter(m => m.role === 'Project Manager');
}

function canManage(workspaceId) {
  return managedWorkspaces().some(m => m.workspace_id === workspaceId);
}

function showBar(visible) {
  const bar = document.getElementById('bar');
  bar.hidden = !visible;
  if (visible && state.profile) {
    document.getElementById('acct-button').textContent = state.profile.name + ' ▾';
  }
  document.getElementById('nav-projects').classList.toggle('on', location.hash.startsWith('#/projects'));
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
  state.flash = 'You have signed out.';
  go('#/sign-in');
});

// ---------------------------------------------------------------------------------------------------------------
// Screens.
// ---------------------------------------------------------------------------------------------------------------

function signInScreen() {
  showBar(false);
  const error = h('div', { 'aria-live': 'assertive' });
  const email = h('input', { id: 'email', type: 'email', autocomplete: 'username', required: true });
  const password = h('input', { id: 'password', type: 'password', autocomplete: 'current-password', required: true });
  const submit = h('button', { class: 'btn primary', type: 'submit' }, 'Sign in');
  const form = h('form', { class: 'panel', novalidate: true, onsubmit: async e => {
    e.preventDefault();
    error.replaceChildren();
    if (!email.value.trim() || !password.value) {
      error.append(message('Enter your email and password.'));
      return;
    }
    submit.disabled = true;
    try {
      await ensureCsrfToken();
      state.profile = await api('POST', '/auth/sign-in', { email: email.value.trim(), password: password.value });
      go('#/projects');
    } catch (err) {
      error.append(message(err.status === 401 ? 'The email or password is incorrect.' : err.message));
      password.value = '';
      password.focus();
    } finally {
      submit.disabled = false;
    }
  } },
  h('h1', {}, 'Sign in to Matriz'),
  error,
  h('div', { class: 'field' }, h('label', { for: 'email' }, 'Email'), email),
  h('div', { class: 'field' }, h('label', { for: 'password' }, 'Password'), password),
  submit);
  render(h('div', { class: 'narrow' }, takeFlash(), form));
  email.focus();
}

async function projectsScreen(params) {
  const q = params.get('q') || '';
  const page = Math.max(0, parseInt(params.get('page') || '0', 10) || 0);
  await loadProfile();
  showBar(true);

  const search = h('input', { id: 'search', type: 'search', value: q, maxlength: NAME_MAX,
    placeholder: 'Part of a project name' });
  const searchForm = h('form', { class: 'toolbar', role: 'search', onsubmit: e => {
    e.preventDefault();
    const text = search.value.trim();
    go('#/projects' + (text ? '?q=' + encodeURIComponent(text) : ''));
  } },
  h('div', { class: 'field' }, h('label', { for: 'search' }, 'Search projects by name'), search),
  h('button', { class: 'btn', type: 'submit' }, 'Search'),
  q ? h('a', { class: 'btn secondary', href: '#/projects' }, 'Clear') : null);

  const newButton = managedWorkspaces().length ? h('a', { class: 'btn primary', href: '#/projects/new' }, '+ New project') : null;
  const results = h('div', {}, loading('Loading projects…'));
  render(takeFlash(), h('h1', {}, 'Projects', newButton), searchForm, results);

  const load = async () => {
    results.replaceChildren(loading('Loading projects…'));
    try {
      const query = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
      if (q) query.set('q', q);
      const data = await api('GET', '/projects?' + query);
      let progress = {};
      if (data.items.length) {
        const ids = new URLSearchParams();
        data.items.forEach(p => ids.append('project_id', p.project_id));
        (await api('GET', '/progress?' + ids)).forEach(r => { progress[r.project_id] = r; });
      }
      results.replaceChildren(projectTable(data, progress, q));
    } catch (err) {
      if (err.status !== 401) results.replaceChildren(failure(err, load));
    }
  };
  load();
}

function projectTable(data, progress, q) {
  if (!data.items.length) {
    if (q) {
      return h('div', { class: 'empty' }, h('strong', {}, 'No projects match “' + q + '”.'), 'Try another part of the name.');
    }
    return h('div', { class: 'empty' }, h('strong', {}, 'No projects yet.'),
      managedWorkspaces().length ? h('a', { class: 'btn primary', href: '#/projects/new' }, '+ New project') : null);
  }
  const rows = data.items.map(p => {
    const pr = progress[p.project_id];
    return h('tr', {},
      h('td', {}, h('a', { href: '#/projects/' + p.project_id }, p.name),
        p.is_archived ? h('span', {}, ' ', h('span', { class: 'tag' }, 'Archived')) : null),
      h('td', {}, p.workspace_name),
      h('td', {}, p.owner_name || '—'),
      h('td', {}, formatDate(p.due_date)),
      h('td', {}, pr ? pr.done + ' of ' + pr.total + ' tasks done' : '—'));
  });
  const pages = Math.max(1, Math.ceil(data.total / data.size));
  const link = (target, label) => {
    const query = new URLSearchParams();
    if (q) query.set('q', q);
    if (target > 0) query.set('page', String(target));
    const qs = query.toString();
    return h('a', { class: 'btn secondary', href: '#/projects' + (qs ? '?' + qs : '') }, label);
  };
  return h('div', { class: 'panel' },
    h('div', { class: 'wrap' }, h('table', {},
      h('caption', { class: 'skip' }, 'Projects'),
      h('thead', {}, h('tr', {}, ['Project', 'Workspace', 'Owner', 'Due date', 'Progress'].map(t => h('th', { scope: 'col' }, t)))),
      h('tbody', {}, rows))),
    h('nav', { class: 'pager', 'aria-label': 'Pages' },
      data.page > 0 ? link(data.page - 1, '← Previous') : null,
      h('span', {}, 'Page ' + (data.page + 1) + ' of ' + pages + ' · ' + data.total + ' project' + (data.total === 1 ? '' : 's')),
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
  const progressText = h('dd', { id: 'project-progress' }, progressLabel(progress));
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
  const writer = me.role === 'Project Manager' || me.role === 'Team Member';
  const isPm = me.role === 'Project Manager';
  // Mirrors AccessPolicy so that refused actions are hidden (CMP-11); the backend still decides.
  const can = {
    create: writer,
    edit: t => isPm || (writer && t.owner_member_id === me.member_id),
    reassign: isPm,
    status: t => writer && t.owner_member_id === me.member_id,
    remove: t => writer && t.created_by_member_id === me.member_id,
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
    counts.replaceChildren(...TASK_STATUSES.flatMap((s, i) =>
      [i ? ' ' : '', h('span', { class: 'tag' }, statusLabel(s) + ': ' + (by[s] || 0))]));
    progress.done = by['Done'] || 0;
    progress.total = list.reduce((n, c) => n + c.count, 0);
    progressText.textContent = progressLabel(progress);
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
      h('td', {}, can.status(t) ? statusSelect(t) : h('span', { class: 'tag' }, statusLabel(t.status))),
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
        h('dt', {}, 'Status'), h('dd', {}, statusLabel(task.status))),
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

    const contentAllowed = !editing || can.edit(task);
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

async function projectFormScreen(id) {
  await loadProfile();
  showBar(true);
  const editing = Boolean(id);
  let project = null;
  if (editing) {
    render(loading('Loading the project…'));
    try {
      project = await api('GET', '/projects/' + encodeURIComponent(id));
    } catch (err) {
      if (err.status !== 401) render(message(err.status === 404 ? 'This project was not found.' : err.message));
      return;
    }
    if (!canManage(project.workspace_id)) {
      render(message('Only Project Managers can edit projects.'), h('a', { class: 'btn', href: '#/projects/' + id }, '← Back'));
      return;
    }
  } else if (!managedWorkspaces().length) {
    render(message('Only Project Managers can create projects.'), h('a', { class: 'btn', href: '#/projects' }, '← Back'));
    return;
  }

  const fields = {};
  const field = (name, label, control, hint) => {
    const error = h('div', { class: 'error', id: name + '-error', hidden: true });
    control.id = name;
    control.setAttribute('aria-describedby', name + '-error' + (hint ? ' ' + name + '-hint' : ''));
    const wrapper = h('div', { class: 'field' }, h('label', { for: name }, label), control,
      hint ? h('div', { class: 'hint', id: name + '-hint' }, hint) : null, error);
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

  const loadOwners = async () => {
    owner.replaceChildren(h('option', { value: '' }, 'Loading members…'));
    try {
      const list = await api('GET', '/workspaces/' + encodeURIComponent(workspace.value) + '/members');
      const me = state.profile.memberships.find(m => m.workspace_id === workspace.value);
      const selected = editing ? (project.owner_member_id || '') : (me ? me.member_id : '');
      owner.replaceChildren(h('option', { value: '' }, 'No owner'),
        ...list.map(m => h('option', { value: m.member_id, selected: m.member_id === selected }, m.name + ' (' + m.role + ')')));
      owner.value = selected;
    } catch (err) {
      if (err.status !== 401) owner.replaceChildren(h('option', { value: '' }, 'Members could not be loaded'));
    }
  };
  workspace.addEventListener('change', loadOwners);

  const validate = () => {
    const n = chars(name.value.trim());
    const nameError = n < NAME_MIN || n > NAME_MAX ? 'The name must be 3 to 100 characters long.' : '';
    const descriptionError = chars(description.value) > DESCRIPTION_MAX
      ? 'The description can be at most 1000 characters long.' : '';
    setError('name', nameError);
    setError('description', descriptionError);
    return !nameError && !descriptionError;
  };

  const form = h('form', { class: 'panel', novalidate: true, onsubmit: async e => {
    e.preventDefault();
    banner.replaceChildren();
    Object.keys(fields).forEach(k => setError(k, ''));
    if (!validate()) {
      banner.append(message('Check the highlighted fields.'));
      const first = Object.values(fields).find(f => !f.error.hidden);
      if (first) first.control.focus();
      return;
    }
    const body = {
      name: name.value.trim(),
      description: description.value === '' ? null : description.value,
      owner_member_id: owner.value || null,
      start_date: startDate.value || null,
      due_date: dueDate.value || null,
    };
    if (!editing) body.workspace_id = workspace.value;
    submit.disabled = true;
    try {
      const saved = editing
        ? await api('PATCH', '/projects/' + encodeURIComponent(id), body)
        : await api('POST', '/projects', body);
      state.flash = editing ? 'Changes saved.' : 'Project created.';
      go('#/projects/' + saved.project_id);
    } catch (err) {
      if (err.status === 401) return;
      const details = (err.body && err.body.details) || [];
      let shown = false;
      details.forEach(d => { shown = setError(d.field, d.issue) || shown; });
      banner.append(message(shown ? 'Check the highlighted fields.' : err.message));
    } finally {
      submit.disabled = false;
    }
  } },
  banner,
  field('workspace_id', 'Workspace', workspace, editing ? 'A project stays in its workspace.' : null),
  field('name', 'Name (required)', name, h('span', {}, '3 to 100 characters. ', nameCount)),
  field('description', 'Description', description, h('span', {}, 'Up to 1000 characters. ', descriptionCount)),
  field('owner_member_id', 'Owner', owner, 'You are the owner unless you choose someone else.'),
  h('div', { class: 'row' }, field('start_date', 'Start date', startDate), field('due_date', 'Due date', dueDate)),
  h('div', { class: 'actions' }, submit,
    h('a', { class: 'btn secondary', href: editing ? '#/projects/' + id : '#/projects' }, 'Cancel')));

  render(h('p', {}, h('a', { href: editing ? '#/projects/' + id : '#/projects' }, '← Back')),
    h('h1', {}, editing ? 'Edit project' : 'New project'), form);
  counts();
  loadOwners();
  name.focus();
}

// ---------------------------------------------------------------------------------------------------------------
// Routing (hash based, so the server only serves index.html).
// ---------------------------------------------------------------------------------------------------------------

async function route() {
  const [path, query] = (location.hash.slice(1) || '/projects').split('?');
  const params = new URLSearchParams(query || '');
  const parts = path.split('/').filter(Boolean);
  try {
    if (parts[0] === 'sign-in') return signInScreen();
    if (parts[0] === 'projects' && parts.length === 1) return await projectsScreen(params);
    if (parts[0] === 'projects' && parts[1] === 'new') return await projectFormScreen(null);
    if (parts[0] === 'projects' && parts.length === 2) return await projectDetailScreen(parts[1], params);
    if (parts[0] === 'projects' && parts[2] === 'edit') return await projectFormScreen(parts[1]);
    go('#/projects');
  } catch (err) {
    if (err.status !== 401) render(failure(err, route));
  }
}

window.addEventListener('hashchange', route);
route();
