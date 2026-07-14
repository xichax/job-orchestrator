package com.bharath.orchestrator.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** Payload to submit a new job. */
public class SubmitJobRequest {

    @NotBlank
    public String name;

    @NotBlank
    public String image;

    public String command;
    public String cpus;
    public String memory;

    @Min(0)
    public Integer maxRetries;

    @Min(1)
    public Integer timeoutSeconds;
}
