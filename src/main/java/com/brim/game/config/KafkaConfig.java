package com.brim.game.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.support.converter.JsonMessageConverter;
import org.springframework.kafka.support.converter.RecordMessageConverter;

@Configuration
public class KafkaConfig {

    public static final String TOPIC_GAME_MOVE = "game.move";
    public static final String TOPIC_GAME_STATE = "game.state";
    public static final String TOPIC_GAME_EVENT = "game.event";
    public static final String TOPIC_TURN_BASED = "game.turn-based";
    public static final String TOPIC_LEADERBOARD = "game.leaderboard";

    @Bean
    public org.springframework.kafka.listener.DefaultErrorHandler kafkaErrorHandler(
            org.springframework.kafka.core.KafkaTemplate<String, Object> template) {
        var recoverer = new org.springframework.kafka.listener.DeadLetterPublishingRecoverer(template,
                (record, error) -> new org.apache.kafka.common.TopicPartition(record.topic() + ".DLT", record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        return new org.springframework.kafka.listener.DefaultErrorHandler(recoverer,
                new org.springframework.util.backoff.FixedBackOff(1000, 2));
    }

    @Bean
    public org.springframework.kafka.core.KafkaAdmin.NewTopics deadLetterTopics() {
        return new org.springframework.kafka.core.KafkaAdmin.NewTopics(
            TopicBuilder.name(TOPIC_GAME_MOVE + ".DLT").partitions(3).replicas(1).build(),
            TopicBuilder.name(TOPIC_GAME_EVENT + ".DLT").partitions(3).replicas(1).build(),
            TopicBuilder.name(TOPIC_TURN_BASED + ".DLT").partitions(3).replicas(1).build(),
            TopicBuilder.name(TOPIC_LEADERBOARD + ".DLT").partitions(3).replicas(1).build());
    }

    @Bean
    public ObjectMapper kafkaObjectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
    }

    @Bean
    public NewTopic gameMoveTopic() {
        return TopicBuilder.name(TOPIC_GAME_MOVE)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic gameStateTopic() {
        return TopicBuilder.name(TOPIC_GAME_STATE)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic gameEventTopic() {
        return TopicBuilder.name(TOPIC_GAME_EVENT)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic turnBasedTopic() {
        return TopicBuilder.name(TOPIC_TURN_BASED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic leaderboardTopic() {
        return TopicBuilder.name(TOPIC_LEADERBOARD).partitions(3).replicas(1).build();
    }
}
