const AUTH_KEY = "fieldstone-auth";
const STATE_KEY = "fieldstone-oauth-state";
const VERIFIER_KEY = "fieldstone-oauth-verifier";
const unitsElement = document.querySelector("#unit-list");
const authButton = document.querySelector("#auth-button");
const accountLabel = document.querySelector("#account-label");
const reservationSection = document.querySelector("#reservations-section");
const reservationList = document.querySelector("#reservation-list");
const dialog = document.querySelector("#reservation-dialog");
const reservationForm = document.querySelector("#reservation-form");
const toastElement = document.querySelector("#toast");
let config;
let units = [];
let toastTimer;

async function initialize() {
  try {
    const configResponse = await fetch("/config.json", { cache: "no-store" });
    if (!configResponse.ok) throw new Error("App configuration is missing. Deploy the application or upload config.json.");
    config = await configResponse.json();
    if (!config.apiUrl || !config.cognitoDomain || !config.clientId) throw new Error("App configuration is incomplete.");
    await completeSignIn();
    authButton.disabled = false;
    authButton.addEventListener("click", handleAuthClick);
    await loadUnits();
    updateAccountUi();
    if (getAuth()) await loadReservations();
  } catch (error) {
    showMessage(unitsElement, error.message);
    authButton.textContent = "Unavailable";
  }
}

function getAuth() {
  try {
    const auth = JSON.parse(sessionStorage.getItem(AUTH_KEY) || "null");
    if (!auth || auth.expiresAt <= Date.now()) {
      sessionStorage.removeItem(AUTH_KEY);
      return null;
    }
    return auth;
  } catch {
    sessionStorage.removeItem(AUTH_KEY);
    return null;
  }
}

async function completeSignIn() {
  const url = new URL(window.location.href);
  const code = url.searchParams.get("code");
  if (!code) return;
  const state = url.searchParams.get("state");
  const expectedState = sessionStorage.getItem(STATE_KEY);
  const verifier = sessionStorage.getItem(VERIFIER_KEY);
  sessionStorage.removeItem(STATE_KEY);
  sessionStorage.removeItem(VERIFIER_KEY);
  window.history.replaceState({}, document.title, "/");
  if (!state || state !== expectedState || !verifier) throw new Error("Sign-in could not be verified. Please try again.");
  const response = await fetch(`${config.cognitoDomain}/oauth2/token`, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "authorization_code",
      client_id: config.clientId,
      code,
      redirect_uri: window.location.origin + "/",
      code_verifier: verifier
    })
  });
  if (!response.ok) throw new Error("Sign-in failed. Please try again.");
  const tokens = await response.json();
  sessionStorage.setItem(AUTH_KEY, JSON.stringify({
    accessToken: tokens.access_token,
    expiresAt: Date.now() + tokens.expires_in * 1000
  }));
}

async function startSignIn() {
  const verifier = randomString(64);
  const state = randomString(32);
  sessionStorage.setItem(STATE_KEY, state);
  sessionStorage.setItem(VERIFIER_KEY, verifier);
  const challenge = await sha256Base64Url(verifier);
  const authorizeUrl = new URL(`${config.cognitoDomain}/oauth2/authorize`);
  authorizeUrl.search = new URLSearchParams({
    client_id: config.clientId,
    response_type: "code",
    scope: "openid email profile",
    redirect_uri: window.location.origin + "/",
    state,
    code_challenge_method: "S256",
    code_challenge: challenge
  });
  window.location.assign(authorizeUrl);
}

function handleAuthClick() {
  if (getAuth()) {
    sessionStorage.removeItem(AUTH_KEY);
    window.location.assign(`${config.cognitoDomain}/logout?${new URLSearchParams({
      client_id: config.clientId,
      logout_uri: window.location.origin + "/"
    })}`);
  } else {
    startSignIn().catch((error) => showToast(error.message));
  }
}

function updateAccountUi() {
  const signedIn = Boolean(getAuth());
  authButton.textContent = signedIn ? "Sign out" : "Sign in";
  accountLabel.textContent = signedIn ? "Welcome back" : "";
  reservationSection.hidden = !signedIn;
}

async function api(path, options = {}) {
  const auth = getAuth();
  const headers = new Headers(options.headers || {});
  if (auth) headers.set("authorization", `Bearer ${auth.accessToken}`);
  if (options.body) headers.set("content-type", "application/json");
  const response = await fetch(`${config.apiUrl}${path}`, { ...options, headers });
  if (response.status === 204) return null;
  const result = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(result.message || "The request could not be completed.");
  return result;
}

async function loadUnits() {
  units = await api("/units");
  unitsElement.replaceChildren();
  if (!units.length) {
    showMessage(unitsElement, "There are no spaces available yet. Please check back soon.");
    return;
  }
  units.forEach((unit) => unitsElement.append(createUnitCard(unit)));
}

