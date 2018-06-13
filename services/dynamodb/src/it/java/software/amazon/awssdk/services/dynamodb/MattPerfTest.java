/*
 * Copyright 2010-2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.services.dynamodb;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import software.amazon.awssdk.core.config.options.SdkAdvancedAsyncClientOption;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;

public class MattPerfTest {
    public static void main(String[] args) throws InterruptedException {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            System.err.println(thread + " raised uncaught exception.");
            throwable.printStackTrace();
        });

        loadTest();
    }

    private static void loadTest() throws InterruptedException {
        // Configure
        final int startConcurrency = 1_000_000;
        final int stepConcurrency = 0;

        final Duration runTime = Duration.ofSeconds(60);
        final int maxRuns = 50;

        // Run
        System.out.println("Run | Concurrent Requests | Successes | Failures | TPS | Avg Latency ");
        System.out.println("--- | --- | --- | --- | --- | --- ");
        for (int runNumber = 1, concurrentRequests = startConcurrency;
             runNumber < maxRuns;
             ++runNumber, concurrentRequests += stepConcurrency) {

            Semaphore semaphore = new Semaphore(concurrentRequests);

            DynamoDBAsyncClient client =
                    DynamoDBAsyncClient.builder()
                                       .asyncHttpClientBuilder(NettyNioAsyncHttpClient.builder().maxConnectionsPerEndpoint(100_000_000))
                                       .asyncConfiguration(r -> r.advancedOption(SdkAdvancedAsyncClientOption.FUTURE_COMPLETION_EXECUTOR, Runnable::run))
                                       .build();

            AtomicInteger successes = new AtomicInteger(0);
            AtomicInteger failures = new AtomicInteger(0);
            AtomicLong successLatency = new AtomicLong(0);

            Instant endTime = Instant.now().plus(runTime);

            while (Instant.now().isBefore(endTime)) {
                semaphore.acquire();

                if (Instant.now().isAfter(endTime)) {
                    semaphore.release();
                    break;
                }

                Instant requestStartTime = Instant.now();
                client.listTables().whenComplete((r, t) -> {
                          try {
                              if (t == null) {
                                  successes.incrementAndGet();
                                  successLatency.addAndGet(Duration.between(requestStartTime, Instant.now()).toMillis());
                              } else  {
                                  failures.incrementAndGet();
                              }
                          } finally {
                              semaphore.release();
                          }
                      });
            }

            int successCount = successes.get();
            long successLatencyTotal = successLatency.get();
            int failureCount = failures.get();

            long tps = successCount / runTime.getSeconds();
            long latency = successCount == 0 ? 0 : successLatencyTotal / successCount;

            System.out.println(runNumber + " | " + concurrentRequests + " | " + successCount + " | " +
                               failureCount + " | " + tps + " | " + latency + " ms");

            boolean success = semaphore.tryAcquire(concurrentRequests, 5, TimeUnit.MINUTES);
            if (!success) {
                System.err.println((concurrentRequests - semaphore.availablePermits()) +
                                   " requests didn't complete after 5 minutes.");
            }
            assertThat(success).isTrue();
            client.close();
        }
    }
}
