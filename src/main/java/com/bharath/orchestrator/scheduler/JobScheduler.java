package com.bharath.orchestrator.scheduler;

import com.bharath.orchestrator.exec.ContainerExecutor;
import com.bharath.orchestrator.exec.ExecResult;
import com.bharath.orchestrator.model.Job;
import com.bharath.orchestrator.model.JobRun;
import com.bharath.orchestrator.model.JobStatus;
import com.bharath.orchestrator.model.RunStatus;
import com.bharath.orchestrator.repo.JobRepository;
import com.bharath.orchestrator.repo.JobRunRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Polls the job queue and dispatches eligible jobs to the container executor,
 * never exceeding max-concurrency. Handles retry with exponential backoff.
 */
@Component
public class JobScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobScheduler.class);

    private final JobRepository jobs;
    private final JobRunRepository runs;
    private final ContainerExecutor executor;
    private final int maxConcurrency;
    private final long backoffBaseSeconds;

    private final ExecutorService pool;
    /** Ids of jobs currently executing, so we don't double-dispatch. */
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    public JobScheduler(JobRepository jobs,
                        JobRunRepository runs,
                        ContainerExecutor executor,
                        @Value("${orchestrator.max-concurrency:3}") int maxConcurrency,
                        @Value("${orchestrator.backoff-base-seconds:5}") long backoffBaseSeconds) {
        this.jobs = jobs;
        this.runs = runs;
        this.executor = executor;
        this.maxConcurrency = maxConcurrency;
        this.backoffBaseSeconds = backoffBaseSeconds;
        this.pool = Executors.newFixedThreadPool(maxConcurrency, r -> {
            Thread t = new Thread(r, "job-worker");
            t.setDaemon(true);
            return t;
        });
    }

    @Scheduled(fixedDelayString = "${orchestrator.poll-interval-ms:1500}")
    public void tick() {
        int free = maxConcurrency - inFlight.size();
        if (free <= 0) {
            return;
        }

        List<Job> eligible = jobs.findByStatusInAndNotBeforeLessThanEqualOrderByCreatedAtAsc(
                List.of(JobStatus.QUEUED, JobStatus.RETRYING), Instant.now());

        for (Job job : eligible) {
            if (free <= 0) {
                break;
            }
            if (!inFlight.add(job.getId())) {
                continue; // already running
            }
            free--;
            job.setStatus(JobStatus.RUNNING);
            jobs.save(job);
            pool.submit(() -> execute(job.getId()));
        }
    }

    private void execute(Long jobId) {
        try {
            Job job = jobs.findById(jobId).orElse(null);
            if (job == null || job.getStatus() == JobStatus.CANCELLED) {
                return;
            }

            int attempt = job.getAttempts() + 1;

            JobRun runRow = new JobRun();
            runRow.setJobId(job.getId());
            runRow.setAttempt(attempt);
            runRow.setStatus(RunStatus.RUNNING);
            runRow = runs.save(runRow);

            ExecResult result = executor.run(job, attempt);

            runRow.setStatus(result.status());
            runRow.setExitCode(result.exitCode());
            runRow.setContainerId(result.containerName());
            runRow.setLogsTail(result.logsTail());
            runRow.setFinishedAt(Instant.now());
            runs.save(runRow);

            // Re-load in case the job was cancelled mid-run.
            job = jobs.findById(jobId).orElse(job);
            job.setAttempts(attempt);

            if (job.getStatus() == JobStatus.CANCELLED) {
                jobs.save(job);
                return;
            }

            if (result.status() == RunStatus.SUCCEEDED) {
                job.setStatus(JobStatus.SUCCEEDED);
                log.info("Job {} succeeded on attempt {}", jobId, attempt);
            } else if (attempt > job.getMaxRetries()) {
                job.setStatus(JobStatus.FAILED);
                log.warn("Job {} failed permanently after {} attempt(s) [{}]",
                        jobId, attempt, result.status());
            } else {
                long backoff = backoffBaseSeconds * (long) Math.pow(2, attempt - 1);
                job.setStatus(JobStatus.RETRYING);
                job.setNotBefore(Instant.now().plus(backoff, ChronoUnit.SECONDS));
                log.info("Job {} attempt {} failed [{}]; retrying in {}s",
                        jobId, attempt, result.status(), backoff);
            }
            jobs.save(job);
        } catch (Exception e) {
            log.error("Scheduler error executing job {}: {}", jobId, e.toString(), e);
        } finally {
            inFlight.remove(jobId);
        }
    }

    @PreDestroy
    public void shutdown() {
        pool.shutdownNow();
    }
}
