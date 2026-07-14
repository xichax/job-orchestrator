package com.bharath.orchestrator.repo;

import com.bharath.orchestrator.model.Job;
import com.bharath.orchestrator.model.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface JobRepository extends JpaRepository<Job, Long> {

    List<Job> findByStatusOrderByCreatedAtAsc(JobStatus status);

    long countByStatus(JobStatus status);

    List<Job> findTop200ByOrderByCreatedAtDesc();

    /** Jobs eligible to be dispatched now (queued/retrying and past their backoff). */
    List<Job> findByStatusInAndNotBeforeLessThanEqualOrderByCreatedAtAsc(
            List<JobStatus> statuses, Instant now);
}
