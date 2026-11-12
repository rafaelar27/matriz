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

async function projectDetailScreen(id) {
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
    render(failure(err, () => projectDetailScreen(id)));
    return;
  }
  const manage = canManage(project.workspace_id);
  const deleteButton = manage ? h('button', { class: 'btn danger', type: 'button', onclick: () => confirmDelete(project, progress) },
    'Delete project') : null;
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
        h('dt', {}, 'Progress'), h('dd', {}, progress ? progress.done + ' of ' + progress.total + ' tasks done' : '—')),
      h('h2', { class: 'skip' }, 'Description'),
      h('p', { class: 'description' }, project.description || 'No description.'),
      manage ? h('div', { class: 'actions' },
        h('a', { class: 'btn', href: '#/projects/' + project.project_id + '/edit' }, 'Edit project'), deleteButton) : null));
}

function confirmDelete(project, progress) {
  const dialog = document.getElementById('confirm');
  const tasks = progress ? progress.total : 0;
  document.getElementById('confirm-text').textContent = '“' + project.name + '” will be deleted' +
    (tasks ? ', together with its ' + tasks + ' task' + (tasks === 1 ? '' : 's') + ' and their comments' : '') +
    '. This cannot be undone.';
  dialog.returnValue = '';
  dialog.onclose = async () => {
    if (dialog.returnValue !== 'delete') return;
    try {
      await api('DELETE', '/projects/' + encodeURIComponent(project.project_id));
      state.flash = '“' + project.name + '” was deleted.';
      go('#/projects');
    } catch (err) {
      if (err.status !== 401) {
        main.prepend(message(err.status === 404 ? 'This project no longer exists.' : err.message));
      }
    }
  };
  dialog.showModal();
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
    if (parts[0] === 'projects' && parts.length === 2) return await projectDetailScreen(parts[1]);
    if (parts[0] === 'projects' && parts[2] === 'edit') return await projectFormScreen(parts[1]);
    go('#/projects');
  } catch (err) {
    if (err.status !== 401) render(failure(err, route));
  }
}

window.addEventListener('hashchange', route);
route();