function createUnitCard(unit) {
  const card = document.createElement("article");
  card.className = "unit-card";
  const top = document.createElement("div");
  top.className = "unit-card-top";
  const icon = document.createElement("span");
  icon.className = "unit-icon";
  icon.setAttribute("aria-hidden", "true");
  icon.textContent = "⌂";
  const availability = document.createElement("span");
  availability.className = `availability${unit.available ? "" : " unavailable"}`;
  availability.textContent = unit.available ? "Available" : "Fully booked";
  top.append(icon, availability);

  const id = document.createElement("span");
  id.className = "unit-id";
  id.textContent = `SPACE ${unit.unitId}`;
  const heading = document.createElement("h3");
  heading.textContent = `${unit.size} storage`;
  const price = document.createElement("p");
  price.className = "unit-price";
  const amount = document.createElement("strong");
  amount.textContent = formatPrice(unit.monthlyPrice);
  price.append(amount, document.createTextNode(" / month"));
  const meta = document.createElement("div");
  meta.className = "unit-meta";
  meta.textContent = "Flexible monthly rental · Secure access";
  const button = document.createElement("button");
  button.className = "button button-primary";
  button.type = "button";
  button.textContent = unit.available ? "Reserve this space →" : "Currently unavailable";
  button.disabled = !unit.available;
  button.addEventListener("click", () => openReservation(unit));
  card.append(top, id, heading, price, meta, button);
  return card;
}

function openReservation(unit) {
  if (!getAuth()) {
    showToast("Sign in to reserve a storage space.");
    return;
  }
  document.querySelector("#unit-id").value = unit.unitId;
  document.querySelector("#selected-unit").textContent = `${unit.size} · ${formatPrice(unit.monthlyPrice)} per month`;
  document.querySelector("#start-date").value = "";
  document.querySelector("#end-date").value = "";
  document.querySelector("#form-error").textContent = "";
  dialog.showModal();
}

async function loadReservations() {
  try {
    const reservations = await api("/reservations");
    reservationList.replaceChildren();
    if (!reservations.length) {
      showMessage(reservationList, "Your reservations will appear here.");
      return;
    }
    reservations.forEach((reservation) => reservationList.append(createReservationRow(reservation)));
  } catch (error) {
    showToast(error.message);
  }
}

function createReservationRow(reservation) {
  const row = document.createElement("article");
  row.className = "reservation-row";
  const details = document.createElement("div");
  details.className = "reservation-details";
  const title = document.createElement("strong");
  const unit = units.find((item) => item.unitId === reservation.unitId);
  title.textContent = `${unit?.size || "Storage"} · Space ${reservation.unitId}`;
  const dates = document.createElement("span");
  dates.textContent = `${reservation.startDate} – ${reservation.endDate}`;
  details.append(title, dates);
  const status = document.createElement("span");
  status.className = "reservation-status";
  status.textContent = reservation.status;
  row.append(details, status);
  if (reservation.status !== "CANCELLED") {
    const cancel = document.createElement("button");
    cancel.type = "button";
    cancel.className = "button button-outline";
    cancel.textContent = "Cancel";
    cancel.addEventListener("click", async () => {
      cancel.disabled = true;
      try {
        await api(`/reservations/${encodeURIComponent(reservation.reservationId)}`, { method: "DELETE" });
        showToast("Reservation cancelled.");
        await Promise.all([loadReservations(), loadUnits()]);
      } catch (error) {
        cancel.disabled = false;
        showToast(error.message);
      }
    });
    row.append(cancel);
  }
  return row;
}

reservationForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const submit = reservationForm.querySelector('button[type="submit"]');
  const startDate = document.querySelector("#start-date").value;
  const endDate = document.querySelector("#end-date").value;
  const errorElement = document.querySelector("#form-error");
  if (endDate <= startDate) {
    errorElement.textContent = "Choose an end date after your move-in date.";
    return;
  }
  submit.disabled = true;
  errorElement.textContent = "";
  try {
    await api("/reservations", {
      method: "POST",
      body: JSON.stringify({ unitId: document.querySelector("#unit-id").value, startDate, endDate })
    });
    dialog.close();
    showToast("Your reservation request is in.");
    await Promise.all([loadReservations(), loadUnits()]);
  } catch (error) {
    errorElement.textContent = error.message;
  } finally {
    submit.disabled = false;
  }
});

document.querySelector("#dialog-close").addEventListener("click", () => dialog.close());

function formatPrice(value) {
  return new Intl.NumberFormat(undefined, { style: "currency", currency: "ZAR", maximumFractionDigits: 0 }).format(Number(value));
}

function showMessage(container, message) {
  const paragraph = document.createElement("p");
  paragraph.className = "empty-message";
  paragraph.textContent = message;
  container.replaceChildren(paragraph);
}

function showToast(message) {
  toastElement.textContent = message;
  toastElement.classList.add("visible");
  window.clearTimeout(toastTimer);
  toastTimer = window.setTimeout(() => toastElement.classList.remove("visible"), 3500);
}

function randomString(length) {
  const bytes = crypto.getRandomValues(new Uint8Array(length));
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
}

async function sha256Base64Url(value) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return btoa(String.fromCharCode(...new Uint8Array(digest)))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}

initialize();
