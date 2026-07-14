package com.bharath.orchestrator.repo;

import com.bharath.orchestrator.model.JobRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobRunRepository extends JpaRepository<JobRun, Long> {
    List<JobRun> findByJobIdOrderByAttemptAsc(Long jobId);
}
