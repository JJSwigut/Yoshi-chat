# Yoshi Chat Android

Native Android chat client shell for the Yoshi local agent. This app is intentionally single-module, Kotlin, Jetpack Compose, and Gradle Kotlin DSL.

## Demo

https://github.com/user-attachments/assets/a49cc366-0f46-449d-93b8-0d319e1177cc

## Run The Backend

This repo contains the Android client only. The chat flow depends on the
existing Yoshi monorepo from the take-home prompt because the API worker and
agents service are not part of this Android submission.

Expected local layout:

```text
yoshi/
├── apps/
│   ├── api/
│   └── agents/
└── Yoshi-chat/
```

If `Yoshi-chat` is cloned somewhere else, point the demo script at the backend
monorepo:

```bash
YOSHI_REPO_ROOT=/path/to/yoshi scripts/start-demo-stack.sh physical --launch-app
```

Recommended demo path from the Android project folder:

```bash
scripts/start-demo-stack.sh physical --launch-app
```

Use `physical` for a USB-connected phone. Use `emulator` for an Android emulator:

```bash
scripts/start-demo-stack.sh emulator --launch-app
```

The script starts the API worker and agents service with the host settings the
Android app needs, verifies the agents host header behavior, configures
`adb reverse` for physical devices, and optionally installs/launches the app.
Keep that terminal open during the demo; `Ctrl-C` stops the services.

Manual path from the monorepo root:

```bash
moon api:dev
moon agents:serve
```

If the emulator or a USB device cannot reach the API worker started by `moon`, start the services directly with explicit host binding:

```bash
pnpm --filter @yoshi/api run dev -- --host 0.0.0.0 --port 8787
cd apps/agents
ALLOWED_HOST=127.0.0.1 PYTHONPATH=src uv run uvicorn main:app --host 0.0.0.0 --port 8002
```

The Android app chooses local development URLs at runtime:

- Emulator API worker: `http://10.0.2.2:8787`
- Emulator agents service: `http://10.0.2.2:8002`
- Physical device API worker: `http://127.0.0.1:8787`
- Physical device agents service: `http://127.0.0.1:8002`

`10.0.2.2` is the Android emulator alias for the host machine. If the emulator cannot reach the API worker, start the API worker with `--host 0.0.0.0`. If the agents service rejects the host header, set `ALLOWED_HOST=10.0.2.2` for emulator testing or `ALLOWED_HOST=127.0.0.1` for physical-device testing with `adb reverse`. The demo script handles this automatically.

For a physical device over USB, enable USB debugging and reverse the local ports before launching:

```bash
adb reverse tcp:8787 tcp:8787
adb reverse tcp:8002 tcp:8002
```

On a physical device, `127.0.0.1` means the phone itself; `adb reverse` tunnels those phone-local ports back to the services on your Mac.

## Run The Android App

Open the Android project folder directly in Android Studio:

```text
Yoshi-chat
```

Or install and launch from the command line:

```bash
ANDROID_HOME=/Users/swig/Library/Android/sdk ./gradlew :app:installAndLaunchDebug
```

Run tests:

```bash
ANDROID_HOME=/Users/swig/Library/Android/sdk ./gradlew :app:assembleDebug :app:testDebugUnitTest --no-daemon
```

## Auth Assumptions

This app uses local development auth only:

1. `POST /support/test-login` on the API worker.
2. `GET /api/auth/token` using the dev login cookie.
3. The returned JWT is sent to the agents service as `Authorization: Bearer <jwt>`.

No real auth UI is implemented yet. The app stores the dev session cookie locally and uses it to fetch a fresh JWT on launch. This is intentional: `/support/test-login` creates a random dev user on each call, and creating a new user on every app start makes previously saved thread IDs fail with `403 Thread not owned by user`. Persisting only the dev cookie keeps force-close/relaunch attached to the same backend user while still avoiding persistent JWT storage.

## Confirmed Transport Contract

Thread setup:

- `POST /api/v1/chat/thread/init`
- `GET /api/v1/chat/thread/{thread_id}/state`
- `GET /api/v1/chat/thread/{thread_id}/suggestions?context_thread_id={thread_id}`

Thread metadata:

- `GET /threads?status=active`
- `PATCH /threads/{thread_id}`
- These API-worker routes use the local Better Auth session cookie from `POST /support/test-login`.

Chat transport:

