# Deploying Quark

This guide covers production deployment strategies for Quark, including containerized deployments, scaling considerations, and operational best practices.

## Quick Start: Docker Compose

The fastest way to deploy Quark is via Docker Compose:

```bash
docker-compose up -d
```

This uses the `compose.yaml` in the repository root, which exposes:

- **RTMP ingress:** Port 1935
- **HTTP API & UI:** Port 8080

See `doc/CONFIGURING.md` for all environment variable options.

## Docker Deployment

### Build the Docker Image

From the repository root:

```bash
# Build the backend JAR
cd server
mvn clean install -DskipTests

# Build the Docker image
docker build -t quark:latest .
```

The `Dockerfile` includes:

- **Base image:** `eclipse-temurin:24-jre-ubi9-minimal` (lightweight JRE on UBI9).
- **FFmpeg:** Pre-built binaries for x86_64 and aarch64 (ARM64).
- **Healthcheck:** HTTP GET to `/_healthcheck` every 5 seconds.

## Environment Configuration

See `doc/CONFIGURING.md` for the complete reference.

## Reverse Proxy Setup

Quark does **not** include built-in TLS/SSL. Use a reverse proxy in front for production HTTPS.

## Persistence & Data

Quark **does not persist stream data** on disk by default. All session state is in-memory.

### Recording/Archiving

To record or archive streams, configure an **egress target** via webhooks:

1. In the `SESSION_STARTED` webhook response, include an egress configuration:

   ```json
   {
     "type": "pipeline",
     "config": {
       "command": [
         "ffmpeg",
         "-i",
         "-",
         "-c",
         "copy",
         "-f",
         "matroska",
         "/archive/stream-%{session_id}-%Y%m%d_%H%M%S.mkv"
       ]
     }
   }
   ```

2. Or use **RTMP egress** to push to a third-party platform or storage service.

See `doc/WEBHOOKS.md` for egress configuration details.

### Logs

Logs are written to stdout by default and can be captured by Docker/Kubernetes.

To increase verbosity, set `QUARK_DEBUG=true`.

## Network & Firewall

### Required Ports

| Port | Protocol | Purpose                          |
| :--- | :------- | :------------------------------- |
| 1935 | TCP      | RTMP ingress/egress (if enabled) |
| 8080 | TCP      | HTTP API + playback              |

### Outbound

Quark initiates:

- **Webhook calls** to `QUARK_WEBHOOK_URL` (HTTPS POST).
- **RTMP egress** to target RTMP servers.
- **FFmpeg** subprocess I/O for pipeline/HLS operations.

Ensure firewall allows these outbound connections.

## Performance Tuning

### Memory

The JVM heap is controlled via standard Java options. By default, the container uses the JRE's adaptive sizing.

To set explicit heap limits, override the `CMD`:

```dockerfile
CMD [ "java", "-Xms512m", "-Xmx2g", "-jar", "quark.jar" ]
```

### Virtual Threads (Experimental)

For workloads with many concurrent connections, enable virtual threads:

```bash
QUARK_EXP_VIRTUAL_THREAD_HEAVY_IO=true
```

This uses Java 21 Virtual Threads for I/O-bound operations, reducing thread memory overhead.

### FFmpeg Concurrency

If using pipeline egress (e.g., HLS, recording) at scale, ensure sufficient FFmpeg processes:

```bash
# Example: Up to 16 concurrent FFmpeg processes
ulimit -u 1000
```

In a container, set resource limits and monitor CPU/memory.

## Monitoring & Observability

### Health Check

Quark exposes a health endpoint for monitoring:

```bash
curl http://localhost:8080/_healthcheck
```

Returns `HTTP 200 OK` if healthy.

### Logs & Debugging

Enable debug logging:

```bash
QUARK_DEBUG=true
```

Check container logs:

```bash
docker logs quark
```

### Metrics

Quark does not currently expose Prometheus metrics. Observability is primarily via:

- **HTTP API** (`GET /api/sessions`) for session state.
- **Logs** for lifecycle and error events.
- **Webhooks** for custom business logic and tracking.

### API Endpoints

- `GET /api/sessions` — List active sessions.
- `GET /api/sessions/{id}` — Get session details.
- `DELETE /api/sessions/{id}` — Terminate a session.
- `GET /_healthcheck` — Health check.

## Security Best Practices

1. **Enable authentication:**

   ```bash
   QUARK_AUTH_SECRET="<your-strong-random-secret>"
   ```

2. **Use HTTPS:**

   - Deploy behind a reverse proxy with TLS.
   - Set `X-Forwarded-Proto: https` from proxy.

3. **Restrict webhook endpoint:**

   - Validate webhook signatures (if implemented).
   - Firewall the webhook URL to trusted IPs.

4. **Limit anonymous playback:**

   ```bash
   QUARK_ANON_PREGEX="^(public|demo)$"  # Only allow 'public' and 'demo' sessions OR leave blank to prevent anonymous playback.
   ```

5. **Monitor for abuse:**

   - Log all webhook calls.
   - Alert on unusual session counts or bandwidth.

## Related Documentation

- **Architecture:** `doc/ARCHITECTURE.md` — System design and module overview.
- **Configuration:** `doc/CONFIGURING.md` — All environment variables.
- **Webhooks:** `doc/WEBHOOKS.md` — Session lifecycle events and control.
