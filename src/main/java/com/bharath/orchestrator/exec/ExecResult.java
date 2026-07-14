package com.bharath.orchestrator.exec;

import com.bharath.orchestrator.model.RunStatus;

/** Result of running one container attempt. */
public record ExecResult(
        RunStatus status,
        Integer exitCode,
        String containerName,
        String logsTail
) {}
