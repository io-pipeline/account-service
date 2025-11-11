package ai.pipestream.account.services;

import ai.pipestream.repository.account.AccountEvent;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.mutiny.subscription.Cancellable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

import java.time.Instant;

/**
 * Publisher for account lifecycle events to Kafka.
 * Events are published in protobuf format with Apicurio schema validation.
 */
@ApplicationScoped
public class AccountEventPublisher {

    private static final Logger LOG = Logger.getLogger(AccountEventPublisher.class);
    
    @Inject
    @Channel("account-events")
    MutinyEmitter<AccountEvent> accountEventEmitter;
    
    /**
     * Publish account created event.
     */
    public Cancellable publishAccountCreated(String accountId, String name, String description) {
        try {
            AccountEvent.Created created = AccountEvent.Created.newBuilder()
                .setName(name)
                .setDescription(description != null ? description : "")
                .build();
            
            AccountEvent event = AccountEvent.newBuilder()
                .setEventId(generateEventId(accountId, "created"))
                .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
                    .setSeconds(Instant.now().getEpochSecond())
                    .setNanos(Instant.now().getNano())
                    .build())
                .setAccountId(accountId)
                .setCreated(created)
                .build();
            
            Message<AccountEvent> message = Message.of(event)
                .addMetadata(OutgoingKafkaRecordMetadata.<String>builder()
                    .withKey(accountId)
                    .withTopic("account-events")
                    .build());
            
            LOG.infof("Publishing account created event: accountId=%s", accountId);
            return accountEventEmitter.sendMessageAndForget(message);
        } catch (Exception e) {
            LOG.errorf(e, "Error publishing account created event: accountId=%s", accountId);
            throw new RuntimeException("Failed to publish account created event", e);
        }
    }
    
    /**
     * Publish account updated event.
     */
    public Cancellable publishAccountUpdated(String accountId, String name, String description) {
        try {
            AccountEvent.Updated updated = AccountEvent.Updated.newBuilder()
                .setName(name)
                .setDescription(description != null ? description : "")
                .build();
            
            AccountEvent event = AccountEvent.newBuilder()
                .setEventId(generateEventId(accountId, "updated"))
                .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
                    .setSeconds(Instant.now().getEpochSecond())
                    .setNanos(Instant.now().getNano())
                    .build())
                .setAccountId(accountId)
                .setUpdated(updated)
                .build();
            
            Message<AccountEvent> message = Message.of(event)
                .addMetadata(OutgoingKafkaRecordMetadata.<String>builder()
                    .withKey(accountId)
                    .withTopic("account-events")
                    .build());
            
            LOG.infof("Publishing account updated event: accountId=%s", accountId);
            return accountEventEmitter.sendMessageAndForget(message);
        } catch (Exception e) {
            LOG.errorf(e, "Error publishing account updated event: accountId=%s", accountId);
            throw new RuntimeException("Failed to publish account updated event", e);
        }
    }
    
    /**
     * Publish account inactivated event.
     */
    public Cancellable publishAccountInactivated(String accountId, String reason) {
        try {
            AccountEvent.Inactivated inactivated = AccountEvent.Inactivated.newBuilder()
                .setReason(reason != null ? reason : "")
                .build();
            
            AccountEvent event = AccountEvent.newBuilder()
                .setEventId(generateEventId(accountId, "inactivated"))
                .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
                    .setSeconds(Instant.now().getEpochSecond())
                    .setNanos(Instant.now().getNano())
                    .build())
                .setAccountId(accountId)
                .setInactivated(inactivated)
                .build();
            
            Message<AccountEvent> message = Message.of(event)
                .addMetadata(OutgoingKafkaRecordMetadata.<String>builder()
                    .withKey(accountId)
                    .withTopic("account-events")
                    .build());
            
            LOG.infof("Publishing account inactivated event: accountId=%s, reason=%s", accountId, reason);
            return accountEventEmitter.sendMessageAndForget(message);
        } catch (Exception e) {
            LOG.errorf(e, "Error publishing account inactivated event: accountId=%s", accountId);
            throw new RuntimeException("Failed to publish account inactivated event", e);
        }
    }
    
    /**
     * Publish account reactivated event.
     */
    public Cancellable publishAccountReactivated(String accountId, String reason) {
        try {
            AccountEvent.Reactivated reactivated = AccountEvent.Reactivated.newBuilder()
                .setReason(reason != null ? reason : "")
                .build();
            
            AccountEvent event = AccountEvent.newBuilder()
                .setEventId(generateEventId(accountId, "reactivated"))
                .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
                    .setSeconds(Instant.now().getEpochSecond())
                    .setNanos(Instant.now().getNano())
                    .build())
                .setAccountId(accountId)
                .setReactivated(reactivated)
                .build();
            
            Message<AccountEvent> message = Message.of(event)
                .addMetadata(OutgoingKafkaRecordMetadata.<String>builder()
                    .withKey(accountId)
                    .withTopic("account-events")
                    .build());
            
            LOG.infof("Publishing account reactivated event: accountId=%s, reason=%s", accountId, reason);
            return accountEventEmitter.sendMessageAndForget(message);
        } catch (Exception e) {
            LOG.errorf(e, "Error publishing account reactivated event: accountId=%s", accountId);
            throw new RuntimeException("Failed to publish account reactivated event", e);
        }
    }
    
    /**
     * Generate deterministic event ID: hash(account_id + operation + timestamp_millis)
     */
    private String generateEventId(String accountId, String operation) {
        long timestampMillis = System.currentTimeMillis();
        String input = accountId + operation + timestampMillis;
        return String.valueOf(input.hashCode());
    }
}
