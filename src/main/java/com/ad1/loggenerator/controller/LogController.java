package com.ad1.loggenerator.controller;

import org.json.simple.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.web.bind.annotation.*;

import com.ad1.loggenerator.exception.JobNotFoundException;
import com.ad1.loggenerator.model.AllJobMetrics;
import com.ad1.loggenerator.model.BatchJobMetrics;
import com.ad1.loggenerator.model.BatchTracker;
import com.ad1.loggenerator.model.ContinueMessage;
import com.ad1.loggenerator.model.SelectionModel;
import com.ad1.loggenerator.model.StreamJobMetrics;
import com.ad1.loggenerator.model.StreamTracker;
import com.ad1.loggenerator.service.implementation.BatchService;
import com.ad1.loggenerator.service.implementation.BatchServiceTracker;
import com.ad1.loggenerator.service.implementation.StatisticsUtilitiesService;
import com.ad1.loggenerator.service.implementation.StreamServiceTracker;
import com.ad1.loggenerator.service.implementation.StreamingService;

import lombok.AllArgsConstructor;

/**
 * Receives user requests and returns responses via REST API
 */
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/generate")
public class LogController {

    private final BatchService batchService;
    private final BatchServiceTracker batchServiceTracker;
    private final StreamingService streamingService;
    private final StreamServiceTracker streamServiceTracker;
    private final StatisticsUtilitiesService statisticsUtilitiesService;

    // general request for generating batch files or streaming
    @PostMapping("/batch")
    public ResponseEntity<String> generateBatchRequest(
        @RequestBody SelectionModel selectionModel) throws InterruptedException {

        if (selectionModel.getMode().equals("Batch")) {
            String jobId = batchService.generateJobId();
            selectionModel.setJobId(jobId);
            BatchTracker batchJobTracker = 
                new BatchTracker(
                    jobId, 
                    0, 
                    selectionModel.getBatchSettings().getNumberOfLogs(),
                    System.currentTimeMillis() / 1000,
                    -1
                    );
            batchService.batchMode(selectionModel, batchJobTracker);
            batchServiceTracker.addNewJob(batchJobTracker);
            if (batchServiceTracker.getActiveJobsListSize() == 1) {
                batchServiceTracker.sendBatchData();
            }
            return new ResponseEntity<>(jobId, HttpStatus.OK);
        } else {
            return new ResponseEntity<>("Invalid Request. Try again", HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/stream")
    public ResponseEntity<String> generateStreamRequest(
        @RequestBody SelectionModel selectionModel) throws InterruptedException {

        if (selectionModel.getMode().equals("Stream")) {
            String jobId = streamingService.generateJobId();
            selectionModel.setJobId(jobId);
            StreamTracker streamJobTracker = new StreamTracker(
                jobId, 
                0, 
                System.currentTimeMillis() / 1000,
                true,
                System.currentTimeMillis() / 1000,
                -1
                );
            streamingService.streamMode(selectionModel, streamJobTracker);
            streamServiceTracker.addNewJob(streamJobTracker);
            if (streamServiceTracker.getActiveJobsListSize() == 1) {
                streamServiceTracker.checkLastPings();
            }
            return new ResponseEntity<>(jobId, HttpStatus.OK);
        } else {
            return new ResponseEntity<>("Invalid Request. Try again", HttpStatus.BAD_REQUEST);
        }
    }

    // stop streaming request
    @PostMapping("/stream/stop/{jobId}")
    public ResponseEntity<String> stopRequest(@PathVariable String jobId) {
        boolean result = streamServiceTracker.stopStreamJob(jobId);

        if (result) {
            return new ResponseEntity<>("Streaming has stopped.", HttpStatus.OK);
        } else {
            return new ResponseEntity<>("No stream job with that id.", HttpStatus.BAD_REQUEST);
        }
    }

    // continue streaming request via HTTP request
    @PostMapping("/stream/continue/{jobId}")
    public ResponseEntity<String> continueRequest(@PathVariable String jobId) {
        boolean result = streamServiceTracker.continueStreamJob(jobId);

        if (result) {
            return new ResponseEntity<>("Streaming will continue.", HttpStatus.OK);
        } else {
            return new ResponseEntity<>("No stream job with that id.", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Method to continue a streaming job via socket
     * @param jobId the id of the streaming job to continue
     * @return ContinueMessage with success or failure message
     */
    @MessageMapping("/stream/continue/{jobId}")
    public ContinueMessage continueRequestSocket(@DestinationVariable String jobId) {
        boolean result = streamServiceTracker.continueStreamJob(jobId);

        if (result) {
            return new ContinueMessage(jobId, "Streaming will continue.");
        } else {
            return new ContinueMessage(jobId, "No stream job with that id.");
        }
    }
    
    // test for streaming to addresss
    @PostMapping("stream/toAddress")
    public ResponseEntity<String> addressStream(@RequestBody JSONObject streamData){
        System.out.println(streamData);
        return new ResponseEntity<>("Data successfully received.", HttpStatus.OK);
    }

    /**
     * Method to get metrics for a batch job
     * @return
     */
    @GetMapping("/stats/batch/{jobId}")
    public BatchJobMetrics getBatchJobMetrics(@PathVariable String jobId) {
        BatchJobMetrics batchJobMetrics = statisticsUtilitiesService.generateBatchJobMetrics(jobId);

        if (batchJobMetrics == null) {
            throw new JobNotFoundException("Job Id not found for job " + jobId);
        }

        return batchJobMetrics;
    }

    /**
     * Method to get metrics for a stream job
     * @return
     */
    @GetMapping("/stats/stream/{jobId}")
    public StreamJobMetrics getStreamJobMetrics(@PathVariable String jobId) {
        StreamJobMetrics streamJobMetrics = statisticsUtilitiesService.generateStreamJobMetrics(jobId);

        if (streamJobMetrics == null) {
            throw new JobNotFoundException("Job Id not found for " + jobId);
        }

        return streamJobMetrics;
    }

    /**
     * Method to get metrics of all running and completed 
     * batch and stream jobs.
     * @return
     */
    @GetMapping("/stats")
    public AllJobMetrics getAllJobMetrics() {
        return statisticsUtilitiesService.generateAllJobMetrics();
    }

}