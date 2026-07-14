# Job Orchestration Platform

A lightweight platform that runs arbitrary **jobs**, each in its own **dynamically
provisioned, resource-limited container** (workload isolation). A scheduler dispatches
queued jobs up to a concurrency cap, **retries failures with exponential backoff**, and
persists every job and attempt. Live dashboard + REST API.

---

## Architecture

```
   ┌──────────────────────────────┐
   │  Dashboard + REST API :8090  │   submit / list / cancel
   └───────────────┬──────────────┘
                   │  persist
                   ▼
          ┌──────────────────┐        ┌───────────────────────────┐
          │   H2 (file DB)   │◀──────▶│        Scheduler          │
          │  jobs · runs     │        │  poll queue · concurrency │
          └──────────────────┘        │  cap · retry w/ backoff   │
                                       └─────────────┬─────────────┘
                                                     │  dispatch (1 per slot)
                                                     ▼
                                       ┌───────────────────────────┐
                                       │     ContainerExecutor     │
                                       │  podman run --rm          │
                                       │  --cpus --memory <image>  │
                                       └─────────────┬─────────────┘
                                                     │  one container per job
                                 ┌───────────┬───────┴───────┬───────────┐
                                 ▼           ▼               ▼           ▼
                             [job-1]     [job-2]         [job-3]      …isolated
```

- **Scheduler** — polls the queue every ~1.5s, never runs more than
  `max-concurrency` jobs at once, and moves jobs through
  `QUEUED → RUNNING → SUCCEEDED / RETRYING → FAILED`.
- **ContainerExecutor** — provisions a fresh container per attempt
  (`podman run --rm --cpus … --memory … <image> <command>`), streams its logs,
  enforces a per-job **timeout**, captures the exit code, and removes the container.
  One container per job ⇒ **workload isolation**.
- **Retry/backoff** — a non-zero exit or timeout requeues the job with
  exponential backoff (`base · 2^(attempt-1)`) until `maxRetries` is exhausted.
- **Persistence** — jobs and every attempt are stored in an embedded **H2 file DB**,
  so state survives restarts.

## Tech
Java 21 · Spring Boot 3 · Spring Data JPA · H2 · Podman (or Docker)

---

## Prerequisites
- A **JDK** on PATH (to run the app).
- **Podman** (or Docker) on PATH, with a running machine — used both to compile the
  jar and to launch job containers.

## Run it

```powershell
./run.ps1 up       # compiles the jar in a gradle container, then runs on the host JVM
```

Then open **http://localhost:8090** and submit a job (there are one-click presets for
success, always-fails, timeout, and CPU-work). Click a job row to expand its attempts
and see the container logs.

> The orchestrator runs on the **host JVM** on purpose: it needs to call `podman` to
> provision sibling containers. Use Docker instead by setting `orchestrator.engine=docker`
> (env `ORCHESTRATOR_ENGINE=docker`).

## REST API
| Endpoint | Description |
|---|---|
| `POST /api/jobs` | submit a job (`name`, `image`, `command`, `cpus`, `memory`, `maxRetries`, `timeoutSeconds`) |
| `GET /api/jobs` | list recent jobs |
| `GET /api/jobs/{id}` | one job |
| `GET /api/jobs/{id}/runs` | all attempts (with log tails) |
| `POST /api/jobs/{id}/cancel` | cancel a queued/retrying job |
| `GET /api/stats` | counts by status |

Example:
```bash
curl -X POST http://localhost:8090/api/jobs -H "Content-Type: application/json" -d '{
  "name": "hello",
  "image": "docker.io/library/alpine:3.20",
  "command": "echo hello from an isolated container",
  "cpus": "0.5", "memory": "128m",
  "maxRetries": 2, "timeoutSeconds": 60
}'
```

## Configuration (`application.yml` / env)
| Key | Default | Meaning |
|---|---|---|
| `orchestrator.engine` | `podman` | container engine binary |
| `orchestrator.max-concurrency` | `3` | max jobs running at once |
| `orchestrator.poll-interval-ms` | `1500` | scheduler poll cadence |
| `orchestrator.default-cpus` | `1.0` | default CPU limit per job |
| `orchestrator.default-memory` | `256m` | default memory limit per job |
| `orchestrator.backoff-base-seconds` | `5` | retry backoff base |
