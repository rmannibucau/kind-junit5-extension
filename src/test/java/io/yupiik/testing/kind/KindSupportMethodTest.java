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
package io.yupiik.testing.kind;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static io.yupiik.testing.kind.infra.KindAssertions.assertKindUp;
import static org.junit.jupiter.api.Assertions.assertEquals;

class KindSupportMethodTest {
    private static final List<String> paths = new ArrayList<>();

    @Test
    @KindSupport
    void injectParams1(@KindInject final Kind kc) {
        doTest(kc);
    }

    @Test
    @KindSupport
    void injectParams2(@KindInject final Kind kc) {
        doTest(kc);
    }

    private void doTest(final Kind kc) {
        onTest(kc.kubeconfig());
        assertKindUp(kc.kubeconfigPath());
    }

    // ensure it was started twice
    private static void onTest(final String kubeconfig) {
        paths.add(kubeconfig);
        if (paths.size() > 1) {
            assertEquals(paths.size(), new HashSet<>(paths).size());
        }
    }
}
