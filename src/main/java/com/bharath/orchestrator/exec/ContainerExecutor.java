package com.bharath.orchestrator.exec;

import com.bharath.orchestrator.model.Job;
import com.bharath.orchestrator.model.RunStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Provisions a fresh, resource-limited container per job by shelling out to the
 * container engine (podman/docker). Each job gets its own container -> workload
 * isolation. The container is force-removed afterwards so nothing lingers.
 */
@Component
public class ContainerExecutor {

    private static final Logger log = LoggerFactory.getLogger(ContainerExecutor.class);
    private static final int LOG_TAIL_LINES = 40;

    private final String engine;
    private final String defaultCpus;
    private final String defaultMemory;

    public ContainerExecutor(
            @Value("${orchestrator.engine:podman}") String engine,
            @Value("${orchestrator.default-cpus:1.0}") String defaultCpus,
            @Value("${orchestrator.default-memory:256m}") String defaultMemory) {
        this.engine = engine;
        this.defaultCpus = defaultCpus;
        this.defaultMemory = defaultMemory;
    }

    /** Run one attempt of the job in its own container and wait for it to finish. */
    public ExecResult run(Job job, int attempt) {
        String containerName = "job-" + job.getId() + "-a" + attempt;
        String cpus = job.getCpus() != null && !job.getCpus().isBlank() ? job.getCpus() : defaultCpus;
        String memory = job.getMemory() != null && !job.getMemory().isBlank() ? job.getMemory() : defaultMemory;

        // Clean up any stale container with the same name from a prior crash.
        remove(containerName);

        List<String> cmd = new ArrayList<>();
        cmd.add(engine);
        cmd.add("run");
        cmd.add("--name");
        cmd.add(containerName);
        cmd.add("--rm");                 // auto-remove on exit -> no leftovers
        cmd.add("--cpus");
        cmd.add(cpus);
        cmd.add("--memory");
        cmd.add(memory);
        cmd.add("--label");
        cmd.add("orchestrator=job-orchestrator");
        cmd.add(job.getImage());
        // Optional command, split on whitespace.
        if (job.getCommand() != null && !job.getCommand().isBlank()) {
            for (String tok : job.getCommand().trim().split("\\s+")) {
                cmd.add(tok);
            }
        }

        log.info("Launching container {} for job {} (attempt {}): {}",
                containerName, job.getId(), attempt, String.join(" ", cmd));

        Process proc;
        try {
            proc = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start();
        } catch (Exception e) {
            log.error("Failed to launch container for job {}: {}", job.getId(), e.toString());
            return new ExecResult(RunStatus.ERROR, null, containerName,
                    "Failed to launch container: " + e.getMessage());
        }

        Deque<String> tail = new ArrayDeque<>();
        Thread reader = startLogReader(proc, tail);

        boolean finished;
        try {
            finished = proc.waitFor(job.getTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            proc.destroyForcibly();
            remove(containerName);
            return new ExecResult(RunStatus.ERROR, null, containerName, "Interrupted");
        }

        if (!finished) {
            log.warn("Job {} attempt {} timed out after {}s; killing container",
                    job.getId(), attempt, job.getTimeoutSeconds());
            proc.destroyForcibly();
            stop(containerName);
            remove(containerName);
            joinQuietly(reader);
            return new ExecResult(RunStatus.TIMED_OUT, null, containerName,
                    joinTail(tail) + "\n[orchestrator] killed after timeout");
        }

        joinQuietly(reader);
        int exit = proc.exitValue();
        // With --rm the container is already gone; make sure of it anyway.
        remove(containerName);

        RunStatus status = exit == 0 ? RunStatus.SUCCEEDED : RunStatus.FAILED;
        return new ExecResult(status, exit, containerName, joinTail(tail));
    }

    private Thread startLogReader(Process proc, Deque<String> tail) {
        Thread t = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    synchronized (tail) {
                        tail.addLast(line);
                        while (tail.size() > LOG_TAIL_LINES) {
                            tail.removeFirst();
                        }
                    }
                }
            } catch (Exception ignored) {
                // process ended / stream closed
            }
        }, "log-reader");
        t.setDaemon(true);
        t.start();
        return t;
    }

    private String joinTail(Deque<String> tail) {
        synchronized (tail) {
            return String.join("\n", tail);
        }
    }

    private void joinQuietly(Thread t) {
        try {
            t.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void stop(String name) {
        runQuietly(engine, "stop", "-t", "1", name);
    }

    private void remove(String name) {
        runQuietly(engine, "rm", "-f", name);
    }

    private void runQuietly(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            p.waitFor(15, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // best effort
        }
    }
}
