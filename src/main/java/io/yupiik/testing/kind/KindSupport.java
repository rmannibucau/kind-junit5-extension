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

import io.yupiik.testing.kind.internal.KindExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.time.Duration;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target({TYPE, METHOD})
@ExtendWith(KindExtension.class)
public @interface KindSupport {
    /**
     * Kind version - will be downloaded in cache directory if not there already.
     */
    String kindVersion() default "v0.31.0";

    /**
     * Base cache directory, will be used as a base to append the version and binary.
     */
    String cache() default "";

    /**
     * Directory where kind is downloaded when needed.
     */
    String workingDirectory() default "";

    /**
     * Timeout to await for kind startup. In {@link Duration} format.
     */
    String timeout() default "PT2M";

    /**
     * HTTP proxy if needed to download kind when relevant/not cached.
     */
    HttpProxy httpProxy() default @HttpProxy(address = "", port = -1);

    /**
     * Timeout to connect/download kind. In {@link Duration} format.
     */
    String httpTimeout() default "PT2M";

    /**
     * Explicit cluster name, if not set one is generated randomly.
     */
    String clusterName() default "";

    /**
     * Download link when needed. {@code kindVersion}, {@code os}, {@code architecture} and {@code extension} - with the dot if needed - are replaced when surrounded by braces.
     */
    String downloadUrl() default "https://kind.sigs.k8s.io/dl/{kindVersion}/kind-{os}-{architecture}{extension}";

    /**
     * An optional kind configuration to create a custom cluster (often with multiple nodes).
     */
    String configuration() default "";

    /**
     * Kind log level ({@code -v}/{@code --verbosity}).
     */
    int kindLogLevel() default 0;
}
