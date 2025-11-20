# Architecture

Quark is designed with a modular, plugin-based architecture. The system is composed of a core server and several protocol modules that handle specific streaming technologies.

## High-Level Overview

The application is structured as a Java-based backend (`server/`) and a Svelte-based management UI (`management-ui/`).

### Backend Structure

The backend is divided into several Maven modules:

- **`bootstrap`**: The entry point of the application. It uses reflection to discover and initialize other components.
- **`core`**: Contains the central logic for session management, webhooks, and extensibility interfaces.
- **`http`**: Handles HTTP requests for the API and playback.
- **`protocol/`**: Contains implementations for different streaming protocols.
  - **`rtmp`**: RTMP ingress and egress.
  - **`webrtc`**: WebRTC ingress (WHIP).
  - **`hls`**: HLS egress.
  - **`pipeline`**: Session pipeline mechanisms.

## Core Concepts

### Extensibility

Quark uses a custom annotation-based system to load components at runtime. The `Bootstrap` class scans the classpath for the following annotations:

- `@QuarkEntrypoint`: Marks a class with a `start()` method to be invoked on startup. Used by protocol daemons (e.g., `RTMPDaemon`, `HTTPDaemon`).
- `@QuarkEgressConfiguration`: Registers a configuration class for a specific egress type (e.g., RTMP, Pipeline).
- `@QuarkHttpEndpoint`: Registers an HTTP endpoint provider.
- `@QuarkStaticSessionListener`: Registers a listener for session events.

### Session Management

A **Session** represents a live stream. It is created when an ingress connection is established (e.g., an RTMP stream starts).

- **Ingress**: The source of the stream.
- **Egress**: The destination(s) of the stream. A session can have multiple egresses (e.g., recording to disk, restreaming to another RTMP server, or serving via HTTP).

### Webhooks

The `core` module triggers webhooks for session lifecycle events (`SESSION_STARTING`, `SESSION_STARTED`, `SESSION_ENDING`, `SESSION_ENDED`). This allows external applications to control authentication, configuration, and stream management.

## Data Flow

1.  **Ingress**: A client connects via a protocol (e.g., RTMP).
2.  **Authentication**: The `SESSION_STARTING` webhook is fired to authenticate the stream.
3.  **Session Creation**: If authenticated, a `Session` object is created.
4.  **Configuration**: The `SESSION_STARTED` webhook is fired to retrieve egress configurations.
5.  **Streaming**: Media data flows from the ingress to the configured egresses.
6.  **Termination**: When the source disconnects, the `SESSION_ENDING` webhook is fired (allowing for "jamming" or fallback content), followed by `SESSION_ENDED`.

## Modules

### HTTP

The HTTP module provides:

- **API**: Endpoints for stream control and information.
- **Playback**: Serves HTTP-based media.

### RTMP

- **Ingress**: Accepts incoming RTMP streams.
- **Egress**: Can push streams to other RTMP servers.

### WebRTC

- **Ingress (WHIP)**: Accepts WebRTC streams via the WHIP protocol.
- **Egress (WHEP)**: Playback via the WHEP protocol.

### HLS

- **Egress**: Generates HLS playlists and segments for playback via standard HLS players.
