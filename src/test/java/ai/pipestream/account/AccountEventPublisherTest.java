package ai.pipestream.account;

import ai.pipestream.grpc.wiremock.MockServiceTestResource;
import ai.pipestream.repository.account.AccountEvent;
import ai.pipestream.repository.account.AccountServiceGrpc;
import ai.pipestream.repository.account.CreateAccountRequest;

// Standard Kafka and Apicurio classes
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer;

import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

// Awaitility and Hamcrest Matchers
import org.awaitility.Awaitility;
import org.hamcrest.Matchers;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(MockServiceTestResource.class)
public class AccountEventPublisherTest {

    @GrpcClient("account-manager")
    AccountServiceGrpc.AccountServiceBlockingStub accountService;

    // Inject config properties to build our test consumer
    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @ConfigProperty(name = "mp.messaging.outgoing.account-events.apicurio.registry.url")
    String apicurioRegistryUrl;

    private KafkaConsumer<String, AccountEvent> createConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + System.currentTimeMillis());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ProtobufKafkaDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Apicurio specific properties
        props.put("apicurio.registry.url", apicurioRegistryUrl);

        // We are now using the exact same property as your working opensearch-manager consumer
        props.put("apicurio.registry.deserializer.value.return-class", AccountEvent.class.getName());

        return new KafkaConsumer<>(props);
    }
    @Test
    public void testAccountCreatedEventIsPublished() {
        // ARRANGE: Create a unique ID for this specific test run
        String testAccountId = "test-kafka-create-" + System.currentTimeMillis();

        try (KafkaConsumer<String, AccountEvent> consumer = createConsumer()) {
            consumer.subscribe(Collections.singletonList("account-events"));

            // ACT: Call the gRPC endpoint which should produce the message
            var request = CreateAccountRequest.newBuilder()
                    .setAccountId(testAccountId)
                    .setName("Kafka Test Account")
                    .build();
            accountService.createAccount(request);

            // ASSERT: Use Awaitility to poll until we find our specific message
            AccountEvent foundEvent = Awaitility.await()
                    .atMost(10, TimeUnit.SECONDS)
                    .pollInterval(100, TimeUnit.MILLISECONDS)
                    .until(() -> pollForMessage(consumer, testAccountId), Matchers.notNullValue());

            // Perform final assertions on the message we found
            assertNotNull(foundEvent);
            assertTrue(foundEvent.hasCreated(), "Event should be a Created event");
            assertEquals("Kafka Test Account", foundEvent.getCreated().getName());
            assertEquals(testAccountId, foundEvent.getAccountId());
        }
    }

    /**
     * Helper method for Awaitility. It polls Kafka once and looks for a message
     * with a specific accountId.
     * @return The found AccountEvent, or null if not found in this poll.
     */
    private AccountEvent pollForMessage(KafkaConsumer<String, AccountEvent> consumer, String accountId) {
        ConsumerRecords<String, AccountEvent> records = consumer.poll(Duration.ofMillis(100));
        for (ConsumerRecord<String, AccountEvent> record : records) {
            if (record.value().getAccountId().equals(accountId)) {
                return record.value(); // Found it!
            }
        }
        return null; // Didn't find it
    }
}