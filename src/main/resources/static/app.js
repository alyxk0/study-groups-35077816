'use strict';
const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
const esc = (value) =>
  String(value ?? '—').replace(
    /[&<>"']/g,
    (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c],
  );
const state = {
  view: 'groups',
  page: 0,
  size: 10,
  sort: 'id',
  direction: 'asc',
  filter: '',
  value: null,
  groups: [],
  refs: { coordinates: [], locations: [], people: [] },
  session: null,
  enums: null,
  stream: null,
  groupEdit: null,
  refEdit: null,
  details: null,
  confirm: null,
};
const labels = {
  name: 'Название',
  coordinates: 'Координаты',
  creationDate: 'Дата создания',
  studentsCount: 'Число студентов',
  expelledStudents: 'Отчислены',
  transferredStudents: 'Переведены',
  formOfEducation: 'Форма обучения',
  shouldBeExpelled: 'К отчислению',
  averageMark: 'Средний балл',
  semesterEnum: 'Семестр',
  groupAdmin: 'Староста',
  eyeColor: 'Цвет глаз',
  hairColor: 'Цвет волос',
  birthday: 'Дата рождения',
  nationality: 'Гражданство',
  location: 'Локация',
  id: 'ID',
  version: 'Версия',
  x: 'X',
  y: 'Y',
  z: 'Z',
};
const names = {
  groups: 'Группы',
  people: 'Старосты',
  coordinates: 'Координаты',
  locations: 'Локации',
  operations: 'Операции',
  guide: 'Справка',
};
const singular = { people: 'старосту', coordinates: 'координаты', locations: 'локацию' };
const forms = {
  DISTANCE_EDUCATION: 'Дистанционная',
  FULL_TIME_EDUCATION: 'Очная',
  EVENING_CLASSES: 'Вечерняя',
};
const semesters = {
  FIRST: '1-й семестр',
  SECOND: '2-й семестр',
  THIRD: '3-й семестр',
  FOURTH: '4-й семестр',
  EIGHTH: '8-й семестр',
};
const colors = { GREEN: 'Зелёный', ORANGE: 'Оранжевый', BROWN: 'Коричневый' };
const countries = {
  UNITED_KINGDOM: 'Великобритания',
  GERMANY: 'Германия',
  VATICAN: 'Ватикан',
  SOUTH_KOREA: 'Южная Корея',
};
let registerMode = false,
  toastTimer,
  refreshTimer,
  groupRequest = 0;

// Preserve 64-bit integer values from the server instead of rounding them in JSON.parse.
function parseJson(text) {
  let out = '',
    i = 0;
  while (i < text.length) {
    if (text[i] === '"') {
      const start = i++;
      while (i < text.length) {
        if (text[i] === '\\') i += 2;
        else if (text[i++] === '"') break;
      }
      out += text.slice(start, i);
    } else if (text[i] === '-' || /[0-9]/.test(text[i])) {
      const match = text.slice(i).match(/^-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?/);
      if (!match) {
        out += text[i++];
        continue;
      }
      const token = match[0];
      i += token.length;
      out +=
        /^-?\d+$/.test(token) && !Number.isSafeInteger(Number(token)) ? '"' + token + '"' : token;
    } else out += text[i++];
  }
  return JSON.parse(out);
}
async function api(path, options = {}) {
  const { body, ...rest } = options;
  const headers = { ...options.headers };
  if (body !== undefined && !(body instanceof URLSearchParams))
    headers['Content-Type'] = 'application/json';
  if (state.session) headers[state.session.csrfHeader] = state.session.csrfToken;
  const response = await fetch('/api' + path, {
    ...rest,
    headers,
    credentials: 'same-origin',
    body:
      body instanceof URLSearchParams
        ? body
        : body === undefined
          ? undefined
          : JSON.stringify(body),
  });
  const text = await response.text();
  let data;
  try {
    data = text ? parseJson(text) : null;
  } catch {
    data = { message: 'Не удалось прочитать ответ сервера' };
  }
  if (!response.ok) {
    if (response.status === 401 && path !== '/login') showAuth();
    const error = new Error(data?.message || 'Не удалось выполнить операцию');
    error.fields = data?.fields || {};
    error.status = response.status;
    throw error;
  }
  return data;
}
function notify(message) {
  $('#toast').textContent = message;
  $('#toast').hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => ($('#toast').hidden = true), 5000);
}
function on(selector, event, handler) {
  $(selector).addEventListener(event, async (e) => {
    try {
      await handler(e);
    } catch (error) {
      notify(error.message);
    }
  });
}
function clearErrors(form) {
  $('.form-error', form)?.setAttribute('hidden', '');
  $$('.field-error', form).forEach((el) => el.remove());
  $$('.invalid', form).forEach((el) => el.classList.remove('invalid'));
}
function showError(form, error) {
  const box = $('.form-error', form);
  if (box) {
    box.textContent = error.message;
    box.hidden = false;
  }
  for (const [name, message] of Object.entries(error.fields || {})) {
    const input = form.elements.namedItem(name);
    if (!input) continue;
    input.classList.add('invalid');
    const small = document.createElement('small');
    small.className = 'field-error';
    small.textContent = message;
    input.after(small);
  }
}
async function submit(form, action) {
  clearErrors(form);
  const button = $('button[type="submit"], button.primary:not([type="button"])', form);
  if (button) button.disabled = true;
  try {
    await action();
  } catch (error) {
    showError(form, error);
  } finally {
    if (button) button.disabled = false;
  }
}
function integer(value, name, bits = 64, positive = false) {
  if (!/^-?\d+$/.test(value)) throw new Error(name + ': требуется целое число');
  const n = BigInt(value),
    max = (1n << BigInt(bits - 1)) - 1n,
    min = positive ? 1n : -(1n << BigInt(bits - 1));
  if (n < min || n > max)
    throw new Error(
      name + ': число вне допустимого диапазона' + (positive ? ' (должно быть больше 0)' : ''),
    );
  return n.toString();
}
function number(value, name, positive = false) {
  const n = Number(value);
  if (value.trim() === '' || !Number.isFinite(n) || (positive && n <= 0))
    throw new Error(name + ': введите ' + (positive ? 'положительное ' : '') + 'конечное число');
  return n;
}
function enumOptions(items, dictionary, optional = false) {
  return (
    (optional
      ? '<option value="">Не указано</option>'
      : '<option value="" disabled selected>Выберите…</option>') +
    items
      .map(
        (item) =>
          '<option value="' + esc(item) + '">' + esc(dictionary[item] || item) + '</option>',
      )
      .join('')
  );
}
function referenceOptions(kind, optional = false) {
  let html = optional
    ? '<option value="">Без старосты</option>'
    : '<option value="">Выберите…</option>';
  for (const item of state.refs[kind])
    html +=
      '<option value="' +
      esc(item.id) +
      '">#' +
      esc(item.id) +
      ' · ' +
      esc(item.name || `X: ${item.x}, Y: ${item.y}`) +
      '</option>';
  return html;
}
function fillSelect(select, html, value) {
  const old = value ?? select.value;
  select.innerHTML = html;
  if ([...select.options].some((o) => o.value === String(old))) select.value = String(old);
}
function refreshSelectors() {
  const form = $('#group-form');
  fillSelect(
    form.elements.coordinatesId,
    referenceOptions('coordinates'),
    form.elements.coordinatesId.value,
  );
  fillSelect(
    form.elements.groupAdminId,
    referenceOptions('people', true),
    form.elements.groupAdminId.value,
  );
  const location = $('#reference-form').elements.locationId;
  if (location) fillSelect(location, referenceOptions('locations'), location.value);
}
async function loadReferences() {
  const results = await Promise.all(
    ['coordinates', 'locations', 'people'].map((kind) => api('/references/' + kind)),
  );
  ['coordinates', 'locations', 'people'].forEach((kind, i) => (state.refs[kind] = results[i]));
  refreshSelectors();
}
function showAuth() {
  state.stream?.close();
  state.stream = null;
  $$('dialog[open]').forEach((d) => d.close());
  $('#app-view').hidden = true;
  $('#auth-view').hidden = false;
  $('#loading').hidden = true;
}
async function enterApp() {
  state.session = await api('/session');
  if (!state.session.authenticated) {
    showAuth();
    return;
  }
  state.enums = await api('/enums');
  $('#auth-view').hidden = true;
  $('#app-view').hidden = false;
  $('#loading').hidden = true;
  $('#username').textContent = state.session.username;
  $('#group-form').elements.formOfEducation.innerHTML = enumOptions(state.enums.forms, forms);
  $('#group-form').elements.semesterEnum.innerHTML = enumOptions(state.enums.semesters, semesters);
  $$('.semester-select').forEach(
    (s) => (s.innerHTML = enumOptions(state.enums.semesters, semesters)),
  );
  await loadReferences();
  await navigate(state.view);
  connectEvents();
}
function connectEvents() {
  state.stream?.close();
  state.stream = new EventSource('/api/events');
  state.stream.addEventListener('ready', () => {
    $('#live-dot').classList.add('online');
    $('#live-label').textContent = 'Обновления подключены';
    refreshCurrent().catch((e) => notify(e.message));
  });
  state.stream.addEventListener('change', (event) => {
    const change = parseJson(event.data);
    if (
      $('#group-dialog').open &&
      state.groupEdit &&
      (change.entity === 'references' || String(change.id) === String(state.groupEdit.id))
    )
      $('#group-stale').hidden = false;
    ['sum-result', 'max-result', 'count-result'].forEach((id) => $('#' + id).replaceChildren());
    clearTimeout(refreshTimer);
    refreshTimer = setTimeout(() => refreshCurrent().catch((e) => notify(e.message)), 180);
  });
  state.stream.onerror = () => {
    $('#live-dot').classList.remove('online');
    $('#live-label').textContent = 'Переподключение…';
  };
}
async function refreshCurrent() {
  await loadReferences();
  if (state.view === 'groups') await loadGroups();
  else if (state.refs[state.view]) renderReferences();
  if ($('#details-dialog').open && state.details) {
    try {
      const item = await fetchItem(state.details.kind, state.details.item.id);
      renderDetails(state.details.kind, item);
    } catch (e) {
      if (e.status === 404) {
        $('#details-dialog').close();
        notify('Объект удалён другим пользователем');
      } else throw e;
    }
  }
}
async function navigate(view) {
  state.view = view;
  $$('.nav-item').forEach((b) => b.classList.toggle('active', b.dataset.view === view));
  $('#page-title').textContent = names[view];
  ['groups', 'references', 'operations', 'guide'].forEach(
    (v) => ($('#' + v + '-view').hidden = true),
  );
  const reference = Boolean(state.refs[view]);
  $('#' + (reference ? 'references' : view) + '-view').hidden = false;
  $('#create').hidden = !reference && view !== 'groups';
  $('#create').textContent = view === 'groups' ? 'Добавить группу' : 'Добавить ' + singular[view];
  if (view === 'groups') await loadGroups();
  else if (reference) renderReferences();
}
function date(value) {
  if (!value) return '—';
  const d = new Date(value);
  return isNaN(d)
    ? String(value)
    : d.toLocaleString('ru-RU', { dateStyle: 'short', timeStyle: 'short' });
}
function actions(kind, item) {
  return (
    '<div class="row-actions"><button data-action="details" data-kind="' +
    kind +
    '" data-id="' +
    esc(item.id) +
    '">Открыть</button><button data-action="edit" data-kind="' +
    kind +
    '" data-id="' +
    esc(item.id) +
    '">Изменить</button><button class="delete-action" data-action="delete" data-kind="' +
    kind +
    '" data-id="' +
    esc(item.id) +
    '">Удалить</button></div>'
  );
}
async function loadGroups() {
  const request = ++groupRequest;
  const query = new URLSearchParams({
    page: state.page,
    size: state.size,
    sort: state.sort,
    direction: state.direction,
    filter: state.filter,
  });
  if (state.value !== null) query.set('value', state.value);
  const page = await api('/groups?' + query);
  if (request !== groupRequest) return;
  const lastPage = Math.max(0, Math.ceil(page.total / state.size) - 1);
  if (state.page > lastPage) {
    state.page = lastPage;
    return loadGroups();
  }
  state.groups = page.items;
  $('#groups-table tbody').innerHTML = page.items
    .map((g) => {
      const a = g.groupAdmin,
        c = g.coordinates;
      return (
        '<tr><td class="id-cell">#' +
        esc(g.id) +
        '</td><td><strong>' +
        esc(g.name) +
        '</strong></td><td><span class="badge">' +
        esc(semesters[g.semesterEnum]) +
        '</span></td><td>' +
        esc(g.studentsCount) +
        '</td><td>' +
        esc(forms[g.formOfEducation]) +
        '</td><td>#' +
        esc(c.id) +
        ' (' +
        esc(c.x) +
        ', ' +
        esc(c.y) +
        ')</td><td>' +
        esc(date(g.creationDate)) +
        '</td><td>' +
        esc(g.expelledStudents) +
        '</td><td>' +
        esc(g.transferredStudents) +
        '</td><td>' +
        esc(g.shouldBeExpelled) +
        '</td><td>' +
        esc(g.averageMark) +
        '</td><td>' +
        esc(a?.name) +
        '</td><td>' +
        esc(a?.id) +
        '</td><td>' +
        esc(colors[a?.eyeColor]) +
        '</td><td>' +
        esc(colors[a?.hairColor]) +
        '</td><td>' +
        esc(date(a?.birthday)) +
        '</td><td>' +
        esc(countries[a?.nationality]) +
        '</td><td>' +
        (a
          ? '#' +
            esc(a.location.id) +
            ' (' +
            [a.location.x, a.location.y, a.location.z].map(esc).join(', ') +
            ')'
          : '—') +
        '</td><td class="action-column">' +
        actions('groups', g) +
        '</td></tr>'
      );
    })
    .join('');
  $('#group-empty').hidden = page.items.length > 0;
  $('#groups-table').hidden = page.items.length === 0;
  if (!page.total)
    $('#group-empty p').textContent = state.filter
      ? 'Нет групп с таким точным значением. Измените или сбросьте фильтр.'
      : 'Создайте координаты, затем добавьте первую группу. Старосту можно назначить позже.';
  const totalPages = Math.max(1, Math.ceil(page.total / state.size));
  $('#page-info').textContent = page.total
    ? `${state.page * state.size + 1}–${state.page * state.size + page.items.length} из ${page.total} групп`
    : '0 групп';
  $('#page-number').textContent = `${state.page + 1} / ${totalPages}`;
  $('#prev-page').disabled = state.page === 0;
  $('#next-page').disabled = state.page + 1 >= totalPages;
}
function filterInput() {
  const field = $('#filter-column').value;
  const dictionary = {
    formOfEducation: forms,
    semesterEnum: semesters,
    adminEyeColor: colors,
    adminHairColor: colors,
    adminNationality: countries,
  }[field];
  const input = document.createElement(dictionary ? 'select' : 'input');
  input.id = 'filter-value';
  input.setAttribute('aria-label', 'Значение фильтра');
  input.disabled = !field;
  if (dictionary)
    input.innerHTML = Object.entries(dictionary)
      .map(([value, label]) => `<option value="${esc(value)}">${esc(label)}</option>`)
      .join('');
  else input.placeholder = 'Точное значение';
  $('#filter-value').replaceWith(input);
}
function renderReferences() {
  const kind = state.view,
    items = state.refs[kind];
  const columns =
    kind === 'people'
      ? ['id', 'name', 'eyeColor', 'hairColor', 'birthday', 'nationality', 'location']
      : kind === 'coordinates'
        ? ['id', 'x', 'y']
        : ['id', 'x', 'y', 'z'];
  $('#references-table thead').innerHTML =
    '<tr>' +
    columns.map((c) => '<th>' + esc(labels[c]) + '</th>').join('') +
    '<th>Действия</th></tr>';
  $('#references-table tbody').innerHTML = items
    .map(
      (item) =>
        '<tr>' +
        columns
          .map((c) => {
            let value = item[c];
            if (c === 'birthday') value = date(value);
            if (c === 'eyeColor' || c === 'hairColor') value = colors[value];
            if (c === 'nationality') value = countries[value];
            if (c === 'location')
              value = '#' + value.id + ' (' + [value.x, value.y, value.z].join(', ') + ')';
            return '<td>' + esc(value) + '</td>';
          })
          .join('') +
        '<td>' +
        actions(kind, item) +
        '</td></tr>',
    )
    .join('');
  $('#reference-empty').hidden = items.length > 0;
  $('#references-table').hidden = items.length === 0;
}
async function fetchItem(kind, id) {
  return api(kind === 'groups' ? '/groups/' + id : '/references/' + kind + '/' + id);
}
function openGroup(item = null) {
  state.groupEdit = item;
  const form = $('#group-form');
  form.reset();
  clearErrors(form);
  $('#group-stale').hidden = true;
  $('#group-dialog-title').textContent = item ? 'Группа ' + item.name : 'Новая группа';
  refreshSelectors();
  if (item) {
    for (const field of [
      'name',
      'studentsCount',
      'expelledStudents',
      'transferredStudents',
      'shouldBeExpelled',
      'averageMark',
      'formOfEducation',
      'semesterEnum',
    ])
      form.elements[field].value = item[field];
    form.elements.coordinatesId.value = item.coordinates.id;
    form.elements.groupAdminId.value = item.groupAdmin?.id || '';
  } else {
    ['expelledStudents', 'transferredStudents', 'shouldBeExpelled'].forEach(
      (k) => (form.elements[k].value = 1),
    );
  }
  $('#group-dialog').showModal();
}
function openReference(kind, item = null, quick = false) {
  state.refEdit = { kind, item, quick };
  const form = $('#reference-form');
  form.reset();
  clearErrors(form);
  $('#reference-dialog-title').textContent = (item ? 'Изменить ' : 'Добавить ') + singular[kind];
  const integerField = (name) =>
    '<label>' +
    labels[name] +
    '<input name="' +
    name +
    '" inputmode="numeric" pattern="-?[0-9]+" required></label>';
  const floatField = (name) =>
    '<label>' +
    labels[name] +
    '<input name="' +
    name +
    '" type="number" step="any" required></label>';
  if (kind === 'coordinates')
    $('#reference-fields').innerHTML = integerField('x') + floatField('y');
  else if (kind === 'locations')
    $('#reference-fields').innerHTML = floatField('x') + floatField('y') + integerField('z');
  else
    $('#reference-fields').innerHTML =
      '<label class="span-two">Имя<input name="name" required></label><label>Цвет глаз<select name="eyeColor">' +
      enumOptions(state.enums.colors, colors, true) +
      '</select></label><label>Цвет волос<select name="hairColor">' +
      enumOptions(state.enums.colors, colors, true) +
      '</select></label><label>Локация<select name="locationId" required>' +
      referenceOptions('locations') +
      '</select></label><label>Дата рождения<input name="birthday" type="datetime-local" step="1" required><small>Время вводится в часовом поясе этого устройства</small></label><label>Гражданство<select name="nationality" required>' +
      enumOptions(state.enums.countries, countries) +
      '</select></label>';
  if (item)
    for (const input of [...form.elements]) {
      if (input.name && item[input.name] != null) input.value = item[input.name];
    }
  if (item && kind === 'people') {
    form.elements.locationId.value = item.location.id;
    const d = new Date(item.birthday);
    const offset = d.getTimezoneOffset() * 60000;
    form.elements.birthday.value = new Date(d.getTime() - offset).toISOString().slice(0, 19);
  }
  $('#reference-dialog').showModal();
}
function groupInput(form) {
  const f = Object.fromEntries(new FormData(form));
  f.coordinatesId = integer(f.coordinatesId, 'Координаты', 64, true);
  f.groupAdminId = f.groupAdminId ? integer(f.groupAdminId, 'Староста', 64, true) : null;
  f.studentsCount = integer(f.studentsCount, 'Число студентов', 64, true);
  for (const field of ['expelledStudents', 'transferredStudents', 'shouldBeExpelled'])
    f[field] = integer(f[field], labels[field], 32, true);
  f.averageMark = number(f.averageMark, 'Средний балл', true);
  f.version = state.groupEdit?.version;
  return f;
}
function referenceInput(form) {
  const f = Object.fromEntries(new FormData(form)),
    kind = state.refEdit.kind;
  f.version = state.refEdit.item?.version;
  if (kind === 'coordinates') {
    f.x = integer(f.x, 'X');
    f.y = number(f.y, 'Y');
  } else if (kind === 'locations') {
    f.x = number(f.x, 'X');
    f.y = number(f.y, 'Y');
    if (!Number.isFinite(Math.fround(f.y))) throw new Error('Y превышает диапазон float');
    f.z = integer(f.z, 'Z');
  } else {
    f.eyeColor = f.eyeColor || null;
    f.hairColor = f.hairColor || null;
    f.locationId = integer(f.locationId, 'Локация', 64, true);
    f.birthday = new Date(f.birthday).toISOString();
  }
  return f;
}
function renderDetails(kind, item) {
  state.details = { kind, item };
  function block(object) {
    let html = '<dl class="details-grid">',
      children = '';
    for (const [key, value] of Object.entries(object)) {
      if (value && typeof value === 'object') {
        children +=
          '<h3 class="details-section">' + esc(labels[key] || key) + '</h3>' + block(value);
      } else {
        const display =
          key === 'birthday' || key === 'creationDate'
            ? date(value)
            : forms[value] || semesters[value] || countries[value] || colors[value] || value;
        html += '<div><dt>' + esc(labels[key] || key) + '</dt><dd>' + esc(display) + '</dd></div>';
      }
    }
    return html + '</dl>' + children;
  }
  $('#details-content').innerHTML = block(item);
}
async function details(kind, id) {
  renderDetails(kind, await fetchItem(kind, id));
  if (!$('#details-dialog').open) $('#details-dialog').showModal();
}
function confirmAction(title, text, action, input = false) {
  $('#confirm-title').textContent = title;
  $('#confirm-text').textContent = text;
  $('#confirm-input-wrap').hidden = !input;
  $('#confirm-input').required = input;
  $('#confirm-input').value = '';
  clearErrors($('#confirm-form'));
  state.confirm = action;
  $('#confirm-dialog').showModal();
}

