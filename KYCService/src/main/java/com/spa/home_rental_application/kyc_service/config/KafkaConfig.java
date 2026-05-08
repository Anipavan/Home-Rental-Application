package com.spa.home_rental_application.kyc_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.UserServiceEvents.UserProfileCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Hard-wired Kafka consumer setup for KYC Service.
 *
 * <p>Why this class exists at all: the previous attempt configured the
 * deserializers entirely in {@code application.yaml} via
 * {@code spring.kafka.consumer.value-deserializer:
 * ErrorHandlingDeserializer} plus {@code spring.kafka.consumer.properties.
 * spring.json.value.default.type}. That setup tripped two separate Spring
 * Boot quirks:
 *
 * <ol>
 *   <li>Spring Boot's relaxed-binding for {@code Map<String,String>}
 *       sometimes splits dotted keys into nested maps, so
 *       {@code spring.json.value.default.type} never made it into the flat
 *       Kafka properties map that {@link JsonDeserializer#configure} reads.</li>
 *   <li>Even when the key did make it through, the wrapping
 *       {@link ErrorHandlingDeserializer} re-instantiates the delegate from
 *       the {@code spring.deserializer.value.delegate.class} property and
 *       the constructed delegate doesn't always pick up the type-info
 *       props the way the surrounding code expects.</li>
 * </ol>
 *
 * <p>The runtime symptom is the listener container exploding on the first
 * poll with {@code No type information in headers and no default type
 * provided} — the deserializer was created but had no target class, so
 * any record (including a healthy one) blows up.
 *
 * <p>The fix: stop relying on yaml property propagation and build the
 * deserializers as Spring beans here, with the target type set
 * programmatically. We then publish a custom {@code ConsumerFactory} and
 * {@code ConcurrentKafkaListenerContainerFactory} (named
 * {@code kafkaListenerContainerFactory}, matching the {@code containerFactory}
 * attribute on {@code @KafkaListener} in {@code UserRegisteredConsumer}).
 *
 * <p>The yaml is still the single source of truth for the broker URL,
 * group id, and trusted packages — pulled via
 * {@link KafkaProperties#buildConsumerProperties}.
 */
@Configuration
@Slf4j
public class KafkaConfig {

    @Value("${spring.json.trusted.packages:com.spa.home_rental_application.KafkaEvents.*}")
    private String trustedPackages;

    /**
     * The {@link JsonDeserializer} we hand to Kafka. We construct it with
     * the target type fixed at compile time and {@code useTypeHeaders=false}
     * because the producer publishes without {@code __TypeId__} headers.
     *
     * <p>The {@code user-events} topic carries three event shapes
     * ({@code UserProfileCreatedEvent}, {@code UserProfileUpdatedEvent},
     * {@code OwnerRegisteredEvent}) but they all share the
     * {@code eventType / userId / timestamp} fields — and
     * {@code UserRegisteredConsumer} only acts on the
     * {@code user.profile.created} eventType, ignoring the rest. So
     * deserialising every record into {@code UserProfileCreatedEvent} is
     * safe; the consumer's null-userId guard filters out the rest.
     */
    @Bean
    public JsonDeserializer<UserProfileCreatedEvent> userEventDeserializer() {
        ObjectMapper mapper = new ObjectMapper();
        // LocalDateTime / Instant support — without this, Jackson would
        // refuse to deserialise the timestamp field on UserProfileCreatedEvent.
        mapper.registerModule(new JavaTimeModule());

        JsonDeserializer<UserProfileCreatedEvent> jd =
                new JsonDeserializer<>(UserProfileCreatedEvent.class, mapper);
        jd.setUseTypeHeaders(false);
        jd.addTrustedPackages(trustedPackages.split(","));
        return jd;
    }

    /**
     * Consumer factory that wraps {@link #userEventDeserializer()} in
     * {@link ErrorHandlingDeserializer} so a poison-pill record turns into
     * a logged warning instead of a crash. Broker URL, group id, and
     * common consumer props are pulled from the auto-configured
     * {@link KafkaProperties} (i.e. from {@code application.yaml}) so this
     * class stays environment-agnostic.
     */
    @Bean
    public ConsumerFactory<String, UserProfileCreatedEvent> kycConsumerFactory(
            KafkaProperties kafkaProperties,
            JsonDeserializer<UserProfileCreatedEvent> userEventDeserializer) {

        Map<String, Object> props = new HashMap<>(
                kafkaProperties.buildConsumerProperties(null));
        // Override the deserializer classes — the yaml may try to set them
        // too, but our concrete deserializer beans win because we pass
        // them to the DefaultKafkaConsumerFactory constructor below.
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                ErrorHandlingDeserializer.class);

        ErrorHandlingDeserializer<String> keyDeser =
                new ErrorHandlingDeserializer<>(new StringDeserializer());
        ErrorHandlingDeserializer<UserProfileCreatedEvent> valueDeser =
                new ErrorHandlingDeserializer<>(userEventDeserializer);

        return new DefaultKafkaConsumerFactory<>(props, keyDeser, valueDeser);
    }

    /**
     * Container factory referenced by
     * {@code @KafkaListener(containerFactory="kafkaListenerContainerFactory")}
     * in {@code UserRegisteredConsumer}. The bean name must match exactly.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserProfileCreatedEvent>
            kafkaListenerContainerFactory(
                    ConsumerFactory<String, UserProfileCreatedEvent> kycConsumerFactory,
                    CommonErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, UserProfileCreatedEvent> f =
                new ConcurrentKafkaListenerContainerFactory<>();
        f.setConsumerFactory(kycConsumerFactory);
        f.setCommonErrorHandler(kafkaErrorHandler);
        return f;
    }

    /**
     * Tells the listener container what to do with poison-pill records.
     *
     * <p>{@link DeserializationException} and {@link SerializationException}
     * are <b>not retried</b> ({@code FixedBackOff(0,0)}) — re-deserialising
     * the same bytes will always fail, so retrying just spams the log.
     * The bad record is logged once at WARN and the consumer moves on.
     *
     * <p>Without this bean Spring uses its default handler with
     * {@code FixedBackOff(0L, 9L)}, so a single poison-pill record
     * produces 10 stack traces in the log even though the consumer
     * ultimately recovers. That noise is what was making it look like
     * the service was crashing.
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler() {
        DefaultErrorHandler handler = new DefaultErrorHandler(
                (record, exception) -> log.warn(
                        "Skipping un-deserialisable record on topic={} partition={} offset={}: {}",
                        record.topic(), record.partition(), record.offset(),
                        exception.getMessage()),
                new FixedBackOff(0L, 0L));
        handler.addNotRetryableExceptions(
                DeserializationException.class,
                SerializationException.class,
                ClassCastException.class,
                IllegalStateException.class);
        return handler;
    }
}
