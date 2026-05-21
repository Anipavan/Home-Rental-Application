package com.spa.home_rental_application.document_service.ocr;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async executor for OCR work. Without this Spring uses
 * {@code SimpleAsyncTaskExecutor} which spawns an unbounded thread per
 * call — fine for low traffic, dangerous if the upload endpoint gets
 * burst-loaded.
 *
 * <p>Pool sizing:
 * <ul>
 *   <li><b>core=2</b> — always-on threads ready to pick up OCR tasks
 *       without spin-up latency on the common case (single upload).</li>
 *   <li><b>max=4</b> — caps the parallelism. Sandbox.co.in's free tier
 *       has a per-second rate limit; 4 in-flight calls keeps us under
 *       it comfortably.</li>
 *   <li><b>queue=100</b> — buffers bursts. After 100 queued tasks new
 *       uploads return successfully but auto-OCR is skipped (logged as
 *       a warning); the admin can re-drive via /extract.</li>
 * </ul>
 */
@Configuration
@EnableAsync
public class AsyncOcrConfig {

    @Bean(name = "ocrExecutor")
    public Executor ocrExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(4);
        exec.setQueueCapacity(100);
        exec.setThreadNamePrefix("ocr-");
        // CallerRuns prevents OOM if the queue fills — the upload thread
        // does the OCR inline as a backpressure release valve. Slow under
        // load but never drops work.
        exec.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        exec.initialize();
        return exec;
    }
}
