# Configuring Quark

This document lists the environment variables used to configure Quark.

## Core

| Variable                            | Default | Description                                                                                         |
| :---------------------------------- | :------ | :-------------------------------------------------------------------------------------------------- |
| `QUARK_DEBUG`                       | `false` | Enable debug mode.                                                                                  |
| `QUARK_AUTH_SECRET`                 | `null`  | HMAC256 secret for signed JWTs. If not defined, the API will be "open" (no authorization required). |
| `QUARK_ANON_PREGEX`                 | `null`  | The default playback regex for anonymous users. Null to disallow anonymous viewership.              |
| `QUARK_WEBHOOK_URL`                 | `null`  | Webhook URL. Null to disable webhooks.                                                              |
| `QUARK_THUMB_IT`                    | `30`    | The interval (in seconds) in which a thumbnail should be rendered.                                  |
| `QUARK_EXP_VIRTUAL_THREAD_HEAVY_IO` | `false` | **Experimental.** Whether or not to use Virtual Threads for heavy-IO tasks.                         |

## HTTP

| Variable    | Default | Description                                                                                                                |
| :---------- | :------ | :------------------------------------------------------------------------------------------------------------------------- |
| `HTTP_PORT` | `8080`  | The port to bind the HTTP server to. Set to `-1` to disable. If disabled, Quark will have no controls aside from Webhooks. |

## RTMP

| Variable    | Default | Description                                                  |
| :---------- | :------ | :----------------------------------------------------------- |
| `RTMP_PORT` | `1935`  | The port to bind the RTMP server to. Set to `-1` to disable. |

## WebRTC

| Variable                           | Default | Description                                                                                                                                                                 |
| :--------------------------------- | :------ | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `QUARK_EXP_WHIP`                   | `false` | **Experimental.** Enable WHIP support.                                                                                                                                      |
| `QUARK_EXP_WHIP_AVC_AUTO_RECONFIG` | `false` | **Experimental.** Enable WHIP AVC auto reconfiguration. This experimental option will send a new AVCDecoderConfiguration payload when it detects a SPS/PPS PLI from WebRTC. |
| `QUARK_WHIP_OVERRIDE_ADDRESS`      | `""`    | Override address for WHIP. By default, it advertises all interfaces.                                                                                                        |

## HLS

| Variable        | Default | Description                                                                   |
| :-------------- | :------ | :---------------------------------------------------------------------------- |
| `QUARK_EXP_HLS` | `false` | **Experimental.** Whether or not to generate a HLS playlist for each session. |
