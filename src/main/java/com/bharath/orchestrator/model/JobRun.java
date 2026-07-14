package com.bharath.orchestrator.model;

import jakarta.persistence.*;
import java.time.Instant;

/** One container execution attempt of a Job. */
@Entity
@Table(name = "job_runs")
public class JobRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long jobId;

    /** 1-based attempt number for this job. */
    private int attempt;

    /** The container name/id we assigned. */
    private String containerId;

    @Enumerated(EnumType.STRING)
    private RunStatus status = RunStatus.RUNNING;

    private Integer exitCode;

    /** Tail of the container's stdout/stderr, for the dashboard. */
    @Column(length = 8000)
    private String logsTail;

    private Instant startedAt = Instant.now();
    private Instant finishedAt;

    // --- getters / setters ---
    public Long getId() { return id; }
    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }
    public int getAttempt() { return attempt; }
    public void setAttempt(int attempt) { this.attempt = attempt; }
    public String getContainerId() { return containerId; }
    public void setContainerId(String containerId) { this.containerId = containerId; }
    public RunStatus getStatus() { return status; }
    public void setStatus(RunStatus status) { this.status = status; }
    public Integer getExitCode() { return exitCode; }
    public void setExitCode(Integer exitCode) { this.exitCode = exitCode; }
    public String getLogsTail() { return logsTail; }
    public void setLogsTail(String logsTail) { this.logsTail = logsTail; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
