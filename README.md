# AI Pharmacy Assistant

Standalone Spring Boot assistant that answers questions with **SELF** knowledge, **local seed database** tools, and **RAG** over a demo Markdown corpus. Built for portfolio / interview demos — not a real pharmacy storefront and not connected to any production sales system.

This repository is independent of the original pharmacy website project. Do not point it at a production MongoDB URI or reuse production secrets.

---

## 1. What it does

- Chat UI at `http://localhost:8080/` (static `index.html`)
- Gemini function calling with SELF / DB / RAG / DB+RAG routing
- Local JWT login for private tools (profile, cart, orders, addresses)
- RAG ingest of `classpath:rag-data/**/*.md` into MongoDB `rag_chunks`
- In-app cosine retrieval (full collection scan) — **not** MongoDB Atlas Vector Search
- Optional structured RAG debug logs and admin-only debug search endpoint

---

## 2. Main features

| Area | Capability |
|------|------------|
| Chat | Session history via `X-Session-Id`, clear history, grounded replies |
| Catalog (seed) | Search products, detail, discounts, store config |
| Personal (JWT) | My profile, orders, order detail, cart, addresses |
| RAG | Ingest → chunk → embed → cosine → threshold → topK → Gemini |
| UI | Login/logout, suggestion prompts, tool/source chips from real API metadata |
| Ops | Docker Compose Mongo, seed on empty DB, startup ingest when `chunkCount == 0` |

---

## 3. Tech stack (actual)

| Layer | Choice |
|-------|--------|
| Language | Java 21 |
| Framework | Spring Boot 3.5.10 |
| Security | Spring Security + JWT (jjwt 0.12.x) |
| LLM | Gemini `generateContent` + function calling |
| Embeddings | Gemini `gemini-embedding-001` via OkHttp |
| Database | MongoDB 7 (Docker Compose), DB name `ai_pharmacy_assistant` |
| Vector index | **None** — cosine similarity computed in Java |
| Frontend | Single-page `static/index.html` (no React rebuild in this phase) |

Chunking defaults: target size **700**, overlap **80**. Retrieval: score threshold **0.25**, then **topK = 4**.

---

## 4. Architecture — SELF / DB / RAG / DB+RAG

```text
Browser (index.html)
    │  Authorization: Bearer <JWT>   (optional)
    │  X-Session-Id: <session>
    ▼
POST /api/chat
    ▼
ChatbotService
    ├─ SELF          → Gemini answers without tools (greetings, how-to-use)
    ├─ DB            → product / discount / store / getMy* tools → Mongo seed collections
    ├─ RAG           → searchKnowledge → RagSearchService → rag_chunks
    └─ DB + RAG      → both tool types in one turn, then Gemini synthesizes
```

Tool payloads include `sourceType` = `DB` or `RAG`. The API reply may include `toolsUsed` and `sources` derived from those payloads (never invented on the client).

---

## 5. Function calling and tools

**Public (no login)**

| Tool | Role |
|------|------|
| `searchProducts` | Name / keyword / price filter on seed catalog |
| `getProductDetail` | Single product: price, stock, category |
| `getDiscountProducts` | Active discount events |
| `getStoreInfo` | Local store config placeholders |
| `searchKnowledge` | RAG over demo Markdown |

**Private (JWT required — user id from principal, never from client body)**

| Tool | Role |
|------|------|
| `getMyProfile` | Current user profile |
| `getMyOrders` / `getMyOrderDetail` | Own orders only |
| `getMyCart` / `getMyAddresses` | Own cart / addresses |

Not exposed: navigation tools (`openProduct`, `openMyOrders`) — no SPA router in this build.

Flow: Gemini may emit one or more `functionCall` parts → backend executes tools → returns `functionResponse` parts → Gemini writes the final answer (up to 4 tool rounds).

---

## 6. RAG pipeline

