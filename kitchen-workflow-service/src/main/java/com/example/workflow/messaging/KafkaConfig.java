package com.example.workflow.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
class KafkaConfig {

    @Bean
    DefaultErrorHandler errorHandler(KafkaTemplate<String, String> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
            (rec, ex) -> new TopicPartition(rec.topic() + ".DLT", -1));
        var backoff = new ExponentialBackOff(500, 2.0);
        backoff.setMaxAttempts(5);
        return new DefaultErrorHandler(recoverer, backoff);
    }

    @Bean
    KafkaAdmin.NewTopics workflowTopics() {
        return new KafkaAdmin.NewTopics(new NewTopic("restaurant.events.order", 3, (short) 1));
    }
}
