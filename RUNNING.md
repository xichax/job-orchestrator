# Running the Job Orchestration Platform locally

A step-by-step guide to build and run the platform on your own machine.

**Important:** unlike a fully-containerized app, the orchestrator itself runs on your
**host JVM** — because its whole job is to shell out to `podman` and provision a fresh
container for every job you submit. So you need **both** a JDK (to run the app) and
Podman (to launch job containers). You do **not** need Gradle — the jar is compiled
inside a Gradle container.

---

## 1. Prerequisites

| Tool | Why | Check |
|---|---|---|
| **JDK 17+** on PATH | runs the orchestrator app | `java -version` |
| **Podman** (or Docker) on PATH | compiles the jar + launches job containers | `podman --version` |
| Podman machine running | Linux VM that hosts the containers | `podman machine list` |
| Port **8090** free | dashboard + REST API | — |

Start the Podman machine if it isn't already:

```powershell
podman machine start
podman machine list        # confirm "Currently running"
```

> To use Docker instead of Podman, set the engine (see §7): `ORCHESTRATOR_ENGINE=docker`.

---

## 2. Get the code

```powershell
git clone https://github.com/bharath-thappeta/job-orchestrator.git
cd job-orchestrator
```

---

## 3. Build & run

The `run.ps1` helper does both steps:

```powershell
./run.ps1 up
```

What that does:
1. **Build** — runs `gradle bootJar` inside a `gradle:8.10-jdk21` container (mounts the
   project in), producing `build/libs/job-orchestrator-0.1.0.jar`. No local Gradle needed.
2. **Run** — starts the jar on your host JVM at **http://localhost:8090**.

Other commands:

```powershell
./run.ps1 build    # only compile the jar
./run.ps1 run      # only run an already-built jar
```

Stop the app with **Ctrl+C** in that terminal.

### Manual equivalent (if you prefer)

```powershell
# build the jar in a container
podman run --rm -v "${PWD}:/work" -w /work docker.io/library/gradle:8.10-jdk21 gradle --no-daemon bootJar
# run it on the host
java -jar build/libs/job-orchestrator-0.1.0.jar
```

---

## 4. Try it out

Open **http://localhost:8090**. Use the **Submit a job** form — there are one-click
presets:

| Preset | What it demonstrates |
|---|---|
| **Success** | job runs in its own container and exits 0 → SUCCEEDED |
| **Always fails** | exits 1 every time → retried with backoff → FAILED |
| **Slow (timeout)** | sleeps longer than the timeout → killed → TIMED_OUT → retried |
| **CPU work** | a multi-step job you can watch progress live |

Click any job row to expand its **attempts** and see the captured container logs.

### Or via the API

```bash
# submit a job
curl -X POST http://localhost:8090/api/jobs -H "Content-Type: application/json" -d '{
  "name": "hello",
  "image": "docker.io/library/alpine:3.20",
  "command": "echo hello from an isolated container",
  "cpus": "0.5", "memory": "128m",
  "maxRetries": 2, "timeoutSeconds": 60
}'

curl http://localhost:8090/api/jobs          # list jobs
curl http://localhost:8090/api/jobs/1/runs   # attempts for job 1
curl http://localhost:8090/api/stats         # counts by status
```

### Watch the real containers being provisioned

While a job runs, in another terminal:

```powershell
podman ps -a --filter "label=orchestrator=job-orchestrator"
```

You'll see containers named `job-<id>-a<attempt>` appear per attempt and get removed
when they finish — that's the dynamic provisioning + isolation in action.

---

## 5. First-run notes & timing

- The **first build** downloads the Gradle image and dependencies (a few minutes).
  After that it's cached.
- The **first job** pulls its container image (e.g. `alpine`) — that attempt takes a bit
  longer; later jobs on the same image are instant.
- State is persisted to an embedded **H2 file DB** under `./data/`, so your jobs and
  their history survive an app restart. Delete `./data/` to start fresh.

---

## 6. Troubleshooting

| Symptom | Cause & fix |
|---|---|
| `java: command not found` | No JDK on PATH. Install a JDK 17+ and reopen the terminal. |
| Build fails pulling `gradle:8.10-jdk21` | Registry blocked. Ensure Docker Hub is reachable, or change the image in `run.ps1` to an allowed mirror. |
| Jobs stay **QUEUED**, never run | Podman machine not running (`podman machine start`) or `podman` not on PATH for the app process. |
| Every job **ERROR** with launch failure | The engine binary can't be found or the image can't be pulled. Test manually: `podman run --rm docker.io/library/alpine:3.20 echo ok`. |
| `port is already allocated` / 8090 in use | Another process owns 8090. Stop it or change `server.port` in `application.yml`. |
| Feature not supported H2 error on boot | Only occurs with an incompatible JDBC URL; this repo uses `jdbc:h2:file:./data/orchestrator`, which is correct. Delete `./data/` if corrupted. |

---

## 7. Configuration reference

Set via `application.yml` or environment variables (env overrides file):

| Key / env | Default | Meaning |
|---|---|---|
| `orchestrator.engine` / `ORCHESTRATOR_ENGINE` | `podman` | container engine binary (`podman` or `docker`) |
| `orchestrator.max-concurrency` / `ORCHESTRATOR_MAX_CONCURRENCY` | `3` | max jobs running at once |
| `orchestrator.poll-interval-ms` | `1500` | scheduler poll cadence |
| `orchestrator.default-cpus` | `1.0` | default CPU limit per job |
| `orchestrator.default-memory` | `256m` | default memory limit per job |
| `orchestrator.backoff-base-seconds` | `5` | retry backoff base; attempt _n_ waits `base · 2^(n-1)` |
| `server.port` | `8090` | HTTP port |

Example — run against Docker with more concurrency:

```powershell
$env:ORCHESTRATOR_ENGINE="docker"; $env:ORCHESTRATOR_MAX_CONCURRENCY="5"
java -jar build/libs/job-orchestrator-0.1.0.jar
```

---

## 8. Stopping

- **Ctrl+C** in the terminal running the app.
- Job containers are auto-removed (`--rm`); to sweep any stragglers:
  `podman rm -f $(podman ps -aq --filter "label=orchestrator=job-orchestrator")`
- Optional: `podman machine stop` to free the VM.