```text
Markdown (rag-data/**)
    → split by ## headings, then window (700 / overlap 80)
    → embed each chunk (gemini-embedding-001)
    → save to Mongo collection rag_chunks
    → (re-ingest: deleteByDocumentPath then insert — no duplicate paths)

Query time:
    → embed question (same embedding model)
    → cosine similarity vs all chunks with valid vectors
    → keep score ≥ threshold (0.25)
    → take topK (4)
    → return chunks to Gemini as searchKnowledge result
    → generate answer grounded on chunk text
```

**Important:** retrieval is an **in-process cosine scan**. This project does **not** use Atlas `$vectorSearch` or a separate vector DB.

---

## 7. Repository layout

```text
rag-knowledge-assistant/
├── docker-compose.yml          # local Mongo only
├── .env.example                # copy guidance (secrets not committed)
├── README.md
└── backend/
    ├── .env.example
    ├── pom.xml
    ├── mvnw / mvnw.cmd
    └── src/main/
        ├── java/rag_assistant/
        │   ├── controller/     # chat, auth, rag, catalog, admin
        │   ├── service/        # ChatbotService, tools, auth
        │   ├── rag/            # ingest, search, embedding, models
        │   ├── catalog/        # seed DB query services
        │   ├── security/       # JWT filter & config
        │   └── dto/
        └── resources/
            ├── application.properties
            ├── static/index.html
            └── rag-data/       # demo corpus (store + products)
```

---

## 8. Environment and `.env`

From repo root or `backend/`:

```bash
cd backend
copy .env.example .env
```

Required / important variables:

