(() => {
  const TOKEN_KEY = "ledger.token";

  const el = (id) => document.getElementById(id);

  const PERMISSIONS = [
    ["TASKS_READ", "View tasks"],
    ["TASKS_WRITE", "Manage tasks"],
    ["BUDGET_READ", "View budget"],
    ["BUDGET_WRITE", "Manage budget"],
    ["WIKI_READ", "View wiki"],
    ["WIKI_WRITE", "Manage wiki"],
    ["FILES_READ", "View files"],
    ["FILES_WRITE", "Upload files"],
    ["MAIL_READ", "View mail"],
    ["SEARCH_READ", "Use search"],
    ["REPOS_READ", "View repos"],
    ["USERS_MANAGE", "Manage users"],
    ["ROLES_MANAGE", "Manage roles"],
  ];

  const state = {
    token: localStorage.getItem(TOKEN_KEY),
    user: null,
    users: new Map(),
    roles: [],
    tasks: [],
  };

  function decodeToken(token) {
    try {
      const payload = JSON.parse(atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
      return { userId: payload.uid, username: payload.sub, role: payload.role, permissions: payload.permissions || [] };
    } catch (e) {
      return null;
    }
  }

  function hasPermission(name) {
    return !!(state.user && state.user.permissions && state.user.permissions.includes(name));
  }

  function showToast(message, isError) {
    const toast = el("toast");
    toast.textContent = message;
    toast.classList.toggle("error", !!isError);
    toast.hidden = false;
    clearTimeout(showToast._t);
    showToast._t = setTimeout(() => (toast.hidden = true), 3500);
  }

  function escapeHtml(value) {
    return String(value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  async function api(path, options = {}) {
    const headers = Object.assign({}, options.headers);
    if (options.body) {
      headers["Content-Type"] = "application/json";
    }
    if (state.token) {
      headers["Authorization"] = "Bearer " + state.token;
    }
    const res = await fetch(path, Object.assign({}, options, { headers }));
    if (res.status === 204) {
      return null;
    }
    const isJson = (res.headers.get("content-type") || "").includes("application/json");
    const data = isJson ? await res.json().catch(() => null) : await res.text().catch(() => null);
    if (!res.ok) {
      const message = (data && data.error) || (typeof data === "string" && data) || `Request failed (${res.status})`;
      throw new Error(message);
    }
    return data;
  }

  async function loadBranding() {
    try {
      const data = await api("/branding");
      if (data && data.logoUrl) {
        const img = el("brand-logo");
        img.src = data.logoUrl;
        img.hidden = false;
        el("brand-mark-fallback").hidden = true;
      }
    } catch (e) {
      // fall back to the built-in mark
    }
  }

  function checkHttps() {
    if (window.location.protocol === "http:") {
      el("https-warning").hidden = false;
    }
  }

  function setSignedIn(token) {
    state.token = token;
    localStorage.setItem(TOKEN_KEY, token);
    state.user = decodeToken(token);
    el("login-screen").hidden = true;
    el("app-shell").hidden = false;
    el("whoami-chip").textContent = state.user ? `${state.user.username} (${state.user.role})` : "unknown";
    el("stat-user").textContent = state.user ? state.user.username : "—";
    el("stat-role").textContent = state.user ? state.user.role : "—";
    el("users-nav-link").hidden = !hasPermission("USERS_MANAGE");
    el("roles-nav-link").hidden = !hasPermission("ROLES_MANAGE");
    switchView(window.location.hash.startsWith("#/wiki") ? "wiki" : "overview");
    checkAdmin();
    refreshHealth();
    loadRoles();
    loadUsers().then(() => {
      loadTasks();
    });
    loadBudget();
  }

  function setSignedOut() {
    state.token = null;
    state.user = null;
    localStorage.removeItem(TOKEN_KEY);
    el("app-shell").hidden = true;
    el("login-screen").hidden = false;
    el("login-password").value = "";
    el("recovery-login-code").value = "";
    showRecoveryLoginForm(false);
  }

  el("login-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const errorEl = el("login-error");
    errorEl.hidden = true;
    try {
      const data = await api("/auth/login", {
        method: "POST",
        body: JSON.stringify({
          username: el("login-username").value.trim(),
          password: el("login-password").value,
        }),
      });
      setSignedIn(data.token);
    } catch (err) {
      errorEl.textContent = err.message;
      errorEl.hidden = false;
    }
  });

  function showRecoveryLoginForm(show) {
    el("login-form").hidden = show;
    el("show-recovery-login-row").hidden = show;
    el("recovery-login-form").hidden = !show;
    el("show-password-login-row").hidden = !show;
  }

  el("show-recovery-login").addEventListener("click", (e) => {
    e.preventDefault();
    showRecoveryLoginForm(true);
  });

  el("show-password-login").addEventListener("click", (e) => {
    e.preventDefault();
    showRecoveryLoginForm(false);
  });

  el("recovery-login-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const errorEl = el("recovery-login-error");
    errorEl.hidden = true;
    try {
      const data = await api("/auth/recovery-login", {
        method: "POST",
        body: JSON.stringify({
          username: el("recovery-login-username").value.trim(),
          code: el("recovery-login-code").value.trim(),
        }),
      });
      setSignedIn(data.token);
      showToast("Signed in with a recovery code — that code is now used up. Generate new ones from Account.");
    } catch (err) {
      errorEl.textContent = err.message;
      errorEl.hidden = false;
    }
  });

  el("logout-btn").addEventListener("click", async () => {
    try {
      await api("/auth/logout", { method: "POST" });
    } catch (e) {
      // ignore, log out locally regardless
    }
    setSignedOut();
  });

  function switchView(view) {
    document.querySelectorAll(".nav-link").forEach((link) => {
      link.classList.toggle("active", link.dataset.view === view);
    });
    document.querySelectorAll(".view").forEach((section) => {
      section.hidden = section.id !== "view-" + view;
    });
    if (view === "wiki") {
      if (!window.location.hash.startsWith("#/wiki")) {
        window.location.hash = "#/wiki/pages";
      } else {
        handleWikiRoute();
      }
    }
    if (view === "users") {
      loadUserList();
    }
    if (view === "roles") {
      loadRoleList();
    }
    if (view === "account") {
      loadRecoveryCodeStatus();
    }
  }

  document.querySelectorAll(".nav-link").forEach((link) => {
    link.addEventListener("click", (e) => {
      e.preventDefault();
      switchView(link.dataset.view);
    });
  });

  window.addEventListener("hashchange", () => {
    if (window.location.hash.startsWith("#/wiki")) {
      switchView("wiki");
    }
  });

  async function refreshHealth() {
    const dot = el("health-dot");
    try {
      await api("/health");
      el("stat-health").textContent = "OK";
      el("header-health").textContent = "healthy";
      dot.className = "dot up";
    } catch (e) {
      el("stat-health").textContent = "down";
      el("header-health").textContent = "unreachable";
      dot.className = "dot down";
    }
  }

  async function checkAdmin() {
    try {
      const data = await api("/admin/check");
      el("stat-admin").textContent = data.canManageUsers ? "yes" : "no";
    } catch (e) {
      el("stat-admin").textContent = "—";
    }
  }

  async function loadRoles() {
    try {
      state.roles = await api("/roles");
    } catch (e) {
      state.roles = [];
    }
    populateRoleSelect(el("user-role"));
    return state.roles;
  }

  function populateRoleSelect(select, selectedRoleId) {
    if (!select) return;
    select.innerHTML = state.roles
      .map((r) => `<option value="${r.id}" ${r.id === selectedRoleId ? "selected" : ""}>${escapeHtml(r.name)}</option>`)
      .join("");
  }

  function permissionCheckboxesHtml(idPrefix, selectedPermissions) {
    const selected = new Set(selectedPermissions || []);
    return PERMISSIONS.map(
      ([value, label]) => `
        <label>
          <input type="checkbox" name="${idPrefix}-permission" value="${value}" ${selected.has(value) ? "checked" : ""}>
          ${escapeHtml(label)}
        </label>`
    ).join("");
  }

  function readCheckedPermissions(container) {
    return Array.from(container.querySelectorAll("input[type=checkbox]:checked")).map((el) => el.value);
  }

  async function loadRoleList() {
    const list = el("role-list");
    let roles;
    try {
      roles = await loadRoles();
    } catch (e) {
      list.innerHTML = `<p class="empty-state">Couldn't load roles: ${escapeHtml(e.message)}</p>`;
      return;
    }
    list.innerHTML = "";
    if (!roles.length) {
      list.innerHTML = `<p class="empty-state">No roles yet.</p>`;
      return;
    }
    roles.forEach((role) => list.appendChild(renderRoleCard(role)));
  }

  function renderRoleCard(role) {
    const card = document.createElement("div");
    card.className = "ticket-card";

    card.innerHTML = `
      <div class="ticket-card-top">
        <strong>${escapeHtml(role.name)}</strong>
        ${role.builtIn ? `<span class="badge badge-purple">Built in</span>` : ""}
      </div>
      <div class="chip-row">
        <span class="muted">Permissions:</span>
        <span>${role.permissions.length ? role.permissions.map(escapeHtml).join(", ") : "None"}</span>
      </div>
      <div class="ticket-actions">
        <button type="button" class="pill small edit-btn">Edit</button>
        <button type="button" class="pill small danger delete-btn" ${role.builtIn ? "disabled" : ""}>Delete</button>
      </div>
      <form class="ticket-form edit-form" hidden>
        <div class="field-row">
          <label>Name</label>
          <input type="text" class="edit-name" value="${escapeHtml(role.name)}" required>
        </div>
        <div class="field-row">
          <label>Permissions</label>
          <div class="checkbox-grid edit-permissions">${permissionCheckboxesHtml("edit-" + role.id, role.permissions)}</div>
        </div>
        <div class="form-actions">
          <button type="submit" class="pill small primary">Save</button>
          <button type="button" class="pill small cancel-edit-btn">Cancel</button>
        </div>
      </form>
    `;

    const editForm = card.querySelector(".edit-form");

    card.querySelector(".edit-btn").addEventListener("click", () => {
      editForm.hidden = !editForm.hidden;
    });
    card.querySelector(".cancel-edit-btn").addEventListener("click", () => {
      editForm.hidden = true;
    });

    editForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      try {
        await api(`/roles/${role.id}`, {
          method: "PATCH",
          body: JSON.stringify({
            name: card.querySelector(".edit-name").value.trim(),
            permissions: readCheckedPermissions(card.querySelector(".edit-permissions")),
          }),
        });
        showToast("Role updated.");
        loadRoleList();
      } catch (err) {
        showToast(err.message, true);
      }
    });

    card.querySelector(".delete-btn").addEventListener("click", async () => {
      if (role.builtIn) return;
      if (!confirm(`Delete role "${role.name}"? This cannot be undone.`)) {
        return;
      }
      try {
        await api(`/roles/${role.id}`, { method: "DELETE" });
        showToast("Role deleted.");
        loadRoleList();
      } catch (err) {
        showToast(err.message, true);
      }
    });

    return card;
  }

  el("role-permissions").innerHTML = permissionCheckboxesHtml("new-role", []);

  el("role-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const errorEl = el("role-error");
    errorEl.hidden = true;
    try {
      await api("/roles", {
        method: "POST",
        body: JSON.stringify({
          name: el("role-name").value.trim(),
          permissions: readCheckedPermissions(el("role-permissions")),
        }),
      });
      el("role-form").reset();
      el("role-permissions").innerHTML = permissionCheckboxesHtml("new-role", []);
      showToast("Role created.");
      loadRoleList();
    } catch (err) {
      errorEl.textContent = err.message;
      errorEl.hidden = false;
    }
  });

  async function loadUsers(query) {
    try {
      const url = query ? "/users?query=" + encodeURIComponent(query) : "/users";
      const users = await api(url);
      if (!query) {
        state.users = new Map(users.map((u) => [u.id, u]));
        refreshUsernameDatalist();
      }
      return users;
    } catch (e) {
      return [];
    }
  }

  function refreshUsernameDatalist() {
    let datalist = el("all-usernames");
    if (!datalist) {
      datalist = document.createElement("datalist");
      datalist.id = "all-usernames";
      document.body.appendChild(datalist);
    }
    datalist.innerHTML = "";
    state.users.forEach((u) => {
      const option = document.createElement("option");
      option.value = u.username;
      datalist.appendChild(option);
    });
  }

  function userLabel(userId) {
    const u = state.users.get(userId);
    return u ? `${u.firstName} ${u.lastName} (${u.username})` : userId;
  }

  function findUserByUsername(username) {
    for (const u of state.users.values()) {
      if (u.username.toLowerCase() === username.toLowerCase()) {
        return u;
      }
    }
    return null;
  }

  const STATUS_LABELS = {
    NOTSTARTED: "Not Started",
    INPROGRESS: "In Progress",
    COMPLETED: "Completed",
    BLOCKED: "Blocked",
  };

  const STATUS_BADGE = {
    NOTSTARTED: "badge-yellow",
    INPROGRESS: "badge-blue",
    COMPLETED: "badge-green",
    BLOCKED: "badge-red",
  };

  const SELECTABLE_STATUSES = ["NOTSTARTED", "INPROGRESS", "COMPLETED"];

  function formatDate(isoDate) {
    if (!isoDate) return null;
    const [y, m, d] = isoDate.split("-");
    return `${d}-${m}-${y}`;
  }

  async function loadTasks() {
    const list = el("task-list");
    let tasks;
    try {
      tasks = await api("/tasks");
    } catch (e) {
      list.innerHTML = `<p class="empty-state">Couldn't load tasks: ${escapeHtml(e.message)}</p>`;
      return;
    }
    state.tasks = tasks;
    list.innerHTML = "";
    if (!tasks.length) {
      list.innerHTML = `<p class="empty-state">No tasks yet — add one above.</p>`;
      return;
    }
    tasks.forEach((task) => list.appendChild(renderTask(task)));
  }

  function taskTitleById(taskId) {
    const task = state.tasks.find((t) => t.id === taskId);
    return task ? task.title : taskId;
  }

  function renderTask(task) {
    const card = document.createElement("div");
    card.className = "ticket-card";

    const statusValues = SELECTABLE_STATUSES.includes(task.status)
      ? SELECTABLE_STATUSES
      : [task.status, ...SELECTABLE_STATUSES];
    const statusOptions = statusValues
      .map((s) => `<option value="${s}" ${s === task.status ? "selected" : ""}>${STATUS_LABELS[s] || s}</option>`)
      .join("");

    const isSelfAssigned = state.user && task.assigneeIds.includes(state.user.userId);
    const canManageAssignees = hasPermission("TASKS_WRITE");

    const assigneeChips = task.assigneeIds.length
      ? task.assigneeIds
          .map(
            (id) => `<span class="chip" data-user-id="${id}">${escapeHtml(userLabel(id))}${
              canManageAssignees ? `<button type="button" class="unassign-btn" title="Remove">×</button>` : ""
            }</span>`
          )
          .join("")
      : `<span class="muted">No one assigned</span>`;

    const blockerChips = task.blockedByIds.length
      ? task.blockedByIds
          .map(
            (id) =>
              `<span class="chip" data-blocker-id="${id}">${escapeHtml(taskTitleById(id))}<button type="button" class="remove-blocker-btn" title="Remove blocker">×</button></span>`
          )
          .join("")
      : `<span class="muted">No blockers</span>`;

    const otherTasks = state.tasks.filter((t) => t.id !== task.id && !task.blockedByIds.includes(t.id));
    const blockerOptions = otherTasks.map((t) => `<option value="${t.id}">${escapeHtml(t.title)}</option>`).join("");

    card.innerHTML = `
      <div class="ticket-card-top">
        <strong>${escapeHtml(task.title)}</strong>
        <span class="badge ${STATUS_BADGE[task.status] || "badge-blue"}">${STATUS_LABELS[task.status] || task.status}</span>
      </div>
      ${task.description ? `<div class="ticket-card-body">${escapeHtml(task.description)}</div>` : ""}
      <div class="chip-row">
        <span class="muted">Due:</span>
        <span>${task.dueDate ? escapeHtml(formatDate(task.dueDate)) : "No due date"}</span>
        ${task.blocked ? `<span class="badge badge-red">Blocked by open task(s)</span>` : ""}
      </div>
      <div class="chip-row">
        <span class="muted">Assignees:</span>
        ${assigneeChips}
      </div>
      <div class="chip-row">
        <span class="muted">Blocked by:</span>
        ${blockerChips}
      </div>
      <div class="ticket-actions">
        <select class="status-select">${statusOptions}</select>
        <button type="button" class="pill small claim-btn">${isSelfAssigned ? "Unclaim" : "Claim"}</button>
        <button type="button" class="pill small danger delete-btn">Delete</button>
      </div>
      <div class="blocker-picker">
        <select class="blocker-select">
          <option value="">Add blocker…</option>
          ${blockerOptions}
        </select>
        <button type="button" class="pill small add-blocker-btn">Add blocker</button>
      </div>
      ${
        canManageAssignees
          ? `<div class="assign-row">
               <input type="text" class="assign-input" list="all-usernames" placeholder="Assign by username…">
               <button type="button" class="pill small assign-btn">Assign</button>
             </div>`
          : ""
      }
    `;

    card.querySelector(".status-select").addEventListener("change", async (e) => {
      try {
        await api(`/tasks/${task.id}/status`, {
          method: "PATCH",
          body: JSON.stringify({ status: e.target.value }),
        });
        showToast("Task status updated.");
        loadTasks();
      } catch (err) {
        showToast(err.message, true);
      }
    });

    card.querySelector(".claim-btn").addEventListener("click", async () => {
      if (!state.user) return;
      try {
        if (isSelfAssigned) {
          await api(`/tasks/${task.id}/assignees/${state.user.userId}`, { method: "DELETE" });
          showToast("Unclaimed task.");
        } else {
          await api(`/tasks/${task.id}/assignees/${state.user.userId}`, { method: "POST" });
          showToast("Claimed task.");
        }
        loadTasks();
      } catch (err) {
        showToast(err.message, true);
      }
    });

    card.querySelector(".delete-btn").addEventListener("click", async () => {
      try {
        await api(`/tasks/${task.id}`, { method: "DELETE" });
        showToast("Task deleted.");
        loadTasks();
      } catch (err) {
        showToast(err.message, true);
      }
    });

    card.querySelectorAll(".unassign-btn").forEach((btn) => {
      btn.addEventListener("click", async () => {
        const userId = btn.closest(".chip").dataset.userId;
        try {
          await api(`/tasks/${task.id}/assignees/${userId}`, { method: "DELETE" });
          showToast("Removed assignee.");
          loadTasks();
        } catch (err) {
          showToast(err.message, true);
        }
      });
    });

    card.querySelectorAll(".remove-blocker-btn").forEach((btn) => {
      btn.addEventListener("click", async () => {
        const blockerId = btn.closest(".chip").dataset.blockerId;
        try {
          await api(`/tasks/${task.id}/blockers/${blockerId}`, { method: "DELETE" });
          showToast("Removed blocker.");
          loadTasks();
        } catch (err) {
          showToast(err.message, true);
        }
      });
    });

    card.querySelector(".add-blocker-btn").addEventListener("click", async () => {
      const select = card.querySelector(".blocker-select");
      const blockerId = select.value;
      if (!blockerId) {
        showToast("Choose a task to block on first.", true);
        return;
      }
      try {
        await api(`/tasks/${task.id}/blockers/${blockerId}`, { method: "POST" });
        showToast("Blocker added.");
        loadTasks();
      } catch (err) {
        showToast(err.message, true);
      }
    });

    const assignBtn = card.querySelector(".assign-btn");
    if (assignBtn) {
      assignBtn.addEventListener("click", async () => {
        const input = card.querySelector(".assign-input");
        const username = input.value.trim();
        if (!username) return;
        let match = findUserByUsername(username);
        if (!match) {
          const results = await loadUsers(username);
          match = results.find((u) => u.username.toLowerCase() === username.toLowerCase());
        }
        if (!match) {
          showToast(`No user found with username "${username}".`, true);
          return;
        }
        try {
          await api(`/tasks/${task.id}/assignees/${match.id}`, { method: "POST" });
          showToast(`Assigned ${match.username}.`);
          input.value = "";
          loadTasks();
        } catch (err) {
          showToast(err.message, true);
        }
      });
    }

    return card;
  }

  el("task-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const errorEl = el("task-error");
    errorEl.hidden = true;
    try {
      await api("/tasks", {
        method: "POST",
        body: JSON.stringify({
          title: el("task-title").value.trim(),
          description: el("task-description").value.trim() || null,
          dueDate: el("task-due-date").value || null,
        }),
      });
      el("task-form").reset();
      showToast("Task created.");
      loadTasks();
    } catch (err) {
      errorEl.textContent = err.message;
      errorEl.hidden = false;
    }
  });

  const TYPE_LABELS = { INCOME: "Income", EXPENSE: "Expense" };

  async function loadBudget() {
    const list = el("budget-list");
    let entries;
    try {
      entries = await api("/budget");
    } catch (e) {
      list.innerHTML = `<p class="empty-state">Couldn't load budget entries: ${escapeHtml(e.message)}</p>`;
      return;
    }
    list.innerHTML = "";

    let income = 0;
    let expense = 0;
    let currency = "USD";
    entries.forEach((entry) => {
      currency = entry.currency;
      if (entry.type === "INCOME") {
        income += Number(entry.amount);
      } else {
        expense += Number(entry.amount);
      }
    });
    el("budget-total-income").textContent = `+${income.toFixed(2)} ${currency}`;
    el("budget-total-expense").textContent = `-${expense.toFixed(2)} ${currency}`;
    el("budget-total-net").textContent = `${(income - expense).toFixed(2)} ${currency}`;

    if (!entries.length) {
      list.innerHTML = `<p class="empty-state">No budget entries yet — add one above.</p>`;
      return;
    }
    entries.forEach((entry) => list.appendChild(renderBudgetEntry(entry)));
  }

  function renderBudgetEntry(entry) {
    const card = document.createElement("div");
    card.className = "ticket-card " + (entry.type === "INCOME" ? "income" : "expense");
    const sign = entry.type === "INCOME" ? "+" : "-";
    card.innerHTML = `
      <div class="ticket-card-top">
        <strong>${escapeHtml(entry.name)}</strong>
        <span class="badge ${entry.type === "INCOME" ? "badge-green" : "badge-red"}">${TYPE_LABELS[entry.type] || entry.type}</span>
      </div>
      ${entry.description ? `<div class="ticket-card-body">${escapeHtml(entry.description)}</div>` : ""}
      <div class="chip-row">
        <span class="stat-value" style="font-size:1.1rem;">${sign}${Number(entry.amount).toFixed(2)} ${escapeHtml(entry.currency)}</span>
      </div>
      <div class="ticket-actions">
        <button type="button" class="pill small danger delete-btn">Delete</button>
      </div>
    `;
    card.querySelector(".delete-btn").addEventListener("click", async () => {
      try {
        await api(`/budget/${entry.id}`, { method: "DELETE" });
        showToast("Entry deleted.");
        loadBudget();
      } catch (err) {
        showToast(err.message, true);
      }
    });
    return card;
  }

  el("budget-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const errorEl = el("budget-error");
    errorEl.hidden = true;
    try {
      await api("/budget", {
        method: "POST",
        body: JSON.stringify({
          name: el("budget-name").value.trim(),
          description: el("budget-description").value.trim() || null,
          amount: Number(el("budget-amount").value),
          type: el("budget-type").value,
        }),
      });
      el("budget-form").reset();
      showToast("Budget entry created.");
      loadBudget();
    } catch (err) {
      errorEl.textContent = err.message;
      errorEl.hidden = false;
    }
  });

  let wikiEditingPage = null;

  function showWikiSubview(name) {
    el("wiki-list-page").hidden = name !== "list";
    el("wiki-view-page").hidden = name !== "view";
    el("wiki-editor-page").hidden = name !== "editor";
  }

  function parseWikiHash() {
    const hash = window.location.hash.replace(/^#\/?/, "");
    const parts = hash.split("/").filter(Boolean);
    if (parts[0] !== "wiki") {
      return { mode: "list" };
    }
    const rest = parts.slice(1);
    if (rest.length === 0 || rest[0] === "pages") {
      return { mode: "list" };
    }
    if (rest[0] === "new") {
      return { mode: "editor", path: null };
    }
    if (rest[rest.length - 1] === "edit") {
      return { mode: "editor", path: rest.slice(0, -1).join("/") };
    }
    return { mode: "view", path: rest.join("/") };
  }

  async function handleWikiRoute() {
    const route = parseWikiHash();

    if (route.mode === "list") {
      showWikiSubview("list");
      loadWikiList();
      return;
    }

    if (route.mode === "editor" && !route.path) {
      showWikiSubview("editor");
      populateEditor(null);
      return;
    }

    try {
      const page = await api("/wiki/by-path?path=" + encodeURIComponent(route.path));
      if (route.mode === "editor") {
        showWikiSubview("editor");
        populateEditor(page);
      } else {
        showWikiSubview("view");
        populateView(page);
      }
    } catch (e) {
      showToast(`Wiki page "${route.path}" not found.`, true);
      window.location.hash = "#/wiki/pages";
    }
  }

  function populateView(page) {
    el("wiki-view-badge").textContent = "/" + page.path;
    el("wiki-view-title").textContent = page.title;
    el("wiki-view-content").innerHTML = renderMarkdown(page.content || "");
    el("wiki-view-edit-btn").onclick = () => {
      window.location.hash = "#/wiki/" + page.path + "/edit";
    };
  }

  el("wiki-view-back-btn").addEventListener("click", () => {
    window.location.hash = "#/wiki/pages";
  });

  function populateEditor(page) {
    el("wiki-error").hidden = true;
    wikiEditingPage = page;
    el("wiki-editor-title").textContent = page ? "Edit Article" : "New Article";
    el("wiki-title-input").value = page ? page.title : "";
    el("wiki-path-input").value = page ? page.path : "";
    el("wiki-path-input").disabled = Boolean(page);
    el("wiki-content-input").value = page ? page.content || "" : "";
    el("wiki-delete-btn").hidden = !page;
    updateWikiPreview();
  }

  async function loadWikiList() {
    const list = el("wiki-list");
    let pages;
    try {
      pages = await api("/wiki");
    } catch (e) {
      list.innerHTML = `<p class="empty-state">Couldn't load wiki pages: ${escapeHtml(e.message)}</p>`;
      return;
    }
    list.innerHTML = "";
    if (!pages.length) {
      list.innerHTML = `<p class="empty-state">No wiki pages yet — create one.</p>`;
      return;
    }
    pages.forEach((page) => {
      const card = document.createElement("div");
      card.className = "article-card";
      const snippet = (page.content || "").slice(0, 140);
      card.innerHTML = `
        <span class="badge badge-purple">${escapeHtml(page.path)}</span>
        <h3>${escapeHtml(page.title)}</h3>
        <p>${escapeHtml(snippet)}${(page.content || "").length > 140 ? "…" : ""}</p>
      `;
      card.addEventListener("click", () => {
        window.location.hash = "#/wiki/" + page.path;
      });
      list.appendChild(card);
    });
  }

  el("wiki-new-btn").addEventListener("click", () => {
    window.location.hash = "#/wiki/new";
  });
  el("wiki-back-btn").addEventListener("click", () => {
    window.location.hash = "#/wiki/pages";
  });

  ["wiki-title-input", "wiki-path-input", "wiki-content-input"].forEach((id) => {
    el(id).addEventListener("input", updateWikiPreview);
  });

  function updateWikiPreview() {
    el("wiki-preview-title").textContent = el("wiki-title-input").value.trim() || "Page title";
    const path = el("wiki-path-input").value.trim();
    el("wiki-preview-path").textContent = path ? "/" + path.replace(/^\/+/, "") : "/path";
    el("wiki-preview-content").innerHTML = renderMarkdown(el("wiki-content-input").value);
  }

  function renderMarkdown(source) {
    if (!source || !source.trim()) {
      return `<p class="muted">Nothing to preview yet.</p>`;
    }

    const codeBlocks = [];
    const withPlaceholders = source.replace(/```([\s\S]*?)```/g, (_, code) => {
      codeBlocks.push(code.replace(/^\n/, "").replace(/\n$/, ""));
      return `@@CODEBLOCK${codeBlocks.length - 1}@@`;
    });

    const text = escapeHtml(withPlaceholders);
    const lines = text.split("\n");
    const htmlLines = [];
    let listType = null;

    const closeList = () => {
      if (listType) {
        htmlLines.push(listType === "ul" ? "</ul>" : "</ol>");
        listType = null;
      }
    };

    for (const rawLine of lines) {
      const line = rawLine.trim();

      const placeholderMatch = line.match(/^@@CODEBLOCK(\d+)@@$/);
      if (placeholderMatch) {
        closeList();
        const code = escapeHtml(codeBlocks[Number(placeholderMatch[1])]);
        htmlLines.push(`<pre><code>${code}</code></pre>`);
        continue;
      }

      const headerMatch = line.match(/^(#{1,3})\s+(.*)$/);
      if (headerMatch) {
        closeList();
        const level = headerMatch[1].length;
        htmlLines.push(`<h${level}>${inlineMarkdown(headerMatch[2])}</h${level}>`);
        continue;
      }

      const ulMatch = line.match(/^[-*]\s+(.*)$/);
      if (ulMatch) {
        if (listType !== "ul") {
          closeList();
          htmlLines.push("<ul>");
          listType = "ul";
        }
        htmlLines.push(`<li>${inlineMarkdown(ulMatch[1])}</li>`);
        continue;
      }

      const olMatch = line.match(/^\d+\.\s+(.*)$/);
      if (olMatch) {
        if (listType !== "ol") {
          closeList();
          htmlLines.push("<ol>");
          listType = "ol";
        }
        htmlLines.push(`<li>${inlineMarkdown(olMatch[1])}</li>`);
        continue;
      }

      closeList();

      if (line === "") {
        continue;
      }

      htmlLines.push(`<p>${inlineMarkdown(line)}</p>`);
    }
    closeList();

    return htmlLines.join("\n");
  }

  function isSafeLinkUrl(url) {
    return /^(https?:|mailto:|\/|#)/i.test(url);
  }

  function inlineMarkdown(text) {
    return text
      .replace(/`([^`]+)`/g, "<code>$1</code>")
      .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
      .replace(/\*([^*]+)\*/g, "<em>$1</em>")
      .replace(/\[([^\]]+)\]\(([^)]+)\)/g, (match, label, url) =>
        isSafeLinkUrl(url) ? `<a href="${url}" target="_blank" rel="noreferrer">${label}</a>` : label
      );
  }

  el("wiki-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const errorEl = el("wiki-error");
    errorEl.hidden = true;
    const title = el("wiki-title-input").value.trim();
    const path = el("wiki-path-input").value.trim();
    const content = el("wiki-content-input").value;

    try {
      let savedPath;
      if (wikiEditingPage) {
        const updated = await api(`/wiki/${wikiEditingPage.id}`, {
          method: "PATCH",
          body: JSON.stringify({ title, content }),
        });
        savedPath = updated.path;
        showToast("Wiki page updated.");
      } else {
        const created = await api("/wiki", {
          method: "POST",
          body: JSON.stringify({ path, title, content: content || null }),
        });
        savedPath = created.path;
        showToast("Wiki page created.");
      }
      window.location.hash = "#/wiki/" + savedPath;
    } catch (err) {
      errorEl.textContent = err.message;
      errorEl.hidden = false;
    }
  });

  el("wiki-delete-btn").addEventListener("click", async () => {
    if (!wikiEditingPage) return;
    try {
      await api(`/wiki/${wikiEditingPage.id}`, { method: "DELETE" });
      showToast("Wiki page deleted.");
      window.location.hash = "#/wiki/pages";
    } catch (err) {
      showToast(err.message, true);
    }
  });

  async function loadUserList() {
    const list = el("user-list");
    let users;
    try {
      users = await api("/users");
    } catch (e) {
      list.innerHTML = `<p class="empty-state">Couldn't load users: ${escapeHtml(e.message)}</p>`;
      return;
    }
    list.innerHTML = "";
    if (!users.length) {
      list.innerHTML = `<p class="empty-state">No users yet.</p>`;
      return;
    }
    users.forEach((user) => list.appendChild(renderUserCard(user)));
  }

  function renderUserCard(user) {
    const card = document.createElement("div");
    card.className = "ticket-card";
    const isSelf = state.user && state.user.userId === user.id;

    card.innerHTML = `
      <div class="ticket-card-top">
        <strong>${escapeHtml(user.firstName)} ${escapeHtml(user.lastName)}</strong>
        <span class="badge badge-blue">${escapeHtml(user.roleName)}</span>
      </div>
      <div class="chip-row">
        <span class="muted">Username:</span>
        <span>${escapeHtml(user.username)}</span>
        ${isSelf ? `<span class="badge badge-yellow">You</span>` : ""}
      </div>
      <div class="ticket-actions">
        <button type="button" class="pill small edit-btn">Edit</button>
        <button type="button" class="pill small danger delete-btn" ${isSelf ? "disabled" : ""}>Delete</button>
      </div>
      <form class="ticket-form edit-form" hidden>
        <div class="field-row">
          <label>First name</label>
          <input type="text" class="edit-first-name" value="${escapeHtml(user.firstName)}" required>
        </div>
        <div class="field-row">
          <label>Last name</label>
          <input type="text" class="edit-last-name" value="${escapeHtml(user.lastName)}" required>
        </div>
        <div class="field-row">
          <label>Role</label>
          <select class="edit-role"></select>
        </div>
        <div class="field-row">
          <label>New password</label>
          <input type="password" class="edit-password" placeholder="Leave blank to keep current">
        </div>
        <div class="form-actions">
          <button type="submit" class="pill small primary">Save</button>
          <button type="button" class="pill small cancel-edit-btn">Cancel</button>
        </div>
      </form>
    `;

    populateRoleSelect(card.querySelector(".edit-role"), user.roleId);

    const editForm = card.querySelector(".edit-form");

    card.querySelector(".edit-btn").addEventListener("click", () => {
      editForm.hidden = !editForm.hidden;
    });
    card.querySelector(".cancel-edit-btn").addEventListener("click", () => {
      editForm.hidden = true;
    });

    editForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      const password = card.querySelector(".edit-password").value;
      try {
        await api(`/users/${user.id}`, {
          method: "PATCH",
          body: JSON.stringify({
            firstName: card.querySelector(".edit-first-name").value.trim(),
            lastName: card.querySelector(".edit-last-name").value.trim(),
            roleId: card.querySelector(".edit-role").value,
            password: password || null,
          }),
        });
        showToast("User updated.");
        loadUserList();
        loadUsers();
      } catch (err) {
        showToast(err.message, true);
      }
    });

    card.querySelector(".delete-btn").addEventListener("click", async () => {
      if (isSelf) return;
      if (!confirm(`Delete user "${user.username}"? This cannot be undone.`)) {
        return;
      }
      try {
        await api(`/users/${user.id}`, { method: "DELETE" });
        showToast("User deleted.");
        loadUserList();
        loadUsers();
      } catch (err) {
        showToast(err.message, true);
      }
    });

    return card;
  }

  el("user-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const errorEl = el("user-error");
    errorEl.hidden = true;
    try {
      await api("/users", {
        method: "POST",
        body: JSON.stringify({
          firstName: el("user-first-name").value.trim(),
          lastName: el("user-last-name").value.trim(),
          username: el("user-username").value.trim(),
          password: el("user-password").value,
          roleId: el("user-role").value,
        }),
      });
      el("user-form").reset();
      showToast("User created.");
      loadUserList();
      loadUsers();
    } catch (err) {
      errorEl.textContent = err.message;
      errorEl.hidden = false;
    }
  });

  function formatDateTime(iso) {
    if (!iso) return "—";
    const d = new Date(iso);
    return isNaN(d.getTime()) ? "—" : d.toLocaleString();
  }

  async function loadRecoveryCodeStatus() {
    try {
      const status = await api("/auth/recovery-codes");
      el("recovery-codes-remaining").textContent = `${status.remaining} / ${status.total}`;
      el("recovery-codes-generated-at").textContent = formatDateTime(status.generatedAt);
      el("recovery-codes-warning").hidden = status.total > 0;
    } catch (e) {
      el("recovery-codes-remaining").textContent = "—";
      el("recovery-codes-generated-at").textContent = "—";
    }
  }

  function renderRecoveryCodes(codes) {
    const grid = el("recovery-codes-grid");
    grid.innerHTML = "";
    codes.forEach((code) => {
      const chip = document.createElement("div");
      chip.className = "recovery-code-chip";
      chip.textContent = code;
      grid.appendChild(chip);
    });
    el("recovery-codes-reveal").hidden = false;
  }

  el("recovery-codes-generate-btn").addEventListener("click", async () => {
    if (!confirm("Generating new codes invalidates any existing ones. Continue?")) {
      return;
    }
    try {
      const data = await api("/auth/recovery-codes", { method: "POST" });
      renderRecoveryCodes(data.codes);
      showToast("New recovery codes generated — save them now, they won't be shown again.");
      loadRecoveryCodeStatus();
    } catch (err) {
      showToast(err.message, true);
    }
  });

  el("recovery-codes-revoke-btn").addEventListener("click", async () => {
    if (!confirm("Revoke all of your recovery codes? You won't be able to use them for emergency access anymore.")) {
      return;
    }
    try {
      await api("/auth/recovery-codes", { method: "DELETE" });
      el("recovery-codes-reveal").hidden = true;
      showToast("Recovery codes revoked.");
      loadRecoveryCodeStatus();
    } catch (err) {
      showToast(err.message, true);
    }
  });

  el("recovery-codes-copy-btn").addEventListener("click", async () => {
    const codes = Array.from(el("recovery-codes-grid").children).map((c) => c.textContent);
    try {
      await navigator.clipboard.writeText(codes.join("\n"));
      showToast("Codes copied to clipboard.");
    } catch (e) {
      showToast("Couldn't copy automatically — copy them manually.", true);
    }
  });

  el("recovery-codes-dismiss-btn").addEventListener("click", () => {
    el("recovery-codes-reveal").hidden = true;
    el("recovery-codes-grid").innerHTML = "";
  });

  const SEARCH_BADGE = {
    TASK: "badge-blue",
    WIKI_PAGE: "badge-purple",
    UPLOAD: "badge-orange",
  };

  el("search-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const list = el("search-list");
    const query = el("search-query").value.trim();
    if (!query) return;
    list.innerHTML = `<p class="empty-state">Searching…</p>`;
    try {
      const results = await api("/search?q=" + encodeURIComponent(query));
      list.innerHTML = "";
      if (!results.length) {
        list.innerHTML = `<p class="empty-state">No results for "${escapeHtml(query)}".</p>`;
        return;
      }
      results.forEach((result) => {
        const card = document.createElement("div");
        card.className = "article-card";
        card.innerHTML = `
          <span class="badge ${SEARCH_BADGE[result.type] || "badge-blue"}">${result.type.replace("_", " ")}</span>
          <h3>${escapeHtml(result.title)}</h3>
          ${result.snippet ? `<p>${escapeHtml(result.snippet)}</p>` : ""}
        `;
        list.appendChild(card);
      });
    } catch (err) {
      list.innerHTML = `<p class="empty-state">${escapeHtml(err.message)}</p>`;
    }
  });

  loadBranding();
  checkHttps();

  if (state.token) {
    setSignedIn(state.token);
  } else {
    setSignedOut();
  }
})();
