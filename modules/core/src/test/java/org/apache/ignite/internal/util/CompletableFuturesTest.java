/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.util;

import static org.apache.ignite.internal.util.CompletableFutures.booleanCompletedFuture;
import static org.apache.ignite.internal.util.CompletableFutures.emptyCollectionCompletedFuture;
import static org.apache.ignite.internal.util.CompletableFutures.emptyListCompletedFuture;
import static org.apache.ignite.internal.util.CompletableFutures.emptyMapCompletedFuture;
import static org.apache.ignite.internal.util.CompletableFutures.emptySetCompletedFuture;
import static org.apache.ignite.internal.util.CompletableFutures.falseCompletedFuture;
import static org.apache.ignite.internal.util.CompletableFutures.nullCompletedFuture;
import static org.apache.ignite.internal.util.CompletableFutures.trueCompletedFuture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/** For {@link CompletableFutures} testing. */
public class CompletableFuturesTest {
    @Test
    void testNullCompletedFuture() {
        CompletableFuture<Integer> future = nullCompletedFuture();

        assertTrue(future.isDone());
        assertNull(future.join());
    }

    @Test
    void testTrueCompletedFuture() {
        CompletableFuture<Boolean> future = trueCompletedFuture();

        assertTrue(future.isDone());
        assertTrue(future.join());
    }

    @Test
    void testFalseCompletedFuture() {
        CompletableFuture<Boolean> future = falseCompletedFuture();

        assertTrue(future.isDone());
        assertFalse(future.join());
    }

    @Test
    void testBooleanCompletedFuture() {
        CompletableFuture<Boolean> future0 = booleanCompletedFuture(true);
        CompletableFuture<Boolean> future1 = booleanCompletedFuture(false);

        assertTrue(future0.isDone());
        assertTrue(future1.isDone());

        assertTrue(future0.join());
        assertFalse(future1.join());
    }

    @Test
    void testEmptyCollectionCompletedFuture() {
        CompletableFuture<Collection<String>> future = emptyCollectionCompletedFuture();

        assertTrue(future.isDone());
        assertTrue(future.join().isEmpty());

        assertThrows(UnsupportedOperationException.class, () -> future.join().add("1"));
    }

    @Test
    void testEmptyListCompletedFuture() {
        CompletableFuture<List<Integer>> future = emptyListCompletedFuture();

        assertTrue(future.isDone());
        assertTrue(future.join().isEmpty());

        assertThrows(UnsupportedOperationException.class, () -> future.join().add(1));
    }

    @Test
    void testEmptySetCompletedFuture() {
        CompletableFuture<Set<Long>> future = emptySetCompletedFuture();

        assertTrue(future.isDone());
        assertTrue(future.join().isEmpty());

        assertThrows(UnsupportedOperationException.class, () -> future.join().add(1L));
    }

    @Test
    void testEmptyMapCompletedFuture() {
        CompletableFuture<Map<Long, Integer>> future = emptyMapCompletedFuture();

        assertTrue(future.isDone());
        assertTrue(future.join().isEmpty());

        assertThrows(UnsupportedOperationException.class, () -> future.join().put(1L, 2));
    }
}