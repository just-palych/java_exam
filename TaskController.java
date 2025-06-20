import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final JdbcTemplate jdbcTemplate;
    private final ConsumerFactory<String, String> consumerFactory;

    public TaskController(JdbcTemplate jdbcTemplate, 
                        ConsumerFactory<String, String> consumerFactory) {
        this.jdbcTemplate = jdbcTemplate;
        this.consumerFactory = consumerFactory;
    }

    @PostMapping("/kafka-postgres-count")
    public KafkaPostgresCountResponse countMessagesInPostgres(
            @RequestParam String bootstrapServers,
            @RequestParam String topicName) {

        Map<String, Object> configs = new HashMap<>(consumerFactory.getConfigurationProperties());
        configs.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configs.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configs.put(ConsumerConfig.GROUP_ID_CONFIG, "temp-group-" + System.currentTimeMillis());

        Consumer<String, String> consumer = null;
        int messageCount = 0;
        int matchingRecordsCount = 0;

        try {
            ConsumerFactory<String, String> factory = 
                new DefaultKafkaConsumerFactory<>(configs);
            consumer = factory.createConsumer();

            consumer.subscribe(Collections.singletonList(topicName));
            ConsumerRecords<String, String> records = 
                consumer.poll(Duration.ofSeconds(5));

            messageCount = records.count();

            if (messageCount > 0) {
                String sql = "SELECT COUNT(*) FROM messages WHERE content LIKE '%test%'";
                matchingRecordsCount = jdbcTemplate.queryForObject(sql, Integer.class);
            }

        } catch (Exception e) {
            throw new RuntimeException("Error processing Kafka messages", e);
        } finally {
            if (consumer != null) {
                consumer.close();
            }
        }

        return new KafkaPostgresCountResponse(messageCount, matchingRecordsCount);
    }

    public record KafkaPostgresCountResponse(
        int kafkaMessagesRead,
        int postgresMatchingRecords
    ) {}
}