on('#auth-toggle', 'click', () => {
  registerMode = !registerMode;
  $('#auth-title').textContent = registerMode ? 'Регистрация' : 'Вход';
  $('#auth-subtitle').textContent = registerMode
    ? 'Зарегистрируйтесь, чтобы работать с учебными группами.'
    : 'Войдите, чтобы открыть учебные группы.';
  $('#auth-submit').textContent = registerMode ? 'Зарегистрироваться' : 'Войти';
  $('#auth-toggle').textContent = registerMode
    ? 'Уже есть аккаунт? Войти'
    : 'Нет аккаунта? Зарегистрироваться';
  $('#auth-form').elements.password.minLength = registerMode ? 8 : 1;
  $('#auth-form').elements.password.autocomplete = registerMode
    ? 'new-password'
    : 'current-password';
  $('#auth-error').hidden = true;
});
on('#auth-form', 'submit', async (e) => {
  e.preventDefault();
  $('#auth-error').hidden = true;
  $('#auth-submit').disabled = true;
  try {
    state.session = await api('/session');
    const data = Object.fromEntries(new FormData(e.target));
    data.username = data.username.toLowerCase();
    if (registerMode) await api('/register', { method: 'POST', body: data });
    await api('/login', { method: 'POST', body: new URLSearchParams(data) });
    e.target.elements.password.value = '';
    await enterApp();
  } catch (error) {
    $('#auth-error').textContent =
      error.message +
      (Object.values(error.fields || {}).length
        ? ': ' + Object.values(error.fields).join('; ')
        : '');
    $('#auth-error').hidden = false;
  } finally {
    $('#auth-submit').disabled = false;
  }
});
on('#logout', 'click', async () => {
  await api('/logout', { method: 'POST' });
  state.session = await api('/session');
  showAuth();
});
on('#create', 'click', () => (state.view === 'groups' ? openGroup() : openReference(state.view)));
on('#filter-form', 'submit', async (e) => {
  e.preventDefault();
  state.filter = $('#filter-column').value;
  state.value = state.filter ? $('#filter-value').value : null;
  state.page = 0;
  await loadGroups();
});
on('#filter-column', 'change', filterInput);
on('#reset-filter', 'click', async () => {
  $('#filter-form').reset();
  filterInput();
  state.filter = '';
  state.value = null;
  state.page = 0;
  await loadGroups();
});
on('#page-size', 'change', async (e) => {
  state.size = Number(e.target.value);
  state.page = 0;
  await loadGroups();
});
on('#prev-page', 'click', async () => {
  state.page = Math.max(0, state.page - 1);
  await loadGroups();
});
on('#next-page', 'click', async () => {
  state.page++;
  await loadGroups();
});
on('#group-form', 'submit', (e) => {
  e.preventDefault();
  return submit(e.target, async () => {
    const item = state.groupEdit;
    await api('/groups' + (item ? '/' + item.id : ''), {
      method: item ? 'PUT' : 'POST',
      body: groupInput(e.target),
    });
    $('#group-dialog').close();
    await refreshCurrent();
    notify(item ? 'Группа обновлена' : 'Группа создана');
  });
});
on('#reference-form', 'submit', (e) => {
  e.preventDefault();
  return submit(e.target, async () => {
    const { kind, item, quick } = state.refEdit;
    const saved = await api('/references/' + kind + (item ? '/' + item.id : ''), {
      method: item ? 'PUT' : 'POST',
      body: referenceInput(e.target),
    });
    $('#reference-dialog').close();
    await refreshCurrent();
    if (quick) {
      $('#group-form').elements[kind === 'coordinates' ? 'coordinatesId' : 'groupAdminId'].value =
        saved.id;
    }
    notify('Объект сохранён');
  });
});
on('#quick-coordinates', 'click', () => openReference('coordinates', null, true));
on('#quick-person', 'click', () => {
  if (!state.refs.locations.length) {
    notify('Сначала создайте локацию в разделе «Локации»');
    return;
  }
  openReference('people', null, true);
});
on('#details-edit', 'click', () => {
  const { kind, item } = state.details;
  $('#details-dialog').close();
  kind === 'groups' ? openGroup(item) : openReference(kind, item);
});
on('#confirm-form', 'submit', (e) => {
  e.preventDefault();
  return submit(e.target, async () => {
    await state.confirm($('#confirm-input').value);
    $('#confirm-dialog').close();
  });
});
on('#find-id', 'click', () =>
  confirmAction(
    'Найти группу по ID',
    'Введите идентификатор группы.',
    async (value) => {
      await details('groups', integer(value, 'ID', 64, true));
    },
    true,
  ),
);
on('#sum-operation', 'click', async () => {
  $('#sum-result').textContent = (await api('/groups/operations/sum')).sum + ' студентов';
});
on('#max-operation', 'click', async () => {
  const { group } = await api('/groups/operations/max-semester');
  $('#max-result').innerHTML = group
    ? '<button class="text-button" data-action="details" data-kind="groups" data-id="' +
      esc(group.id) +
      '">' +
      esc(group.name) +
      ' · ' +
      esc(semesters[group.semesterEnum]) +
      '</button>'
    : 'Групп пока нет';
});
on('#count-operation', 'submit', async (e) => {
  e.preventDefault();
  $('#count-result').textContent =
    (await api('/groups/operations/count-after?semester=' + e.target.elements.semester.value))
      .count + ' групп';
});
on('#transfer-operation', 'submit', (e) => {
  e.preventDefault();
  const f = Object.fromEntries(new FormData(e.target));
  const body = {
    sourceId: integer(f.sourceId, 'Исходная группа', 64, true),
    targetId: integer(f.targetId, 'Целевая группа', 64, true),
  };
  confirmAction(
    'Перевести всех студентов?',
    `Все студенты группы #${body.sourceId} перейдут в #${body.targetId}. Группа #${body.sourceId} будет удалена.`,
    async () => {
      const result = await api('/groups/operations/transfer', { method: 'POST', body });
      $('#transfer-result').textContent =
        `Переведено: ${result.transferred}. Исходная группа удалена.`;
      await refreshCurrent();
      notify('Студенты переведены');
    },
  );
});
on('#add-operation', 'submit', async (e) => {
  e.preventDefault();
  const id = integer(e.target.elements.id.value, 'Группа', 64, true);
  const group = await api('/groups/' + id + '/students', { method: 'POST' });
  $('#add-result').textContent = group.name + ': ' + group.studentsCount + ' студентов';
  await refreshCurrent();
});
document.addEventListener('click', async (e) => {
  const close = e.target.closest('[data-close]');
  if (close) {
    $('#' + close.dataset.close).close();
    return;
  }
  const go = e.target.closest('[data-go],[data-view]');
  if (go) {
    try {
      await navigate(go.dataset.go || go.dataset.view);
    } catch (error) {
      notify(error.message);
    }
    return;
  }
  const sort = e.target.closest('[data-sort]');
  if (sort) {
    state.direction =
      state.sort === sort.dataset.sort && state.direction === 'asc' ? 'desc' : 'asc';
    state.sort = sort.dataset.sort;
    try {
      await loadGroups();
    } catch (error) {
      notify(error.message);
    }
    return;
  }
  const button = e.target.closest('[data-action]');
  if (!button) return;
  const { kind, id, action } = button.dataset;
  try {
    if (action === 'details') await details(kind, id);
    if (action === 'edit') {
      const item = await fetchItem(kind, id);
      kind === 'groups' ? openGroup(item) : openReference(kind, item);
    }
    if (action === 'delete') {
      const item = await fetchItem(kind, id);
      confirmAction(
        'Удалить объект?',
        `Объект #${id}${item.name ? ' «' + item.name + '»' : ''} будет удалён. Если на него ссылаются другие объекты, операция будет отменена.`,
        async () => {
          await api(
            (kind === 'groups' ? '/groups/' : '/references/' + kind + '/') +
              id +
              '?version=' +
              item.version,
            { method: 'DELETE' },
          );
          await refreshCurrent();
          notify('Объект удалён');
        },
      );
    }
  } catch (error) {
    notify(error.message);
  }
});
filterInput();
enterApp().catch((error) => {
  $('#loading').hidden = true;
  showAuth();
  $('#auth-error').textContent = 'Не удалось подключиться к серверу. ' + error.message;
  $('#auth-error').hidden = false;
});
