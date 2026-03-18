package com.wpanther.orchestrator.infrastructure.config.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.wpanther.orchestrator.infrastructure.adapter.in.messaging.StartSagaCommand;
import com.wpanther.orchestrator.infrastructure.adapter.in.messaging.ConcreteSagaReply;
import com.wpanther.saga.domain.model.SagaReply;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for the orchestrator service.
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    /**
     * Default number of consumer threads for Kafka listener containers.
     */
    private static final int DEFAULT_CONSUMER_CONCURRENCY = 3;

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:orchestrator-service}")
    private String groupId;

    @Value("${spring.kafka.consumer.auto-offset-reset:earliest}")
    private String autoOffsetReset;

    private final ObjectMapper objectMapper;

    public KafkaConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ========== Producer Configuration ==========

    @Bean
    public ProducerFactory<String, com.wpanther.saga.domain.model.SagaCommand> sagaCommandProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        config.put(JsonSerializer.TYPE_MAPPINGS, "SagaCommand:com.wpanther.saga.domain.model.SagaCommand");
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, com.wpanther.saga.domain.model.SagaCommand> sagaCommandKafkaTemplate() {
        return new KafkaTemplate<>(sagaCommandProducerFactory());
    }

    // ========== Consumer Configuration ==========

    @Bean
    public ConsumerFactory<String, SagaReply> sagaReplyConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        // Restrict to known safe packages for SagaReply deserialization
        config.put(JsonDeserializer.TRUSTED_PACKAGES,
            "com.wpanther.orchestrator.infrastructure.adapter.in.messaging," +
            "com.wpanther.saga.domain.model");

        // SagaReply is abstract; map it to ConcreteSagaReply for deserialization
        ObjectMapper replyMapper = objectMapper.copy();
        SimpleModule module = new SimpleModule();
        module.addAbstractTypeMapping(SagaReply.class, ConcreteSagaReply.class);
        replyMapper.registerModule(module);

        return new DefaultKafkaConsumerFactory<>(config,
                new StringDeserializer(),
                new JsonDeserializer<>(SagaReply.class, replyMapper, false));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, SagaReply> sagaReplyKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, SagaReply> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(sagaReplyConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setConcurrency(DEFAULT_CONSUMER_CONCURRENCY);
        return factory;
    }

    // ========== Generic Consumer Factory for Testing ==========

    @Bean
    public ConsumerFactory<String, String> stringConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId + "-string");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    // ========== StartSagaCommand Consumer Factory ==========

    @Bean
    public ConsumerFactory<String, StartSagaCommand> startSagaCommandConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "orchestrator-service");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        // Restrict to known safe packages for StartSagaCommand deserialization
        props.put(JsonDeserializer.TRUSTED_PACKAGES,
            "com.wpanther.orchestrator.infrastructure.adapter.in.messaging");
        return new DefaultKafkaConsumerFactory<>(
            props,
            new StringDeserializer(),
            new JsonDeserializer<>(StartSagaCommand.class, objectMapper, false)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, StartSagaCommand>
            startSagaCommandKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, StartSagaCommand> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(startSagaCommandConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setConcurrency(DEFAULT_CONSUMER_CONCURRENCY);
        return factory;
    }
}
