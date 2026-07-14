package com.bharath.orchestrator.api;

import com.bharath.orchestrator.model.Job;
import com.bharath.orchestrator.model.JobRun;
import com.bharath.orchestrator.model.JobStatus;
import com.bharath.orchestrator.repo.JobRepository;
import com.bharath.orchestrator.repo.JobRunRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class JobController {

    private final JobRepository jobs;
    private final JobRunRepository runs;

    public JobController(JobRepository jobs, JobRunRepository runs) {
        this.jobs = jobs;
        this.runs = runs;
    }

    @PostMapping("/jobs")
    public Job submit(@Valid @RequestBody SubmitJobRequest req) {
        Job job = new Job();
        job.setName(req.name);
        job.setImage(req.image);
        job.setCommand(req.command);
        job.setCpus(req.cpus);
        job.setMemory(req.memory);
        if (req.maxRetries != null) job.setMaxRetries(req.maxRetries);
        if (req.timeoutSeconds != null) job.setTimeoutSeconds(req.timeoutSeconds);
        job.setStatus(JobStatus.QUEUED);
        return jobs.save(job);
    }

    @GetMapping("/jobs")
    public List<Job> list() {
        return jobs.findTop200ByOrderByCreatedAtDesc();
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<Job> get(@PathVariable Long id) {
        return jobs.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/jobs/{id}/runs")
    public List<JobRun> runs(@PathVariable Long id) {
        return runs.findByJobIdOrderByAttemptAsc(id);
    }

    @PostMapping("/jobs/{id}/cancel")
    public ResponseEntity<Job> cancel(@PathVariable Long id) {
        return jobs.findById(id).map(job -> {
            if (job.getStatus() == JobStatus.QUEUED || job.getStatus() == JobStatus.RETRYING) {
                job.setStatus(JobStatus.CANCELLED);
                jobs.save(job);
            }
            return ResponseEntity.ok(job);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> out = new HashMap<>();
        for (JobStatus s : JobStatus.values()) {
            out.put(s.name().toLowerCase(), jobs.countByStatus(s));
        }
        out.put("total", jobs.count());
        return out;
    }
}
