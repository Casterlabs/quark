# Webhooks

Quark uses webhooks to notify your application about session lifecycle events. You can configure the webhook URL using the `QUARK_WEBHOOK_URL` environment variable.

All webhook requests are sent as `POST` requests with a JSON body. The body always follows this structure:

```json
{
  "type": "EVENT_TYPE",
  "data": { ... }
}
```

## Events

### `SESSION_STARTING`

Triggered when a new session is about to start. This is a synchronous webhook, meaning Quark will wait for a response before proceeding.

**Request Data:**

| Field      | Type    | Description                                          |
| :--------- | :------ | :--------------------------------------------------- |
| `protocol` | String  | The protocol used (e.g., "RTMP", "WHIP").            |
| `ip`       | String  | The IP address of the client.                        |
| `url`      | String? | The URL the client is connecting to (if applicable). |
| `app`      | String? | The application name (e.g., RTMP app).               |
| `key`      | String  | The stream key or identifier.                        |
| `metadata` | Object? | Additional metadata provided by the client.          |

**Response:**

To allow the session, return a JSON object with the `id` field. To reject the session, return `null` or omit the `id`.

```json
{
  "id": "session-id"
}
```

### `SESSION_STARTED`

Triggered after a session has successfully started. This is an asynchronous webhook.

**Request Data:**

| Field      | Type    | Description                                    |
| :--------- | :------ | :--------------------------------------------- |
| `id`       | String  | The session ID returned in `SESSION_STARTING`. |
| `info`     | Object  | Information about the streams (video/audio).   |
| `metadata` | Object? | Metadata associated with the session.          |

**Response:**

You can configure egresses (outputs) for the session or terminate it immediately.

```json
{
  "shouldTerminate": false,
  "egresses": {
    "rtmp": [
      { ...rtmp config... }
    ],
    "pipeline": [
      { ...pipeline config... }
    ]
  }
}
```

### `SESSION_ENDING`

Triggered when a session is ending. This is a synchronous webhook.

**Request Data:**

| Field         | Type    | Description                                                                          |
| :------------ | :------ | :----------------------------------------------------------------------------------- |
| `id`          | String  | The session ID.                                                                      |
| `wasGraceful` | Boolean | Whether the session ended gracefully (e.g., client disconnected) or due to an error. |
| `metadata`    | Object? | Metadata associated with the session.                                                |

**Response:**

You can optionally "jam" the session by providing a source URL. This will play the source to the client instead of disconnecting them immediately.

```json
{
  "source": "http://example.com/jam.mp4",
  "loop": true
}
```

If `source` is `null`, the session will close normally.

### `SESSION_ENDED`

Triggered when a session has fully ended. This is an asynchronous webhook.

**Request Data:**

| Field | Type   | Description     |
| :---- | :----- | :-------------- |
| `id`  | String | The session ID. |

**Response:**

Ignored.