| Variable | Purpose |
|----------|---------|
| `MONGODB_URI` | Must be local, e.g. `mongodb://localhost:27018/ai_pharmacy_assistant` (Compose maps host `27018` → container `27017`) |
| `GEMINI_API_KEY` | Your key from [Google AI Studio](https://aistudio.google.com/apikey) |
| `JWT_SECRET` | Base64 HMAC secret (**local only**, do not reuse production) |
| `JWT_EXPIRATION_MINUTES` | Default `1440` |
| `APP_SEED_ENABLED` | Seed business collections when empty |
| `APP_RAG_STARTUP_INGEST` | Ingest corpus when `chunkCount == 0` |
| `APP_RAG_DEBUG` | Structured retrieval logs + admin debug search |
| `APP_RAG_SCORE_THRESHOLD` | Default `0.25` |
| `PORT` | Default `8080` |

Never commit a filled `.env`. Never put customer PII or real pharmacy credentials in this repo.

---

## 9. MongoDB with Docker Compose

```bash
# from repo root
docker compose up -d
```

- Container: `ai-pharmacy-mongo`
- Host port: `27018` (maps to Mongo inside the container on `27017`) — chosen so it does not clash with another local Mongo already using `27017`
- App URI: `mongodb://localhost:27018/ai_pharmacy_assistant`
- Database name used by the app: `ai_pharmacy_assistant`
- Volume: `ai_pharmacy_mongo_data`

Stop: `docker compose down` (add `-v` only if you intentionally wipe local data). Do not stop or remove unrelated Mongo containers on your machine.

---

## 10. Seed business data and ingest RAG

### Business seed

On startup, if the `product` collection is empty and `APP_SEED_ENABLED=true`, `DatabaseSeeder` loads synthetic catalog, users, carts, addresses, and sample orders.

Demo accounts (password hashed with BCrypt in Mongo):

| Username | Role | Password (local demo) |
|----------|------|------------------------|
| `alice_demo` | USER | `DemoUser123!` |
| `bob_demo` | USER | `DemoUser123!` |
| `admin_demo` | ADMIN | `DemoUser123!` |

Force re-seed (wipes business collections, **not** `rag_chunks`):

```bash
# login as admin_demo, then:
curl -X POST "http://localhost:8080/api/admin/seed?force=true" ^
  -H "Authorization: Bearer <admin-jwt>"
```

Verify: `GET /api/catalog/status`, `GET /api/catalog/products?q=Biragan`

### RAG ingest

- Automatic when `rag_chunks` is empty and `APP_RAG_STARTUP_INGEST=true` (needs valid Gemini key).
- Manual (ADMIN JWT): `POST /api/rag/ingest`
- Status: `GET /api/rag/status` → `chunkCount`, `chunksWithEmbedding`, `returnPolicyChunks`, …

Wait until ingest finishes before judging retrieval quality. A mid-startup `chunkCount` is not the final corpus size.

---

## 11. Run backend and open UI

```bash
docker compose up -d
cd backend
.\mvnw.cmd spring-boot:run
```

Open **http://localhost:8080/** — AI Pharmacy Assistant UI.

---

## 12. Login, API examples, tests

### Login

```bash
curl -X POST http://localhost:8080/api/auth/login ^
  -H "Content-Type: application/json" ^
  -d "{\"username\":\"alice_demo\",\"password\":\"DemoUser123!\"}"
```

Response includes `token`. Send `Authorization: Bearer <token>` on `/api/chat` for private tools. The backend resolves `userId` from the JWT principal — the UI must **not** pass another user’s id.

### Chat

```bash
curl -X POST "http://localhost:8080/api/chat?message=Gia%20Biragan" ^
  -H "X-Session-Id: demo1" ^
  -H "Authorization: Bearer <optional-jwt>"
```

### Clear history

```bash
curl -X DELETE http://localhost:8080/api/chat/history -H "X-Session-Id: demo1"
```

### Tests

```bash
cd backend
.\mvnw.cmd test
```

Integration tests that call Gemini need a real `GEMINI_API_KEY` and a reachable Mongo. Treat them separately from pure unit tests (e.g. cosine math). Do not claim “all PASS” without running them.

---

## 13. RAG debug (development only)

1. Set `APP_RAG_DEBUG=true` in `backend/.env`
2. Restart the app
3. Watch structured logs: `queryId`, candidate counts, top scores/paths, returned count
4. Admin-only search probe:

```bash
curl "http://localhost:8080/api/rag/debug/search?q=chinh%20sach%20doi%20tra" ^
  -H "Authorization: Bearer <admin-jwt>"
```

Disabled when `APP_RAG_DEBUG=false`. Not for end users. Logs must not include API keys, JWT secrets, or passwords.

---

## 14. Troubleshooting

### MongoDB connection refused

- Confirm Docker Compose is up and `MONGODB_URI` targets `localhost:27018/ai_pharmacy_assistant`
- Never point at a production pharmacy database

### Gemini errors / missing key

- Set `GEMINI_API_KEY` in `backend/.env` and run Maven from `backend/`
- `401`/`403`: invalid key; `429` / `RESOURCE_EXHAUSTED`: wait and retry

### Authentication / LOGIN_REQUIRED

- Login via UI or `POST /api/auth/login`
- Private tools fail closed if JWT missing/invalid
- Logout blacklists the token server-side

### Ingestion / `chunkCount = 0`

- Fix Mongo + Gemini key, then `POST /api/rag/ingest` as admin
- Re-ingest is safe on the local demo DB (per-`documentPath` replace)

### “No document found” for policies

- Confirm `GET /api/rag/status` shows a stable `chunkCount > 0` **after** ingest completes
- Confirm return-policy file chunks exist (`returnPolicyChunks`)
- Use admin debug search to inspect scores; threshold default is `0.25`
- Clear chat session if earlier empty-corpus turns confused the model

---

## 15. Current limits and next improvements

**Limits**

- Cosine scan is O(n); fine for a small demo corpus, not large-scale production search
- Chat history is in-memory only (lost on restart)
- Seed data is synthetic; prices/stock are **not** live production inventory
- No published RAG accuracy benchmark — do not quote a made-up precision percentage
- UI is static HTML, not a full React storefront
- Demo corpus / store brand names are fictional placeholders

**Possible next steps**

- Hybrid keyword + vector retrieval if cosine alone misses paraphrases
- Persistent chat history
- Formal RAG evaluation set
- Stronger packaging for a public demo release (without leaking secrets)

---

## Relation to the original pharmacy project

This assistant was created by **copying** chatbot/RAG ideas into a separate folder, then extending with local seed DB + JWT for a full demo. The original pharmacy website and its production database remain untouched and must keep their own configuration.
