import { createHash, randomUUID } from "node:crypto";
import { createReadStream, existsSync, statSync } from "node:fs";
import { extname, isAbsolute, join, normalize, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { createServer } from "node:http";

const PORT = Number(process.env.PORT ?? 4174);
const DIST = resolve(fileURLToPath(new URL("../dist", import.meta.url)));
const now = () => new Date().toISOString();
const state = {
  sessions: new Map(),
  tokens: new Map(),
  users: new Map(),
  conversations: new Map(),
  generations: new Map(),
};
const demoFarms = [
  {
    id: "20000000-0000-0000-0000-000000000001",
    code: "FARM-DL-01",
    enterpriseId: null,
    name: "Nông trại Đắk Lắk",
    address: "Buôn Ma Thuột",
    province: "Đắk Lắk",
    totalAreaHa: 120.5,
    latitude: 12.6667,
    longitude: 108.05,
    status: "ACTIVE",
    createdAt: "2026-07-19T00:00:00Z",
    updatedAt: "2026-07-19T00:00:00Z",
    version: 0,
  },
  {
    id: "20000000-0000-0000-0000-000000000002",
    code: "FARM-LD-01",
    enterpriseId: null,
    name: "Nông trại Lâm Đồng",
    address: "Bảo Lộc",
    province: "Lâm Đồng",
    totalAreaHa: 80,
    latitude: 11.94,
    longitude: 108.44,
    status: "ACTIVE",
    createdAt: "2026-07-19T00:00:00Z",
    updatedAt: "2026-07-19T00:00:00Z",
    version: 0,
  },
  {
    id: "20000000-0000-0000-0000-000000000003",
    code: "FARM-BT-01",
    enterpriseId: null,
    name: "Nông trại Bình Thuận",
    address: "Hàm Thuận Nam",
    province: "Bình Thuận",
    totalAreaHa: 95,
    latitude: 10.93,
    longitude: 108.1,
    status: "ACTIVE",
    createdAt: "2026-07-19T00:00:00Z",
    updatedAt: "2026-07-19T00:00:00Z",
    version: 0,
  },
];
const demoPlotsByFarm = new Map([
  [
    demoFarms[0].id,
    [
      {
        id: "30000000-0000-0000-0000-000000000001",
        farmId: demoFarms[0].id,
        areaId: null,
        code: "DL-A01",
        name: "Lô cà phê Robusta",
        areaInHectares: 12.25,
        soilType: "BASALT",
        status: "IN_USE",
        latitude: null,
        longitude: null,
        createdAt: "2026-07-19T00:00:00Z",
        updatedAt: "2026-07-19T00:00:00Z",
        version: 0,
      },
      {
        id: "30000000-0000-0000-0000-000000000002",
        farmId: demoFarms[0].id,
        areaId: null,
        code: "DL-A02",
        name: "Lô sầu riêng Ri6",
        areaInHectares: 9.75,
        soilType: "BASALT",
        status: "PREPARING",
        latitude: null,
        longitude: null,
        createdAt: "2026-07-19T00:00:00Z",
        updatedAt: "2026-07-19T00:00:00Z",
        version: 0,
      },
    ],
  ],
  [
    demoFarms[1].id,
    [
      {
        id: "30000000-0000-0000-0000-000000000003",
        farmId: demoFarms[1].id,
        areaId: null,
        code: "LD-B01",
        name: "Nhà kính xà lách",
        areaInHectares: 8,
        soilType: "LOAM",
        status: "IN_USE",
        latitude: null,
        longitude: null,
        createdAt: "2026-07-19T00:00:00Z",
        updatedAt: "2026-07-19T00:00:00Z",
        version: 0,
      },
    ],
  ],
  [
    demoFarms[2].id,
    [
      {
        id: "30000000-0000-0000-0000-000000000004",
        farmId: demoFarms[2].id,
        areaId: null,
        code: "BT-C01",
        name: "Lô thanh long ruột đỏ",
        areaInHectares: 16.5,
        soilType: "SANDY_LOAM",
        status: "IN_USE",
        latitude: null,
        longitude: null,
        createdAt: "2026-07-19T00:00:00Z",
        updatedAt: "2026-07-19T00:00:00Z",
        version: 0,
      },
    ],
  ],
]);

const securityHeaders = {
  "Content-Security-Policy": "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; connect-src 'self'; img-src 'self' data:; font-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'",
  "Cross-Origin-Opener-Policy": "same-origin",
  "Permissions-Policy": "camera=(), geolocation=(), microphone=()",
  "Referrer-Policy": "strict-origin-when-cross-origin",
  "X-Content-Type-Options": "nosniff",
  "X-Frame-Options": "DENY",
};

function writeHeaders(response, extra = {}) {
  response.setHeader("Cache-Control", "no-store");
  for (const [name, value] of Object.entries({ ...securityHeaders, ...extra })) {
    response.setHeader(name, value);
  }
}

function json(response, status, body, extra = {}) {
  writeHeaders(response, { "Content-Type": "application/json; charset=utf-8", ...extra });
  response.writeHead(status);
  response.end(JSON.stringify(body));
}

function apiError(response, status, code, message, path) {
  json(response, status, {
    timestamp: now(), status, error: status === 401 ? "Unauthorized" : "Bad Request",
    code, message, path, violations: [], details: {},
  });
}

function readBody(request) {
  return new Promise((resolveBody, reject) => {
    let body = "";
    request.setEncoding("utf8");
    request.on("data", (chunk) => { body += chunk; });
    request.on("end", () => {
      try { resolveBody(body ? JSON.parse(body) : {}); } catch (error) { reject(error); }
    });
    request.on("error", reject);
  });
}

function cookies(request) {
  return Object.fromEntries((request.headers.cookie ?? "").split(";").flatMap((part) => {
    const separator = part.indexOf("=");
    if (separator < 0) return [];
    return [[part.slice(0, separator).trim(), decodeURIComponent(part.slice(separator + 1).trim())]];
  }));
}

function scenarioForEmail(email) {
  return email.toLowerCase().includes("cancel") ? "cancel" : "reconnect";
}

function userForEmail(email) {
  const key = email.toLowerCase();
  const existing = state.users.get(key);
  if (existing) return existing;
  const user = {
    id: randomUUID(), email: key, fullName: "E2E Operator", status: "ACTIVE",
    roles: ["SYSTEM_ADMIN"],
    permissions: [
      "FARM_READ",
      "CROP_CATALOG_READ",
      "CROP_CYCLE_READ",
      "HARVEST_READ",
      "INVENTORY_READ",
      "INVENTORY_USE",
      "SALES_READ",
      "IOT_READ",
      "IOT_WRITE",
      "IDENTITY_USER_READ",
      "ASSISTANT_USE",
    ],
    lastLoginAt: null, createdAt: now(), scenario: scenarioForEmail(key),
  };
  state.users.set(key, user);
  return user;
}

function sessionUser(request) {
  const authorization = request.headers.authorization;
  if (authorization?.startsWith("Bearer ")) {
    const tokenUser = state.tokens.get(authorization.slice("Bearer ".length));
    if (tokenUser) return tokenUser;
  }
  const refresh = cookies(request).agricore_refresh;
  return refresh ? state.sessions.get(refresh) ?? null : null;
}

function authOrFail(request, response, path) {
  const user = sessionUser(request);
  if (!user) {
    apiError(response, 401, "UNAUTHENTICATED", "Authentication required", path);
    return null;
  }
  return user;
}

function accessToken(user) {
  const token = createHash("sha256").update(`${user.id}:${Date.now()}:${randomUUID()}`).digest("hex");
  state.tokens.set(token, user);
  return token;
}

function authResponse(user) {
  return { accessToken: accessToken(user), tokenType: "Bearer", expiresIn: 300, user };
}

function refreshCookie(value, maxAge = 900) {
  return `agricore_refresh=${encodeURIComponent(value)}; Max-Age=${maxAge}; Path=/api/v1/auth/web; HttpOnly; SameSite=Strict`;
}

function conversationResponse(conversation) {
  return {
    id: conversation.id, title: conversation.title, contextType: conversation.contextType,
    farmId: conversation.farmId, status: conversation.status, roleSnapshot: ["SYSTEM_ADMIN"],
    nextMessageSequence: conversation.nextMessageSequence, version: conversation.version,
    createdAt: conversation.createdAt, updatedAt: conversation.updatedAt,
    archivedAt: conversation.archivedAt, purgeAfter: conversation.purgeAfter,
  };
}

function generationResponse(generation) {
  return {
    id: generation.id, conversationId: generation.conversationId, status: generation.status,
    provider: "mock", model: null, errorCode: null, userMessageId: generation.userMessageId,
    nextEventSequence: generation.events.length, queuedAt: generation.createdAt,
    createdAt: generation.createdAt, updatedAt: generation.updatedAt,
    completedAt: generation.status === "COMPLETED" || generation.status === "CANCELLED" ? generation.updatedAt : null,
    deduplicated: false,
  };
}

function event(generation, sequenceNo, eventType, payload) {
  return {
    id: randomUUID(), generationId: generation.id, sequenceNo, eventType,
    payload: JSON.stringify(payload), createdAt: now(),
  };
}

function appendEvent(generation, eventType, payload) {
  const next = event(generation, generation.events.length, eventType, payload);
  generation.events.push(next);
  generation.updatedAt = next.createdAt;
  if (eventType === "STATUS") generation.status = payload.status;
  if (eventType === "COMPLETED") generation.status = "COMPLETED";
  if (eventType === "CANCELLED") generation.status = "CANCELLED";
  return next;
}

function frame(value) {
  return `id:${value.sequenceNo}\nevent:${value.eventType.toLowerCase()}\ndata:${JSON.stringify(value)}\n\n`;
}

function writeEvents(response, generation, after) {
  for (const value of generation.events) {
    if (value.sequenceNo > after) response.write(frame(value));
  }
}

function page(content, size = 20) {
  return { page: 0, size, totalElements: content.length, totalPages: content.length ? 1 : 0,
    first: true, last: true, content };
}

function messagePage(generation) {
  if (!generation) return page([], 100);
  const messages = [{
    id: generation.userMessageId, conversationId: generation.conversationId,
    generationId: generation.id, sequenceNo: 0, role: "USER", content: generation.prompt,
    tokenCount: generation.prompt.length, createdAt: generation.createdAt,
  }];
  if (generation.status === "COMPLETED") {
    messages.push({
      id: generation.assistantMessageId, conversationId: generation.conversationId,
      generationId: generation.id, sequenceNo: 1, role: "ASSISTANT",
      content: generation.scenario === "reconnect"
        ? "Ưu tiên hôm nay: kiểm tra cảnh báo IoT vượt ngưỡng, đối soát lô sắp thu hoạch với tồn kho bao bì, và xác nhận các phiếu giữ hàng đang mở. [Source: https://example.com/operations]"
        : "Mock response before cancellation.", tokenCount: 12, createdAt: generation.updatedAt,
    });
  }
  return page(messages, 100);
}

function completeGeneration(generation) {
  if (!generation.events.some((value) => value.eventType === "COMPLETED")) {
    generation.assistantMessageId = randomUUID();
    appendEvent(generation, "COMPLETED", {
      status: "COMPLETED", assistantMessageId: generation.assistantMessageId,
      finishReason: "stop", inputTokens: 12, outputTokens: 8,
    });
  }
}

async function streamGeneration(request, response, generation, after) {
  writeHeaders(response, {
    "Cache-Control": "no-cache, no-transform", Connection: "keep-alive",
    "Content-Type": "text/event-stream; charset=utf-8", "X-Accel-Buffering": "no",
  });
  response.writeHead(200);
  generation.streamConnections += 1;
  const connection = generation.streamConnections;
  let closed = false;
  const finish = () => {
    if (!closed) { closed = true; response.end(); }
  };
  request.on("close", () => { closed = true; });

  if (generation.scenario === "cancel") {
    if (!generation.events.some((value) => value.eventType === "STATUS" && value.payload.includes("RUNNING"))) {
      const running = appendEvent(generation, "STATUS", { status: "RUNNING" });
      if (running.sequenceNo > after) response.write(frame(running));
    } else {
      writeEvents(response, generation, after);
    }
    const timer = setInterval(() => {
      if (closed) { clearInterval(timer); return; }
      if (generation.cancelled && !generation.events.some((value) => value.eventType === "CANCELLED")) {
        const cancelled = appendEvent(generation, "CANCELLED", { status: "CANCELLED" });
        response.write(frame(cancelled));
        clearInterval(timer);
        finish();
      }
    }, 25);
    return;
  }

  if (connection === 1) {
    appendEvent(generation, "STATUS", { status: "RUNNING" });
    appendEvent(generation, "DELTA", { delta: "Reconnect-safe draft" });
    writeEvents(response, generation, after);
    setTimeout(finish, 30);
    return;
  }
  completeGeneration(generation);
  writeEvents(response, generation, after);
  setTimeout(finish, 20);
}

async function handleApi(request, response, url) {
  const path = url.pathname;
  if (path === "/api/v1/auth/web/refresh" && request.method === "POST") {
    const current = sessionUser(request);
    if (!current) return apiError(response, 401, "INVALID_REFRESH_TOKEN", "Refresh session is invalid", path);
    const old = cookies(request).agricore_refresh;
    const next = `${randomUUID()}-${randomUUID()}`;
    state.sessions.delete(old);
    state.sessions.set(next, current);
    return json(response, 200, authResponse(current), { "Set-Cookie": refreshCookie(next) });
  }
  if (path === "/api/v1/auth/web/login" && request.method === "POST") {
    let body;
    try { body = await readBody(request); } catch { return apiError(response, 400, "INVALID_JSON", "Invalid JSON", path); }
    if (typeof body.email !== "string" || typeof body.password !== "string") {
      return apiError(response, 400, "INVALID_CREDENTIALS", "Email and password are required", path);
    }
    const user = userForEmail(body.email);
    for (const [key, session] of state.sessions) if (session.id === user.id) state.sessions.delete(key);
    for (const [key, tokenUser] of state.tokens) if (tokenUser.id === user.id) state.tokens.delete(key);
    for (const [key, conversation] of state.conversations) if (conversation.userId === user.id) state.conversations.delete(key);
    const refresh = `${randomUUID()}-${randomUUID()}`;
    state.sessions.set(refresh, user);
    return json(response, 200, authResponse(user), { "Set-Cookie": refreshCookie(refresh) });
  }
  if (path === "/api/v1/auth/web/logout" && request.method === "POST") {
    const refresh = cookies(request).agricore_refresh;
    if (refresh) state.sessions.delete(refresh);
    return json(response, 204, null, { "Set-Cookie": refreshCookie("", 0) });
  }

  const user = authOrFail(request, response, path);
  if (!user) return;
  if (path === "/api/v1/farms" && request.method === "GET") {
    return json(response, 200, page(demoFarms, 20));
  }
  const farmPlotsMatch = path.match(/^\/api\/v1\/farms\/([^/]+)\/plots$/);
  if (farmPlotsMatch && request.method === "GET") {
    const farmId = decodeURIComponent(farmPlotsMatch[1]);
    const plots = demoPlotsByFarm.get(farmId);
    if (!plots) return apiError(response, 404, "FARM_NOT_FOUND", "Farm not found", path);
    return json(response, 200, page(plots, 20));
  }
  if (path === "/api/v1/assistant/capabilities" && request.method === "GET") {
    return json(response, 200, { provider: "mock", available: true, streaming: true, reasonCode: null });
  }
  if (path === "/api/v1/assistant/conversations" && request.method === "GET") {
    const conversations = [...state.conversations.values()]
      .filter((value) => value.userId === user.id && value.status === (url.searchParams.get("status") ?? "OPEN"))
      .map(conversationResponse);
    return json(response, 200, page(conversations));
  }
  if (path === "/api/v1/assistant/conversations" && request.method === "POST") {
    let body;
    try { body = await readBody(request); } catch { return apiError(response, 400, "INVALID_JSON", "Invalid JSON", path); }
    const conversation = {
      id: randomUUID(), userId: user.id, title: String(body.title ?? "E2E conversation"),
      contextType: body.contextType === "FARM" ? "FARM" : "ENTERPRISE", farmId: body.farmId ?? null,
      status: "OPEN", nextMessageSequence: 0, version: 0, createdAt: now(), updatedAt: now(),
      archivedAt: null, purgeAfter: null,
    };
    state.conversations.set(conversation.id, conversation);
    return json(response, 201, conversationResponse(conversation));
  }

  const conversationMatch = path.match(/^\/api\/v1\/assistant\/conversations\/([^/]+)(?:\/(.*))?$/);
  if (!conversationMatch) return apiError(response, 404, "NOT_FOUND", "Route not found", path);
  const conversation = state.conversations.get(decodeURIComponent(conversationMatch[1]));
  if (!conversation || conversation.userId !== user.id) return apiError(response, 404, "NOT_FOUND", "Conversation not found", path);
  const remainder = conversationMatch[2] ?? "";
  if (remainder === "messages" && request.method === "GET") {
    const generation = [...state.generations.values()].find((value) => value.conversationId === conversation.id);
    return json(response, 200, messagePage(generation));
  }
  if (remainder === "archive" && request.method === "POST") {
    conversation.status = "ARCHIVED";
    conversation.archivedAt = now();
    return json(response, 200, conversationResponse(conversation));
  }
  if (remainder === "generations" && request.method === "POST") {
    let body;
    try { body = await readBody(request); } catch { return apiError(response, 400, "INVALID_JSON", "Invalid JSON", path); }
    const prompt = String(body.prompt ?? "").trim();
    const generation = {
      id: randomUUID(), conversationId: conversation.id,
      userMessageId: randomUUID(), assistantMessageId: null, prompt, scenario: user.scenario,
      status: "QUEUED", events: [], streamConnections: 0, cancelled: false, createdAt: now(), updatedAt: now(),
    };
    appendEvent(generation, "STATUS", { status: "QUEUED" });
    state.generations.set(generation.id, generation);
    conversation.updatedAt = generation.updatedAt;
    return json(response, 202, generationResponse(generation));
  }
  const generationMatch = remainder.match(/^generations\/([^/]+)(?:\/(events|cancel))?$/);
  if (!generationMatch) return apiError(response, 404, "NOT_FOUND", "Route not found", path);
  const generationId = decodeURIComponent(generationMatch[1]);
  if (!generationId) return apiError(response, 404, "NOT_FOUND", "Generation not found", path);
  const generation = state.generations.get(generationId);
  if (!generation || generation.conversationId !== conversation.id) return apiError(response, 404, "NOT_FOUND", "Generation not found", path);
  if (generationMatch[2] === "cancel" && request.method === "POST") {
    generation.cancelled = true;
    if (!generation.events.some((value) => value.eventType === "CANCELLED")) generation.status = "CANCEL_REQUESTED";
    generation.cancellationRequests = (generation.cancellationRequests ?? 0) + 1;
    return json(response, 200, generationResponse(generation));
  }
  if (generationMatch[2] === "events" && request.method === "GET") {
    const after = Number(url.searchParams.get("after") ?? "-1");
    if (request.headers.accept?.includes("text/event-stream")) return streamGeneration(request, response, generation, after);
    const limit = Math.min(Number(url.searchParams.get("limit") ?? "1000"), 1000);
    return json(response, 200, generation.events.filter((value) => value.sequenceNo > after).slice(0, limit));
  }
  if (!generationMatch[2] && request.method === "GET") return json(response, 200, generationResponse(generation));
  return apiError(response, 404, "NOT_FOUND", "Route not found", path);
}

function staticFile(request, response, url) {
  const requested = url.pathname === "/" ? "/index.html" : url.pathname;
  const candidate = normalize(join(DIST, requested));
  const relativePath = relative(DIST, candidate);
  const insideDist = relativePath !== "" && !relativePath.startsWith("..") && !isAbsolute(relativePath);
  const file = insideDist && existsSync(candidate) && statSync(candidate).isFile()
    ? candidate : join(DIST, "index.html");
  const type = { ".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8", ".css": "text/css; charset=utf-8", ".svg": "image/svg+xml", ".json": "application/json" }[extname(file)] ?? "application/octet-stream";
  writeHeaders(response, { "Content-Type": type });
  response.writeHead(200);
  createReadStream(file).pipe(response);
}

const server = createServer(async (request, response) => {
  const url = new URL(request.url ?? "/", `http://${request.headers.host ?? "127.0.0.1"}`);
  try {
    if (url.pathname === "/healthz") return json(response, 200, { status: "ok" });
    if (url.pathname === "/__e2e/state") {
      const users = [...state.users.values()].map((user) => ({
        email: user.email, scenario: user.scenario,
        generations: [...state.generations.values()].filter((value) => value.conversationId && [...state.conversations.values()].some((conversation) => conversation.id === value.conversationId && conversation.userId === user.id)).map((value) => ({ id: value.id, streamConnections: value.streamConnections, cancellationRequests: value.cancellationRequests ?? 0, status: value.status })),
      }));
      return json(response, 200, { users });
    }
    if (url.pathname.startsWith("/api/")) return await handleApi(request, response, url);
    if (request.method === "GET") return staticFile(request, response, url);
    return apiError(response, 404, "NOT_FOUND", "Route not found", url.pathname);
  } catch (error) {
    console.error(error);
    if (!response.headersSent) apiError(response, 500, "E2E_SERVER_ERROR", "Mock edge server failure", url.pathname);
    else response.end();
  }
});

server.listen(PORT, "127.0.0.1", () => {
  console.log(`AgriCore E2E mock edge listening on http://127.0.0.1:${PORT}`);
});
