/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.notifier.email;

import static org.junit.jupiter.api.Assertions.fail;

import io.vertx.junit5.VertxTestContext;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.BeforeEach;

public abstract class AbstractEmailNotifierTest {

    protected VertxTestContext testContext;

    @BeforeEach
    void setUp() throws IOException {
        testContext = new VertxTestContext();
    }

    /**
     * Helper method to await completion of the test context for 5 seconds and fail the test if the test context failed.
     */
    void awaitCompletionAndCheckFailure() {
        try {
            this.testContext.awaitCompletion(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (testContext.failed()) {
            fail(testContext.causeOfFailure());
        }
    }

    /**
     * Helper method to complete the test context when the future completes.
     *
     * @return a consumer that completes the test context
     */
    BiConsumer<Void, Throwable> completeOrFailNow() {
        return (unused, throwable) -> {
            if (throwable != null) {
                testContext.failNow(throwable);
            }
            testContext.completeNow();
        };
    }
}
