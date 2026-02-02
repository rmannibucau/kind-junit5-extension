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
package io.yupiik.testing.kind.infra;

import io.yupiik.fusion.kubernetes.client.KubernetesClient;
import io.yupiik.fusion.kubernetes.client.KubernetesClientConfiguration;

import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class KindAssertions {
    private KindAssertions() {
        // no-op
    }

    public static void assertKindUp(final Path kubeconfig) {
        try (final var client = new KubernetesClient(new KubernetesClientConfiguration()
                .setKubeconfig(kubeconfig))) {
            final var res = client.send(HttpRequest.newBuilder()
                    .timeout(Duration.ofMinutes(1))
                    .GET()
                    .uri(URI.create("https://kubernetes.api/api/v1/namespaces/kube-system/pods"))
                    .build());
            assertEquals(200, res.statusCode());
            assertTrue(res.body().contains("\"kind\":\"PodList\""), res.body());
        }
    }
}
