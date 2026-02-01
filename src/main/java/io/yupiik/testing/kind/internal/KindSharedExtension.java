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
package io.yupiik.testing.kind.internal;

import org.junit.jupiter.api.MediaType;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExecutableInvoker;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.junit.jupiter.api.extension.TestInstances;
import org.junit.jupiter.api.function.ThrowingConsumer;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public class KindSharedExtension implements
        BeforeAllCallback, BeforeEachCallback,
        ParameterResolver, TestInstancePostProcessor {
    private static final KindExtension DELEGATE = new KindExtension();
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static final ExtensionContext.Store STORE = new ExtensionContext.Store() {
        private final ConcurrentHashMap<Object, Object> store = new ConcurrentHashMap<>();

        @Override
        public Object get(final Object key) {
            return store.get(key);
        }

        @Override
        public <V> V get(final Object key, final Class<V> requiredType) {
            return requiredType.cast(store.get(key));
        }

        @Override
        public <K, V> Object getOrComputeIfAbsent(final K key, final Function<? super K, ? extends V> defaultCreator) {
            return store.computeIfAbsent(key, k -> defaultCreator.apply((K) k));
        }

        @Override
        public <K, V> Object computeIfAbsent(final K key, final Function<? super K, ? extends V> defaultCreator) {
            return getOrComputeIfAbsent(key, defaultCreator);
        }

        @Override
        public <K, V> V getOrComputeIfAbsent(final K key, final Function<? super K, ? extends V> defaultCreator, final Class<V> requiredType) {
            return requiredType.cast(getOrComputeIfAbsent(key, defaultCreator));
        }

        @Override
        public <K, V> V computeIfAbsent(final K key, final Function<? super K, ? extends V> defaultCreator, final Class<V> requiredType) {
            return requiredType.cast(computeIfAbsent(key, defaultCreator));
        }

        @Override
        public void put(final Object key, final Object value) {
            store.put(key, value);
        }

        @Override
        public Object remove(final Object key) {
            return store.remove(key);
        }

        @Override
        public <V> V remove(final Object key, final Class<V> requiredType) {
            return requiredType.cast(store.remove(key));
        }
    };
    private static final ExtensionContext FAKECONTEXT = new ExtensionContext() {
        @Override
        public Optional<ExtensionContext> getParent() {
            return Optional.empty();
        }

        @Override
        public ExtensionContext getRoot() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getUniqueId() {
            return "";
        }

        @Override
        public String getDisplayName() {
            return "";
        }

        @Override
        public Set<String> getTags() {
            return Set.of();
        }

        @Override
        public Optional<AnnotatedElement> getElement() {
            return Optional.empty();
        }

        @Override
        public Optional<Class<?>> getTestClass() {
            return Optional.empty();
        }

        @Override
        public List<Class<?>> getEnclosingTestClasses() {
            return List.of();
        }

        @Override
        public Optional<TestInstance.Lifecycle> getTestInstanceLifecycle() {
            return Optional.empty();
        }

        @Override
        public Optional<Object> getTestInstance() {
            return Optional.empty();
        }

        @Override
        public Optional<TestInstances> getTestInstances() {
            return Optional.empty();
        }

        @Override
        public Optional<Method> getTestMethod() {
            return Optional.empty();
        }

        @Override
        public Optional<Throwable> getExecutionException() {
            return Optional.empty();
        }

        @Override
        public Optional<String> getConfigurationParameter(final String key) {
            return Optional.empty();
        }

        @Override
        public <T> Optional<T> getConfigurationParameter(final String key,
                                                         final Function<? super String, ? extends T> transformer) {
            return Optional.empty();
        }

        @Override
        public void publishReportEntry(final Map<String, String> map) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void publishFile(final String name, final MediaType mediaType, final ThrowingConsumer<Path> action) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void publishDirectory(final String name, final ThrowingConsumer<Path> action) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Store getStore(final Namespace namespace) {
            return STORE;
        }

        @Override
        public Store getStore(final StoreScope scope, final Namespace namespace) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ExecutionMode getExecutionMode() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ExecutableInvoker getExecutableInvoker() {
            throw new UnsupportedOperationException();
        }
    };

    @Override
    public void postProcessTestInstance(final Object testInstance, final ExtensionContext context) throws Exception {
        touch();
        DELEGATE.postProcessTestInstance(testInstance, FAKECONTEXT);
    }

    @Override
    public boolean supportsParameter(final ParameterContext ctx, final ExtensionContext extensionContext) throws ParameterResolutionException {
        touch();
        return DELEGATE.supportsParameter(ctx, FAKECONTEXT);
    }

    @Override
    public Object resolveParameter(final ParameterContext ctx, final ExtensionContext extensionContext) throws ParameterResolutionException {
        touch();
        return DELEGATE.resolveParameter(ctx, FAKECONTEXT);
    }

    @Override
    public void beforeAll(final ExtensionContext context) {
        touch();
        DELEGATE.beforeAll(FAKECONTEXT);
    }

    @Override
    public void beforeEach(final ExtensionContext context) {
        touch();
        DELEGATE.beforeEach(FAKECONTEXT);
    }

    private void touch() {
        if (STARTED.compareAndSet(false, true)) {
            DELEGATE.beforeAll(FAKECONTEXT);
        }
    }
}
