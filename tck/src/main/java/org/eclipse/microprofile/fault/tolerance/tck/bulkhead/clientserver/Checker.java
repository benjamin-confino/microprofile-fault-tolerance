/*
 *******************************************************************************
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package org.eclipse.microprofile.fault.tolerance.tck.bulkhead.clientserver;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.microprofile.fault.tolerance.tck.bulkhead.Utils;
import org.testng.Assert;

/**
 * A simple sleeping test backend worker. Having this backend as a delegate
 * means that we can perform more than one kind of test using a common
 * 
 * @Injected object that delegates to one of these that is passed in as a
 *           parameter to the business method.
 * 
 *           There are a number of tests that this backend can perform:
 *           <ul>
 *           <li>expected number of instances created
 *           <li>expected workers started via perform method
 *           <li>max simultaneous workers not exceeded
 *           </ul>
 * 
 * @author Gordon Hutchison
 */
public class Checker implements BackendTestDelegate {

    private int millis = 1;
    private static AtomicInteger workers = new AtomicInteger(0);
    private static AtomicInteger maxSimultaneousWorkers = new AtomicInteger(0);
    private static AtomicInteger instances = new AtomicInteger(0);
    private static AtomicInteger tasksScheduled = new AtomicInteger(0);
    private static int expectedInstances;
    private static int expectedMaxSimultaneousWorkers;
    private static int expectedTasksScheduled;


    /*
     * This string is used for varying substr's barcharts in the log, for
     * example for the number of concurrent workers.
     */
    static final String BAR = "**************************************************************************************+++";

    /**
     * Constructor
     * 
     * @param i
     *            how long to sleep for in milliseconds
     */
    public Checker(int sleepMillis) {
        millis = sleepMillis;
        instances.incrementAndGet();
    }

    /*
     * Work this is the method that simulates the backend work inside the
     * Bulkhead.
     * 
     * @see org.eclipse.microprofile.fault.tolerance.tck.bulkhead.clientserver.
     * BulkheadTestAction#perform()
     */
    @Override
    public Future<String> perform() {
        try {
            int taskId = tasksScheduled.incrementAndGet();
            int now = workers.incrementAndGet();
            int max = maxSimultaneousWorkers.get();

            while ((now > max) && !maxSimultaneousWorkers.compareAndSet(max, now)) {
                max = maxSimultaneousWorkers.get();
            }

            Utils.log("Task " + taskId + " sleeping for " + millis + " milliseconds. " + now + " workers from "
                    + instances + " instances " + BAR.substring(0, now));
            Thread.sleep(millis);

            Utils.log("woke");
        }
        catch (InterruptedException e) {
            Utils.log(e.toString());
        } 
        finally {
            workers.decrementAndGet();
        }
        CompletableFuture<String> result = new CompletableFuture<>();
        result.complete("max workers was " + maxSimultaneousWorkers.get());
        return result;
    }

    /**
     * Prepare the state for the next test
     */
    public static void reset() {
        instances.set(0);
        workers.set(0);
        maxSimultaneousWorkers.set(0);
        tasksScheduled.set(0);
    }

    /**
     * Check the test ran successfully
     */
    public static void check() {
        Assert.assertEquals(workers.get(), 0, "Some workers still active. ");
        Assert.assertEquals(instances.get(), expectedInstances, " Not all workers launched. ");
        Assert.assertTrue(maxSimultaneousWorkers.get() <= expectedMaxSimultaneousWorkers,
                " Bulkhead appears to have been breeched " + maxSimultaneousWorkers.get() + " workers, expected "
                        + expectedMaxSimultaneousWorkers + ". ");
        Assert.assertFalse(expectedMaxSimultaneousWorkers > 1 && maxSimultaneousWorkers.get() == 1,
                " Workers are not in parrallel. ");
        Assert.assertTrue(expectedMaxSimultaneousWorkers == maxSimultaneousWorkers.get(),
                " Work is not being done simultaneously enough, only " + maxSimultaneousWorkers + ". "
                        + " workers are once. Expecting " + expectedMaxSimultaneousWorkers + ". ");
        Assert.assertFalse(expectedTasksScheduled != 0 && tasksScheduled.get() < expectedTasksScheduled,
                " Some tasks are missing, expected " + expectedTasksScheduled + " got " + tasksScheduled.get() + ". ");

        Utils.log("Checks passed");
    }


    public static int getWorkers() {
        return workers.get();
    }
    
    public static void setExpectedTasksScheduled(int expected) {
        expectedTasksScheduled = expected;
    }

    public static void setExpectedInstances(int expectedInstances) {
        Checker.expectedInstances = expectedInstances;
    }

    public static void setExpectedMaxWorkers(int expectedMaxWorkers) {
        Checker.expectedMaxSimultaneousWorkers = expectedMaxWorkers;
    }
}
