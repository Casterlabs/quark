# Quark

Quark is a modular live-stream orchestration stack that accepts real-time ingress (RTMP, WHIP/WebRTC) and fans it out across multiple egress targets (HTTP playback, RTMP restream, HLS, custom pipelines). The backend is written in Java 21 with a Maven multi-module layout, and the management console is a SvelteKit/Vite app.

## Highlights

- **Plugin-style protocols:** Each ingress/egress lives in its own module under `server/protocol/*`, enabling you to add or ship protocol extensions without touching the core runtime.
- **Webhook-driven control loop:** Authentication, configuration, and teardown are negotiated through four lifecycle webhooks (`SESSION_STARTING/STARTED/ENDING/ENDED`). External services can approve sessions, inject metadata, and define egress targets on the fly.
- **Management UI:** A SvelteKit dashboard (in `management-ui/`) surfaces session state, stats, and controls. It talks to the HTTP API in `server/http`.
- **Docker-first deploys:** A single compose service (`compose.yaml`) exposes common ports (RTMP 1935, HTTP 8080) and wires the documented environment variables.

## Repository tour

| Path                       | Description                                                                   |
| -------------------------- | ----------------------------------------------------------------------------- |
| `server/bootstrap`         | Scans the classpath for Quark annotations and boots protocol daemons.         |
| `server/core`              | Session lifecycle, webhook dispatch, extensibility contracts, shared models.  |
| `server/http`              | REST + playback server that backs the UI.                                     |
| `server/protocol/rtmp`     | RTMP ingress + egress implementation.                                         |
| `server/protocol/webrtc`   | WHIP/WebRTC ingress daemon.                                                   |
| `server/protocol/hls`      | HLS playlist and segment generation.                                          |
| `server/protocol/pipeline` | Internal helpers for custom media pipelines.                                  |
| `management-ui/`           | SvelteKit management console (Vite build, Tailwind-like styles in `app.css`). |
| `doc/`                     | Architecture, configuration, webhook, and agent-focused documentation.        |

## Architecture in a nutshell

1. **Bootstrap** discovers annotated components:
   - `@QuarkEntrypoint` → daemon with a `start()` method (RTMP, HTTP, etc.).
   - `@QuarkEgressConfiguration` → registers egress config factories.
   - `@QuarkHttpEndpoint` → exposes REST handlers.
   - `@QuarkStaticSessionListener` → subscribes to session events.
2. **Ingress connects** via RTMP or WHIP. The `SESSION_STARTING` webhook authenticates the attempt and returns a session ID.
3. **Session starts** once approved, triggering `SESSION_STARTED`. The response can define egresses (RTMP restreams, pipelines, HLS) or terminate immediately.
4. **Streaming loop** pushes media through each configured egress until the ingress disconnects or a webhook terminates it.
5. **Teardown** fires `SESSION_ENDING` (allowing “jam” content) and finally `SESSION_ENDED` notifications.

See `doc/ARCHITECTURE.md` for the full diagram.

## Getting started locally

### Requirements

- JDK 21+
- Apache Maven 3.9+
- Node.js 20+ (for the UI)

### Backend build & run

```powershell
cd server
mvn clean install
```

This builds every module. Launching the stack typically involves the Bootstrap module (e.g., `java -jar bootstrap/target/<artifact>.jar`).

### Management UI

```powershell
cd management-ui
npm install
npm run dev
```

## Documentation & next steps

- `doc/ARCHITECTURE.md` — system design walkthrough.
- `doc/CONFIGURING.md` — environment variable reference.
- `doc/WEBHOOKS.md` — webhook payloads and sequencing.
- `AGENTS.md` — tips for AI/LLM contributors.
