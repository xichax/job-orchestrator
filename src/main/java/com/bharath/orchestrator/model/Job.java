package com.bharath.orchestrator.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A unit of work. Each job runs as one or more container attempts (JobRun).
 * The command is stored as a single string and split on whitespace at launch;
 * for anything fancier the image's entrypoint should handle it.
 */
@Entity
@Table(name = "jobs")
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** Container image, e.g. docker.io/library/alpine:3.20 */
    @Column(nullable = false)
    private String image;

    /** Command to run inside the container (may be blank to use the image default). */
    @Column(length = 2000)
    private String command;

    private String cpus;
    private String memory;

    private int maxRetries = 2;
    private int timeoutSeconds = 120;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status = JobStatus.QUEUED;

    /** Number of attempts already made. */
    private int attempts = 0;

    /** When the job is eligible to run next (used for backoff). */
    private Instant notBefore = Instant.now();

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    // --- getters / setters ---
    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }
    public String getCpus() { return cpus; }
    public void setCpus(String cpus) { this.cpus = cpus; }
    public String getMemory() { return memory; }
    public void setMemory(String memory) { this.memory = memory; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public Instant getNotBefore() { return notBefore; }
    public void setNotBefore(Instant notBefore) { this.notBefore = notBefore; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
