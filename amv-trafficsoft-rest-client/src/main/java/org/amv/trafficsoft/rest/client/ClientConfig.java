package org.amv.trafficsoft.rest.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.base.Charsets;
import com.netflix.hystrix.*;
import feign.*;
import feign.auth.BasicAuthRequestInterceptor;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.hystrix.SetterFactory;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.okhttp.OkHttpClient;
import feign.slf4j.Slf4jLogger;
import lombok.*;
import lombok.Builder.Default;
import lombok.experimental.Accessors;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import static com.netflix.hystrix.HystrixCommandProperties.ExecutionIsolationStrategy.THREAD;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * A configuration interface for clients.
 * <p>
 * The user of this interface has precise control over
 * how a client class behaves internally and is able to configure
 * it to his needs.
 *
 * @param <T> the type of the client this class is configuring
 * @author Alois Leitner
 */
public interface ClientConfig<T> {
    /**
     * An interface for configuring basic authentication.
     *
     * @author Alois Leitner
     */
    interface BasicAuth {
        String username();

        String password();
    }


    /**
     * A simple implementation of {@link BasicAuth}.
     *
     * @author Alois Leitner
     */
    @Value
    @Builder(builderClassName = "Builder")
    @Accessors(fluent = true)
    class BasicAuthImpl implements BasicAuth {
        String username;
        String password;
    }

    Target<T> target();

    default Optional<BasicAuth> basicAuth() {
        return Optional.empty();
    }

    default Optional<BasicAuthRequestInterceptor> basicAuthRequestInterceptor() {
        return basicAuth().map(basicAuth -> new BasicAuthRequestInterceptor(
                basicAuth.username(),
                basicAuth.password(),
                Charsets.UTF_8
        ));
    }

    SetterFactory setterFactory();

    Logger logger();

    Retryer retryer();

    Contract contract();

    ErrorDecoder errorDecoder();

    Client client();

    Logger.Level logLevel();

    Decoder decoder();

    Encoder encoder();

    Request.Options options();

    default Collection<RequestInterceptor> requestInterceptors() {
        return Collections.emptyList();
    }

    /**
     * A simple and highly configurable implementation of {@link ClientConfig}
     * with reasonable default values.
     *
     * @param <T> the type of the client
     * @author Alois Leitner
     */
    @Getter
    @Builder(builderClassName = "Builder")
    @Accessors(fluent = true)
    @EqualsAndHashCode
    class ConfigurableClientConfig<T> implements ClientConfig<T> {
        private static final ObjectMapper defaultObjectMapper = new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .configure(SerializationFeature.INDENT_OUTPUT, true)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        @NonNull
        private Target<T> target;
        private Decoder decoder;
        private Encoder encoder;
        private BasicAuth basicAuth;

        @Default
        private SetterFactory setterFactory = new DefaultSetterFactory();

        @Default
        private Logger logger = new Slf4jLogger();

        @Default
        private Logger.Level logLevel = Logger.Level.FULL;

        @Default
        private Retryer retryer = new Retryer.Default();

        @Default
        private Contract contract = new Contract.Default();

        @Default
        private ErrorDecoder errorDecoder = new ErrorDecoder.Default();

        @Default
        private Client client = new OkHttpClient();

        @Default
        private Request.Options options = new Request.Options();

        @Singular
        private Collection<RequestInterceptor> requestInterceptors;

        @Override
        public Optional<BasicAuth> basicAuth() {
            return Optional.ofNullable(basicAuth);
        }

        @Override
        public Decoder decoder() {
            return Optional.ofNullable(decoder).orElseGet(() -> new JacksonDecoder(defaultObjectMapper));
        }

        @Override
        public Encoder encoder() {
            return Optional.ofNullable(encoder).orElseGet(() -> new JacksonEncoder(defaultObjectMapper));
        }

        private static final class DefaultSetterFactory implements SetterFactory {
            private static int DEFAULT_THREAD_POOL_SIZE = 10;
            private static int DEFAULT_TIMEOUT_IN_MS = (int) SECONDS.toMillis(30);

            @Override
            public HystrixCommand.Setter create(Target<?> target, Method method) {
                String groupKey = target.name();
                String commandKey = Feign.configKey(target.type(), method);

                HystrixThreadPoolProperties.Setter threadPoolProperties = HystrixThreadPoolProperties.Setter()
                        .withCoreSize(DEFAULT_THREAD_POOL_SIZE);

                HystrixCommandProperties.Setter commandProperties = HystrixCommandProperties.Setter()
                        .withFallbackEnabled(false)
                        .withExecutionTimeoutEnabled(true)
                        .withExecutionTimeoutInMilliseconds(DEFAULT_TIMEOUT_IN_MS)
                        .withExecutionIsolationStrategy(THREAD)
                        .withExecutionIsolationThreadInterruptOnTimeout(true);

                return HystrixCommand.Setter
                        .withGroupKey(HystrixCommandGroupKey.Factory.asKey(groupKey))
                        .andCommandKey(HystrixCommandKey.Factory.asKey(commandKey))
                        .andThreadPoolPropertiesDefaults(threadPoolProperties)
                        .andCommandPropertiesDefaults(commandProperties);
            }
        }
    }
}
