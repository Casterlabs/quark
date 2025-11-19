# AGENTS

This document orients AI co-workers to the Quark codebase so you can make confident, repo-aware edits without guesswork.

## Quick facts

- **Stack:** Java 21 (Maven multi-module backend) + SvelteKit/Vite management UI.
- **Monorepo layout:** Backend lives under `server/` (with nested modules like `core`, `http`, `protocol/*`); the UI lives in `management-ui/`.
- **Primary domain:** Real-time streaming orchestration (ingress, session control, multi-egress).
- **Key docs:** `doc/ARCHITECTURE.md`, `doc/CONFIGURING.md`, `doc/WEBHOOKS.md`.

## Repository map (high level)

| Path                       | Purpose                                                                    |
| -------------------------- | -------------------------------------------------------------------------- |
| `server/bootstrap`         | Entry point that discovers protocol daemons via custom annotations.        |
| `server/core`              | Session lifecycle logic, webhook dispatch, extensibility contracts.        |
| `server/http`              | REST/HTTP playback server.                                                 |
| `server/protocol/rtmp`     | RTMP ingress + egress.                                                     |
| `server/protocol/webrtc`   | WHIP/WebRTC ingress.                                                       |
| `server/protocol/hls`      | HLS egress playlist/segment generation.                                    |
| `server/protocol/pipeline` | Internal media pipeline helpers.                                           |
| `management-ui`            | Svelte management console (Vite build, Tailwind-like styles in `app.css`). |
| `doc`                      | Human/agent-facing docs (this file included).                              |

> Tip: Many submodules already have compiled `target/` artifacts—edit `src/main/java` and rely on Maven to rebuild.

## Backend architecture refresher

1. `Bootstrap` scans the classpath for Quark annotations:
   - `@QuarkEntrypoint` → daemon classes with `start()`.
   - `@QuarkEgressConfiguration` → register egress config factories.
   - `@QuarkHttpEndpoint` → bind REST handlers.
   - `@QuarkStaticSessionListener` → observe session events.
2. A session begins when an ingress (RTMP/WHIP/etc.) connects.
3. Webhooks gate and configure the session (see next section).
4. Media flows through configured egresses (RTMP restream, HLS, pipeline, etc.).
5. Shutdown triggers ending webhooks and cleans resources.

When editing, keep the annotation discovery and plugin boundaries intact—new functionality usually fits as a new annotated component inside the relevant module.

## Webhook contract (server ⇄ external control)

All webhooks are POST JSON with shape `{ "type": "...", "data": { ... } }`.

| Event              | Direction | Notes for agents                                                                                        |
| ------------------ | --------- | ------------------------------------------------------------------------------------------------------- |
| `SESSION_STARTING` | Sync      | Provide/validate stream ID. Reject by omitting `id`. Use fields: protocol, ip, url, app, key, metadata. |
| `SESSION_STARTED`  | Async     | Supply egress configs (`rtmp`, `pipeline`, others) or set `shouldTerminate`.                            |
| `SESSION_ENDING`   | Sync      | Can jam session via `{ source, loop }`. `wasGraceful` indicates error vs normal exit.                   |
| `SESSION_ENDED`    | Async     | Notification only; response ignored.                                                                    |

Any new feature that changes session timing or metadata must stay compatible with this contract.

## Configuration knobs (env vars)

- **Core:** `QUARK_DEBUG`, `QUARK_AUTH_SECRET`, `QUARK_ANON_PREGEX`, `QUARK_WEBHOOK_URL`, `QUARK_THUMB_IT`, `QUARK_EXP_VIRTUAL_THREAD_HEAVY_IO`.
- **HTTP:** `HTTP_PORT` (set `-1` to disable API/UI controls).
- **RTMP:** `RTMP_PORT` (set `-1` to disable ingress).
- **WebRTC:** `QUARK_EXP_WHIP`, `QUARK_EXP_WHIP_AVC_AUTO_RECONFIG`, `QUARK_WHIP_OVERRIDE_ADDRESS`.
- **HLS:** `QUARK_EXP_HLS`.

If you introduce new configuration, document it in `doc/CONFIGURING.md` and propagate defaults through the appropriate module.

## Development workflow cheat sheet

- **Backend:** Use Maven from `server/` (e.g., `mvn clean install`). Each module has its own `pom.xml`, but the parent handles dependency alignment.
- **UI:** From `management-ui/`, run `npm install` once, then `npm run dev` or `npm run build` (SvelteKit 5 n Vite). TypeScript config lives in `tsconfig.json`.
- **Docker:** `compose.yaml` wires services; update any new ports or env vars there.

Always run targeted tests (or at least the module build) after touching backend code, and run `npm run check`/`test` for UI changes.

## Guidance for LLM/agent contributors

1. **Respect module boundaries:** Place protocol-specific code under `server/protocol/<name>/src/main/java` and keep shared abstractions in `server/core`.
2. **Keep docs in sync:** Architecture/config/webhooks docs should evolve with code changes; update this `AGENTS.md` if behavior shifts.
3. **Mind generated assets:** Ignore `target/` outputs and `languageServers-log/` when editing.
4. **UI ↔ API alignment:** Any API shape changes in `server/http` must be reflected in Svelte stores/routes (`management-ui/src/lib` & `routes`).
5. **Testing priority:** For new session behaviors, add/adjust unit or integration tests in the relevant module and exercise webhook scenarios if applicable.
6. **Security:** If touching auth (`QUARK_AUTH_SECRET`), ensure JWT/HMAC usage remains opt-in and defaults to open access only when explicitly desired.

## Handy references

- Architecture deep dive: `doc/ARCHITECTURE.md`.
- Env configuration table: `doc/CONFIGURING.md`.
- Webhook payloads & sequencing: `doc/WEBHOOKS.md`.
- UI entry layout: `management-ui/src/routes/+layout.svelte` and `+page.svelte` for dashboard screens.

Keep this file updated whenever you add protocols, env vars, or workflows so future agents can onboard instantly.
