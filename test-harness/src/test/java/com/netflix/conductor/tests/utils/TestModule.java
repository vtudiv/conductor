/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
/**
 *
 */
package com.netflix.conductor.tests.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.common.utils.JsonMapperProvider;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.config.CoreModule;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.dao.dynomite.RedisExecutionDAO;
import com.netflix.conductor.dao.dynomite.RedisMetadataDAO;
import com.netflix.conductor.dao.dynomite.queue.DynoQueueDAO;
import com.netflix.conductor.jedis.InMemoryJedisProvider;
import com.netflix.conductor.jedis.JedisMock;
import com.netflix.dyno.queues.ShardSupplier;
import redis.clients.jedis.JedisCommands;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Viren
 *
 */
public class TestModule extends AbstractModule {
    private int maxThreads = 50;

    private ExecutorService executorService;

    @Override
    protected void configure() {

        System.setProperty("workflow.system.task.worker.callback.seconds", "0");
        System.setProperty("workflow.system.task.worker.queue.size", "10000");
        System.setProperty("workflow.system.task.worker.thread.count", "10");

        configureExecutorService();

        MockConfiguration config = new MockConfiguration();
        bind(Configuration.class).toInstance(config);
        JedisCommands jedisMock = new JedisMock();

        DynoQueueDAO queueDao = new DynoQueueDAO(jedisMock, jedisMock, new ShardSupplier() {

            @Override
            public Set<String> getQueueShards() {
                return new HashSet<>(Collections.singletonList("a"));
            }

            @Override
            public String getCurrentShard() {
                return "a";
            }
        }, config);

        bind(MetadataDAO.class).to(RedisMetadataDAO.class);
        bind(ExecutionDAO.class).to(RedisExecutionDAO.class);
        bind(DynoQueueDAO.class).toInstance(queueDao);
        bind(QueueDAO.class).to(DynoQueueDAO.class);
        bind(IndexDAO.class).to(MockIndexDAO.class);
        bind(JedisCommands.class).toProvider(InMemoryJedisProvider.class);
        install(new CoreModule());
        bind(UserTask.class).asEagerSingleton();
        bind(ObjectMapper.class).toProvider(JsonMapperProvider.class);
        bind(ExternalPayloadStorage.class).to(MockExternalPayloadStorage.class);
    }

    @Provides
    public ExecutorService getExecutorService() {
        return this.executorService;
    }

    private void configureExecutorService() {
        AtomicInteger count = new AtomicInteger(0);
        this.executorService = java.util.concurrent.Executors.newFixedThreadPool(maxThreads, runnable -> {
            Thread workflowWorkerThread = new Thread(runnable);
            workflowWorkerThread.setName(String.format("workflow-worker-%d", count.getAndIncrement()));
            return workflowWorkerThread;
        });
    }
}
