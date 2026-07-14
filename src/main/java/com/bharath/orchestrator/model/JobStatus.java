package com.bharath.orchestrator.model;

/** Lifecycle of a submitted job. */
public enum JobStatus {
    QUEUED,      // waiting for a scheduler slot
    RUNNING,     // a container is currently executing an attempt
    RETRYING,    // last attempt failed, waiting for backoff before requeue
    SUCCEEDED,   // an attempt exited 0
    FAILED,      // exhausted all retries
    CANCELLED    // cancelled by user
}
