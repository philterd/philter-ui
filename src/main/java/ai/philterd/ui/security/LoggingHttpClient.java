/*
 *     Copyright 2026 Philterd, LLC @ https://www.philterd.ai
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.philterd.ui.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.InetAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * An HTTP client for Philter's SDK that logs every response Philter refuses or fails, with its method, path, status,
 * and the {@code X-Request-Id} Philter records on the audit events the request caused, so an administrator can find
 * what happened. The query string is not logged. Everything else is passed to the JDK's client.
 */
final class LoggingHttpClient extends HttpClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingHttpClient.class);

    /** The header Philter returns its request id in. */
    static final String REQUEST_ID = "X-Request-Id";

    private final HttpClient delegate;

    private LoggingHttpClient(final HttpClient delegate) {
        this.delegate = delegate;
    }

    /** A builder configured as the SDK configures its own client, whose client logs failed responses. */
    static HttpClient.Builder builder(final long timeoutSeconds) {
        final HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_1_1);
        final ProxySelector proxySelector = ProxySelector.getDefault();
        if (proxySelector != null) {
            builder.proxy(proxySelector);
        }
        return new Builder(builder);
    }

    /** The line logged for a failed response, or {@code null} for a successful one. */
    static String describe(final String method, final String path, final int status, final String requestId) {
        if (status < 400) {
            return null;
        }
        return "Philter answered " + method + " " + path + " with HTTP " + status + " (request id "
                + (requestId == null ? "none" : requestId) + ")";
    }

    private <T> HttpResponse<T> logged(final HttpResponse<T> response) {
        final String line = describe(response.request().method(), response.request().uri().getRawPath(),
                response.statusCode(), response.headers().firstValue(REQUEST_ID).orElse(null));
        if (line != null) {
            if (response.statusCode() >= 500) {
                LOGGER.warn(line);
            } else {
                LOGGER.info(line);
            }
        }
        return response;
    }

    @Override
    public <T> HttpResponse<T> send(final HttpRequest request, final HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        return logged(delegate.send(request, handler));
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(final HttpRequest request,
                                                            final HttpResponse.BodyHandler<T> handler) {
        return delegate.sendAsync(request, handler).thenApply(this::logged);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(final HttpRequest request,
                                                            final HttpResponse.BodyHandler<T> handler,
                                                            final HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        return delegate.sendAsync(request, handler, pushPromiseHandler).thenApply(this::logged);
    }

    @Override
    public Optional<CookieHandler> cookieHandler() {
        return delegate.cookieHandler();
    }

    @Override
    public Optional<Duration> connectTimeout() {
        return delegate.connectTimeout();
    }

    @Override
    public Redirect followRedirects() {
        return delegate.followRedirects();
    }

    @Override
    public Optional<ProxySelector> proxy() {
        return delegate.proxy();
    }

    @Override
    public SSLContext sslContext() {
        return delegate.sslContext();
    }

    @Override
    public SSLParameters sslParameters() {
        return delegate.sslParameters();
    }

    @Override
    public Optional<Authenticator> authenticator() {
        return delegate.authenticator();
    }

    @Override
    public Version version() {
        return delegate.version();
    }

    @Override
    public Optional<Executor> executor() {
        return delegate.executor();
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public boolean awaitTermination(final Duration duration) throws InterruptedException {
        return delegate.awaitTermination(duration);
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public void shutdownNow() {
        delegate.shutdownNow();
    }

    @Override
    public void close() {
        delegate.close();
    }

    /** Configures the JDK's builder, and wraps the client it builds. */
    private static final class Builder implements HttpClient.Builder {

        private final HttpClient.Builder delegate;

        private Builder(final HttpClient.Builder delegate) {
            this.delegate = delegate;
        }

        @Override
        public HttpClient.Builder cookieHandler(final CookieHandler cookieHandler) {
            delegate.cookieHandler(cookieHandler);
            return this;
        }

        @Override
        public HttpClient.Builder connectTimeout(final Duration duration) {
            delegate.connectTimeout(duration);
            return this;
        }

        @Override
        public HttpClient.Builder sslContext(final SSLContext sslContext) {
            delegate.sslContext(sslContext);
            return this;
        }

        @Override
        public HttpClient.Builder sslParameters(final SSLParameters sslParameters) {
            delegate.sslParameters(sslParameters);
            return this;
        }

        @Override
        public HttpClient.Builder executor(final Executor executor) {
            delegate.executor(executor);
            return this;
        }

        @Override
        public HttpClient.Builder followRedirects(final Redirect policy) {
            delegate.followRedirects(policy);
            return this;
        }

        @Override
        public HttpClient.Builder version(final Version version) {
            delegate.version(version);
            return this;
        }

        @Override
        public HttpClient.Builder priority(final int priority) {
            delegate.priority(priority);
            return this;
        }

        @Override
        public HttpClient.Builder proxy(final ProxySelector proxySelector) {
            delegate.proxy(proxySelector);
            return this;
        }

        @Override
        public HttpClient.Builder authenticator(final Authenticator authenticator) {
            delegate.authenticator(authenticator);
            return this;
        }

        @Override
        public HttpClient.Builder localAddress(final InetAddress localAddress) {
            delegate.localAddress(localAddress);
            return this;
        }

        @Override
        public HttpClient build() {
            return new LoggingHttpClient(delegate.build());
        }
    }

}
