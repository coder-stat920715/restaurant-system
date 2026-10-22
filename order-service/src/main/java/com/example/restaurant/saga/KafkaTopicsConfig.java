package com.example.restaurant.saga;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;

/** Pre-create topics so consumers do not wait for metadata refresh after Debezium auto-creates them. */
@Configuration
class KafkaTopicsConfig {
    @Bean
    KafkaAdmin.NewTopics sagaTopics() {
        return new KafkaAdmin.NewTopics(
            new NewTopic("restaurant.events.order", 3, (short) 1),
            new NewTopic("restaurant.events.saga-payment", 3, (short) 1),
            new NewTopic("restaurant.events.saga-kitchen", 3, (short) 1),
            new NewTopic("restaurant.events.saga-reply", 3, (short) 1));
    }
}
