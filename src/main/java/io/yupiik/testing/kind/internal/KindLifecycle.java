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

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static java.net.http.HttpResponse.BodyHandlers.ofInputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.ofNullable;

public class KindLifecycle implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(KindLifecycle.class.getName());

    private static final ConcurrentMap<Path, Lock> SHARED_LOCKS = new ConcurrentHashMap<>();

    private final String clusterName;
    private final String binary;
    private final Thread hook;

    private String kubeconfig;

    public KindLifecycle() {
        this(new Configuration());
    }

    public KindLifecycle(final Configuration configuration) {
        this.clusterName = ofNullable(configuration.clusterName())
                .filter(Predicate.not(String::isBlank))
                .orElseGet(() -> "ykc-" + UUID.randomUUID().toString().replace("-", ""));
        this.binary = findOrDownloadBinary(configuration).toAbsolutePath().normalize().toString();
        this.hook = new Thread(this::close);
        Runtime.getRuntime().addShutdownHook(hook);
        start(configuration.timeout().toSeconds(), configuration.configuration(), configuration.kindLogLevel());
    }

    public String clusterName() {
        return clusterName;
    }

    public String kubeconfig() { // not thread safe but not risky to run concurrently
        if (kubeconfig != null) {
            return kubeconfig;
        }
        kubeconfig = exec(true, true, "get", "kubeconfig", "--name", clusterName);
        return kubeconfig;
    }

    /**
     * Write the kubeconfig to the passed parameter.
     *
     * @param target expected location of the kubeconfig (overwritten).
     */
    public void writeKubeconfig(final Path target) {
        try {
            if (target.getParent() != null && !Files.exists(target.getParent())) {
                Files.createDirectories(target.getParent());
            }
            Files.writeString(target, kubeconfig(), UTF_8);
        } catch (final IOException ioe) {
            throw new IllegalStateException("Can't write kubeconfig to '" + target + "'", ioe);
        }
    }

    private void start(final long seconds, final Path conf, final int logLevel) {
        final var args = new ArrayList<>(List.of(
                "create", "cluster", "--name", clusterName, "--wait", seconds + "s", "-v", String.valueOf(logLevel)));
        if (conf != null) {
            args.add("--config=" + conf.toAbsolutePath().normalize());
        }
        exec(false, true, args.toArray(new String[0]));
    }

    @Override
    public void close() {
        exec(false, false, "delete", "cluster", "--name", clusterName);
        try {
            Runtime.getRuntime().removeShutdownHook(hook);
        } catch (final IllegalStateException ise) {
            // already shutting down, ok
        }
    }

    private String exec(final boolean returnStdOut, final boolean throwOnError, final String... args) {
        final var builder = new ProcessBuilder(Stream.concat(Stream.of(binary), Stream.of(args)).toList());
        if (!returnStdOut) {
            builder.inheritIO();
        }
        try {
            final var process = builder.start();
            final var exitCode = process.waitFor();
            if (throwOnError && exitCode != 0) {
                throw new IllegalStateException("Kind command failed: " + List.of(args) + ", exitCode=" + exitCode);
            }

            if (returnStdOut) {
                try (final var in = process.getInputStream()) {
                    return new String(in.readAllBytes(), UTF_8);
                }
            }
            return null;
        } catch (final IOException e) {
            throw new IllegalStateException("Can't execute kind command: " + List.of(args), e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private Path findOrDownloadBinary(final Configuration configuration) {
        final var os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        final var win = os.contains("win");
        final var binaryName = win ? "kind.exe" : "kind";
        final var cachedFile = configuration.cache().resolve(configuration.kindVersion()).resolve(binaryName).toAbsolutePath().normalize();

        // lock to ensure we can run tests concurrently - only works for 1 JVM
        final var lock = SHARED_LOCKS.computeIfAbsent(cachedFile, k -> new ReentrantLock());
        lock.lock();
        try {
            if (!Files.exists(cachedFile)) {
                LOGGER.info(() -> "Downloading kind...");
                downloadKind(configuration, win, os, cachedFile);
            }
            LOGGER.info(() -> "Found kind at '" + cachedFile + "'");
            return cachedFile;
        } finally {
            lock.unlock();
        }
    }

    private void downloadKind(final Configuration configuration, final boolean win, final String os, final Path cachedFile) {
        final var arch = System.getProperty("os.arch", "amd64").toLowerCase(Locale.ROOT);
        final var url = configuration
                .downloadUrl()
                .replace("{kindVersion}", configuration.kindVersion())
                .replace("{os}", win ? "windows" : os.contains("mac") ? "darwin" : "linux")
                .replace("{architecture}", arch.contains("aarch64") || arch.contains("arm64") ? "arm64" : "amd64")
                .replace("{extension}", win ? ".exe" : "");
        try {
            final var clientBuilder = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(configuration.httpTimeout());
            if (configuration.httpProxy() != Proxy.NO_PROXY) {
                clientBuilder.proxy(new ProxySelector() {
                    @Override
                    public List<Proxy> select(final URI uri) {
                        return List.of(configuration.httpProxy());
                    }

                    @Override
                    public void connectFailed(final URI uri, final SocketAddress sa, final IOException ioe) {
                        // no-op
                    }
                });
            }
            try (final var http = clientBuilder.build()) {
                final var res = http.send(
                        HttpRequest.newBuilder()
                                .GET()
                                .uri(URI.create(url))
                                .header("accept", "*/*")
                                .timeout(configuration.httpTimeout())
                                .build(),
                        ofInputStream());
                if (res.statusCode() != 200) {
                    throw new IllegalStateException("Invalid kind response: " + res);
                }

                Files.createDirectories(cachedFile.getParent());
                Files.copy(res.body(), cachedFile, StandardCopyOption.REPLACE_EXISTING);
                if (!win) {
                    Files.setPosixFilePermissions(cachedFile, PosixFilePermissions.fromString("rwxr-x---"));
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        } catch (final IOException e) {
            throw new IllegalStateException("Can't download/setup kind", e);
        }
    }

    /**
     * Configure kind cluster creation.
     */
    public static class Configuration {
        /**
         * Kind version - will be downloaded in cache directory if not there already.
         */
        private String kindVersion = "v0.31.0";

        /**
         * Base cache directory, will be used as a base to append the version and binary.
         */
        private Path cache = Path.of(System.getProperty("user.home", "target")).resolve(".yupiik/kind-junit-extension");

        /**
         * Directory where kind is downloaded when needed.
         */
        private Path workingDirectory = Path.of(System.getProperty("java.io.tmpdir", "target/kind_work"));

        /**
         * Timeout to await for kind startup.
         */
        private Duration timeout = Duration.ofMinutes(2);

        /**
         * HTTP proxy if needed to download kind when relevant/not cached.
         */
        private Proxy httpProxy = Proxy.NO_PROXY;

        /**
         * Timeout to connect/download kind.
         */
        private Duration httpTimeout = Duration.ofMinutes(2);

        /**
         * Explicit cluster name, if not set one is generated randomly.
         */
        private String clusterName = null;

        /**
         * Download link when needed. {@code kindVersion}, {@code os}, {@code architecture} and {@code extension} - with the dot if needed - are replaced when surrounded by braces.
         */
        private String downloadUrl = "https://kind.sigs.k8s.io/dl/{kindVersion}/kind-{os}-{architecture}{extension}";

        /**
         * An optional kind configuration to create a custom cluster (often with multiple nodes).
         */
        private Path configuration;

        /**
         * Kind log level ({@code -v}/{@code --verbosity}).
         */
        private int kindLogLevel = 0;

        public int kindLogLevel() {
            return kindLogLevel;
        }

        public void kindLogLevel(final int kindLogLevel) {
            this.kindLogLevel = kindLogLevel;
        }

        public Path configuration() {
            return configuration;
        }

        public void configuration(final Path configuration) {
            this.configuration = configuration;
        }

        public Path workingDirectory() {
            return workingDirectory;
        }

        public void workingDirectory(final Path workingDirectory) {
            this.workingDirectory = workingDirectory;
        }

        public Duration httpTimeout() {
            return httpTimeout;
        }

        public void httpTimeout(final Duration httpTimeout) {
            this.httpTimeout = httpTimeout;
        }

        public String kindVersion() {
            return kindVersion;
        }

        public void kindVersion(final String kindVersion) {
            this.kindVersion = kindVersion;
        }

        public Path cache() {
            return cache;
        }

        public void cache(final Path cache) {
            this.cache = cache;
        }

        public Duration timeout() {
            return timeout;
        }

        public void timeout(final Duration timeout) {
            this.timeout = timeout;
        }

        public Proxy httpProxy() {
            return httpProxy;
        }

        public void httpProxy(final Proxy httpProxy) {
            this.httpProxy = httpProxy;
        }

        public String clusterName() {
            return clusterName;
        }

        public void clusterName(final String clusterName) {
            this.clusterName = clusterName;
        }

        public String downloadUrl() {
            return downloadUrl;
        }

        public void downloadUrl(final String downloadLink) {
            this.downloadUrl = downloadLink;
        }
    }
}
