/*
 * Copyright (c) 2026 - present - Yupiik SAS - https://www.yupiik.com
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.yupiik.testing.kind.fusion;

import io.yupiik.fusion.kubernetes.client.KubernetesClient;
import io.yupiik.fusion.kubernetes.client.KubernetesClientConfiguration;
import org.junit.jupiter.api.function.ThrowingConsumer;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * utility to ease assertions on kubernetes.
 */
public final class FusionAssertions {
    private FusionAssertions() {
        // no-op
    }

    public static void assertKubernetes(final Path kubeconfig, ThrowingConsumer<KubernetesClient> action) {
        try (final var client = new KubernetesClient(new KubernetesClientConfiguration()
                .setKubeconfig(kubeconfig))) {
            action.accept(client);
        } catch (final Throwable e) {
            fail(e);
        }
    }
}
