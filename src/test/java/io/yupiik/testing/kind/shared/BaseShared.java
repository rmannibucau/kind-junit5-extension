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
package io.yupiik.testing.kind.shared;

import io.yupiik.testing.kind.Kind;
import io.yupiik.testing.kind.KindInject;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static io.yupiik.testing.kind.infra.KindAssertions.assertKindUp;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BaseShared {
    private static String last = null;

    protected void onTest(final String cluster) {
        if (last == null) {
            last = cluster;
        } else {
            assertEquals(last, cluster);
        }
    }

    @KindInject
    private Kind kind;

    @Test
    void injectField() {
        assertKindUp(kind.kubeconfigPath());
        onTest(kind.clusterName());
    }

    @Test
    void injectParams(@KindInject final Path kc) {
        assertKindUp(kc);
        onTest(kind.clusterName());
    }
}
