package com.factcheck.collector.integration.tasks;

import com.factcheck.collector.dto.IngestionTaskRequest;

public interface TaskPublisher {
    void enqueueIngestionTask(IngestionTaskRequest taskRequest);
}