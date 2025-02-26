/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dolphinscheduler.plugin.task.api.loop;

import org.apache.dolphinscheduler.plugin.task.api.AbstractTaskExecutor;
import org.apache.dolphinscheduler.plugin.task.api.TaskConstants;
import org.apache.dolphinscheduler.plugin.task.api.TaskException;
import org.apache.dolphinscheduler.plugin.task.api.TaskExecutionContext;
import org.apache.dolphinscheduler.plugin.task.api.utils.RetryUtils;

import java.time.Duration;

import javax.annotation.Nullable;

import lombok.NonNull;

/**
 * This class is the base class for all loop task type.
 * <p>
 * The loop task type means, we will submit a task, and loop the task status until the task is finished.
 */
public abstract class BaseLoopTaskExecutor extends AbstractTaskExecutor {

    /**
     * The task instance info will be set when task has submitted successful.
     */
    protected @Nullable LoopTaskInstanceInfo loopTaskInstanceInfo;

    protected BaseLoopTaskExecutor(@NonNull TaskExecutionContext taskExecutionContext) {
        super(taskExecutionContext);
    }

    @Override
    public void handle() throws TaskException {
        try {
            final long loopInterval = getTaskInstanceStatusQueryInterval().toMillis();
            loopTaskInstanceInfo = submitLoopTask();
            this.appIds = loopTaskInstanceInfo.getTaskInstanceId();
            // loop the task status until the task is finished or task has been canceled.
            // we use retry utils here to avoid the task status query failure due to network failure.
            // the default retry policy is 3 times, and the interval is 1 second.
            while (!cancel
                && !RetryUtils.retryFunction(() -> queryTaskInstanceStatus(loopTaskInstanceInfo).isFinished())) {
                Thread.sleep(loopInterval);
            }
        } catch (InterruptedException e) {
            setExitStatusCode(TaskConstants.EXIT_CODE_FAILURE);
            logger.error("The current loop thread has been interrupted", e);
            Thread.currentThread().interrupt();
            throw new TaskException("The current loop thread has been interrupted");
        } catch (TaskException ex) {
            // print the error message with task logger.
            logger.error("Loop task execute error", ex);
            setExitStatusCode(TaskConstants.EXIT_CODE_FAILURE);
            throw ex;
        } catch (Exception ex) {
            setExitStatusCode(TaskConstants.EXIT_CODE_FAILURE);
            logger.error("Loop task execute error", ex);
            throw new TaskException("Loop task execute error", ex);
        }
    }

    /**
     * Submit the loop task, if submit failed, directly throw exception
     */
    public abstract @NonNull LoopTaskInstanceInfo submitLoopTask() throws TaskException;

    /**
     * Query the loop task status, if query failed, directly throw exception
     */
    public abstract @NonNull LoopTaskInstanceStatus queryTaskInstanceStatus(@NonNull LoopTaskInstanceInfo taskInstanceInfo)
        throws TaskException;

    /**
     * Get the interval time to query the loop task status
     */
    public @NonNull Duration getTaskInstanceStatusQueryInterval() {
        return TaskConstants.DEFAULT_LOOP_STATUS_INTERVAL;
    }

    /**
     * Cancel the loop task, if cancel failed, directly throw exception
     */
    public abstract void cancelLoopTaskInstance(@Nullable LoopTaskInstanceInfo taskInstanceInfo) throws TaskException;

    @Override
    public void cancelApplication(boolean status) throws Exception {
        cancelLoopTaskInstance(loopTaskInstanceInfo);
        super.cancelApplication(status);
    }
}
