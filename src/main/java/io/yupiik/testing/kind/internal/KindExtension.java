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

import io.yupiik.testing.kind.Kind;
import io.yupiik.testing.kind.KindInject;
import io.yupiik.testing.kind.KindSupport;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.junit.platform.commons.util.AnnotationUtils;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;

public class KindExtension implements
        BeforeAllCallback, AfterAllCallback,
        BeforeEachCallback, AfterEachCallback,
        ParameterResolver, TestInstancePostProcessor {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(KindExtension.class);

    @Override
    public ExtensionContextScope getTestInstantiationExtensionContextScope(final ExtensionContext rootContext) {
        return ExtensionContextScope.TEST_METHOD;
    }

    @Override
    public void postProcessTestInstance(final Object testInstance, final ExtensionContext context) throws Exception {
        doInject(context, testInstance.getClass(), testInstance);
    }

    @Override
    public boolean supportsParameter(final ParameterContext ctx, final ExtensionContext extensionContext) throws ParameterResolutionException {
        final var type = ctx.getParameter().getType();
        return ctx.getParameter().isAnnotationPresent(KindInject.class) && canResolveValue(type);
    }

    @Override
    public Object resolveParameter(final ParameterContext ctx, final ExtensionContext extensionContext) throws ParameterResolutionException {
        return resolveValue(extensionContext, ctx.getParameter().getType());
    }

    @Override
    public void beforeAll(final ExtensionContext context) {
        context
                .getStore(NAMESPACE)
                .computeIfAbsent(ExtensionKindLifecycle.class, k -> {
                    final var conf = context.getElement()
                            .flatMap(e -> AnnotationUtils.findAnnotation(e, KindSupport.class)
                                    .or(() -> context.getParent()
                                            .flatMap(ExtensionContext::getElement)
                                            .flatMap(p -> AnnotationUtils.findAnnotation(p, KindSupport.class))))
                            .map(this::mapConfiguration)
                            .orElseGet(KindLifecycle.Configuration::new);
                    return new ExtensionKindLifecycle(conf, context);
                });
    }

    @Override
    public void afterAll(final ExtensionContext context) {
        final var store = context.getStore(NAMESPACE);
        ofNullable(store.get(Destroyable.class, Destroyable.class)).ifPresent(Destroyable::close);
        ofNullable(store.get(ExtensionKindLifecycle.class, ExtensionKindLifecycle.class)).ifPresent(e -> e.destroy(context));
    }

    @Override
    public void beforeEach(final ExtensionContext context) {
        beforeAll(context);
    }

    @Override
    public void afterEach(final ExtensionContext context) {
        afterAll(context);
    }

    private KindLifecycle.Configuration mapConfiguration(final KindSupport kindSupport) {
        final var conf = new KindLifecycle.Configuration();
        conf.kindVersion(kindSupport.kindVersion());
        conf.kindLogLevel(kindSupport.kindLogLevel());
        if (!kindSupport.cache().isBlank()) {
            conf.cache(Path.of(kindSupport.cache()));
        }
        if (!kindSupport.workingDirectory().isBlank()) {
            conf.workingDirectory(Path.of(kindSupport.workingDirectory()));
        }
        if (!kindSupport.timeout().isBlank()) {
            conf.timeout(Duration.parse(kindSupport.timeout()));
        }
        if (!kindSupport.clusterName().isBlank()) {
            conf.clusterName(kindSupport.clusterName());
        }
        if (!kindSupport.downloadUrl().isBlank()) {
            conf.downloadUrl(kindSupport.downloadUrl());
        }
        if (!kindSupport.configuration().isBlank()) {
            conf.configuration(Path.of(kindSupport.configuration()));
        }
        if (!kindSupport.httpTimeout().isBlank()) {
            conf.httpTimeout(Duration.parse(kindSupport.httpTimeout()));
        }
        if (!kindSupport.httpProxy().address().isBlank() && kindSupport.httpProxy().port() > 0) {
            conf.httpProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(kindSupport.httpProxy().address(), kindSupport.httpProxy().port())));
        }
        return null;
    }

    private boolean canResolveValue(final Class<?> type) {
        return type == Path.class || type == String.class || type == Kind.class;
    }

    private Object resolveValue(final ExtensionContext extensionContext, final Class<?> type) {
        if (type == String.class) {
            return findLifecycle(extensionContext).kubeconfig();
        }
        if (type == Path.class) {
            try {
                final var tmp = File.createTempFile("yke", "kbc").toPath();
                findLifecycle(extensionContext).writeKubeconfig(tmp);
                extensionContext
                        .getStore(NAMESPACE)
                        .computeIfAbsent(Destroyable.class, k -> new Destroyable(new ArrayList<>()), Destroyable.class)
                        .cleanup()
                        .add(() -> {
                            if (!Files.exists(tmp)) {
                                return;
                            }
                            try {
                                Files.delete(tmp);
                            } catch (final IOException e) {
                                // no-op, not critical
                            }
                        });
                return tmp;
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }
        if (type == Kind.class) {
            return new Kind() {
                @Override
                public String kubeconfig() {
                    return (String) resolveValue(extensionContext, String.class);
                }

                @Override
                public Path kubeconfigPath() {
                    return (Path) resolveValue(extensionContext, Path.class);
                }

                @Override
                public String clusterName() {
                    return findLifecycle(extensionContext).clusterName();
                }
            };
        }
        throw new ParameterResolutionException("Can't resolve " + type);
    }

    private ExtensionKindLifecycle findLifecycle(final ExtensionContext extensionContext) {
        return extensionContext.getStore(NAMESPACE).get(ExtensionKindLifecycle.class, ExtensionKindLifecycle.class);
    }

    private void doInject(final ExtensionContext ctx, final Class<?> type, final Object testInstance) {
        var current = type;
        while (current != Object.class && current != null) {
            Stream.of(current.getDeclaredFields())
                    .filter(it -> it.isAnnotationPresent(KindInject.class) && canResolveValue(it.getType()))
                    .forEach(it -> {
                        if (!it.canAccess(testInstance)) {
                            it.setAccessible(true);
                        }
                        try {
                            it.set(testInstance, resolveValue(ctx, it.getType()));
                        } catch (final IllegalAccessException e) {
                            throw new IllegalStateException(e);
                        }
                    });
            current = current.getSuperclass();
        }
    }

    private static class ExtensionKindLifecycle extends KindLifecycle {
        private final ExtensionContext ctx;

        private ExtensionKindLifecycle(final Configuration conf, final ExtensionContext context) {
            super(conf);
            this.ctx = context;
        }

        private void destroy(final ExtensionContext ctx) {
            if (this.ctx == ctx) {
                super.close();
            }
        }
    }

    private record Destroyable(List<Runnable> cleanup) implements AutoCloseable {
        @Override
        public void close() {
            cleanup.forEach(Runnable::run);
        }
    }
}