- `POST /api/v1/transport/chat`
- `Content-Type: application/json`
- `Authorization: Bearer <jwt>`
- `x-user-timezone: America/New_York`
- Response: `text/event-stream`

Each SSE frame uses `data: ...` lines and a blank line frame boundary. The final frame is:

```text
data: [DONE]
```

The backend streams `update-state` envelopes:

```json
{
  "type": "update-state",
  "operations": [
    {
      "type": "set",
      "path": ["messages", "3"],
      "value": {
        "type": "ai",
        "content": [
          { "type": "text", "text": "Try a 24-hour pause...", "index": 0 }
        ]
      }
    }
  ]
}
```

The Android request must include discriminator fields for backend validation:

```json
{
  "commands": [
    {
      "type": "add-message",
      "message": {
        "role": "user",
        "parts": [{ "type": "text", "text": "hello" }]
      }
    }
  ],
  "state": {}
}
```

## Architecture Summary

- `ChatViewModel` owns local dev auth, thread init, hydration, optimistic send state, transport collection, and final reconciliation.
- `ChatTransportClient` owns OkHttp SSE transport and emits domain `TransportEvent`s.
- `SseFrameParser` reads line-oriented SSE data and emits complete payloads on blank-line boundaries.
- `TransportStateStore` holds the evolving canonical transport state and applies the observed operation subset.
- `ThreadSessionRepository` stores the last selected thread ID locally for app relaunch.
- `ThreadMetadataApi` talks to the API worker `/threads` routes so the sidebar can show human-readable thread titles, not raw UUIDs.
- The Android app mirrors the web title behavior by deriving a new thread title from the first user message and writing it with `PATCH /threads/{thread_id}`.
- Compose UI renders derived domain messages from `ChatUiState`, including basic inline Markdown emphasis, suggested prompt chips, and a placeholder card for pending human-in-the-loop tool interruptions.
- Debug Inspector is hidden behind a long-press on the top-left thread menu icon and can copy a concise debug summary.

## Why State Operations Instead Of Text Deltas

The backend does not stream a naive assistant text delta protocol. It streams canonical state mutations. Assistant text is progressively built by repeated `update-state` operations that replace message nodes or content arrays. The Android client therefore applies backend operations to an in-memory canonical state store and derives UI from that state. This avoids inventing a second client-side transcript model that can drift from the backend checkpoint, and it makes `[DONE]` reconciliation a straightforward hydrate of the final thread state.

## Demo Checklist

1. Launch app.
2. Wait for the hydrated greeting.
3. Send: `What is one simple budgeting tip?`
4. Observe the streamed assistant response.
5. Tap `Threads` to verify the current thread appears with a readable title; rename it if desired.
6. Long-press the top-left thread menu icon to open Debug Inspector.
7. Tap `Copy`.
8. Verify the copied summary includes backend URL, thread ID, hydration status, stream status, latest SSE event, and message count.

## Debugging

Useful Android Logcat filter:

```bash
adb logcat -v time | rg "YoshiAuth|YoshiChatApi|YoshiChatViewModel|YoshiTransport|TransportStateStore|YoshiAttachments"
```

The logs include request IDs, response codes, transport request size, SSE event metadata, operation paths, and state counts. They intentionally do not log JWTs or other secrets.
The in-app Debug Inspector keeps the latest raw SSE payload for inspection and copyable summaries.

## Known Limitations

- Local dev auth only.
- Thread management uses API-worker title metadata, but full archive/delete flows are not implemented in Android yet.
- No real auth screen.
- Basic inline Markdown emphasis such as `**Goal**` is rendered; full Markdown blocks, lists, links, and tables are not implemented yet.
- Basic attachments are implemented for PDF, common image formats, and small text files; richer previews and attachment management are not implemented yet. If image uploads return `HTTP 502`, the failure is coming from the API worker's upstream file upload path. The Android client logs a per-upload request ID with the `YoshiAttachments` tag so it can be matched against backend logs. A likely backend fix would be to align the upstream file-upload purpose with the attachment type, for example using the vision file purpose for images while keeping document uploads on the existing file/data purpose.
- HITL tool interruptions render as placeholders only; completing tool results is intentionally not implemented.
- No Room persistence.
- No dependency injection.

## Next Steps

- Add production auth.
- Add backend-backed thread list if/when an endpoint exists.
- Add full Markdown rendering if richer assistant formatting becomes required.
- Complete HITL tool result submission.
- Replace local development URL detection with build variants or local properties before production use.
