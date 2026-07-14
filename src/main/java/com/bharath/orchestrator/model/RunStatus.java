package com.bharath.orchestrator.model;

/** Outcome of a single container attempt. */
public enum RunStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
    TIMED_OUT,
    ERROR   // could not even launch the container
}
