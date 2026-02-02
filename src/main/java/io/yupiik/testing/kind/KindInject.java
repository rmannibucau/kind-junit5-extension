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

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Mark a parameter of a test method or a field to inject:
 * <ul>
 *     <li>The kubeconfig path if typed as {@link java.nio.file.Path}, note it will create a file in temp directory</li>
 *     <li>The kubeconfig content if typed as {@link String}</li>
 *     <li>The kind representation as a {@link Kind} instance</li>
 * </ul>
 *
 * Note that it works as a field injection only when the instance is started at class level or is not instantiated {@code PER_CLASS}.
 * Use {@link Kind} indirection/injection for other cases.
 */
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
public @interface KindInject {
}
