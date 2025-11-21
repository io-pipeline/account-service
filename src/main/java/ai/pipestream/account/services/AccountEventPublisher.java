package ai.pipestream.account.services;

import ai.pipestream.repository.account.AccountEvent;
import io.smallrye.mutiny.subscription.Cancellable;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.jboss.logging.Logger;

import ai.pipestream.grpc.util.KafkaProtobufKeys;

import java.time.Instant;
import java.util.UUID;

/**
 * Publisher for account lifecycle events to Kafka.
 * Events are published in protobuf format with Apicurio schema validation.
 * <p>
 * STRICT TYPING: Keys are strictly enforced as UUIDs via Record&lt;UUID, Value&gt;.
 */
@ApplicationScoped
public class AccountEventPublisher {

    private static final Logger LOG = Logger.getLogger(AccountEventPublisher.class);

    /**
     * Default constructor required for CDI.
     */
    public AccountEventPublisher() {
        // Default constructor for framework usage
    }

    @Inject
    @Channel("account-events")
    MutinyEmitter<Record<UUID, AccountEvent>> accountEventEmitter;

    /**
     * Publish account created event.
     *
     * @param accountId unique identifier for the account
     * @param name display name of the account
     * @param description optional account description (may be {@code null})
     * @return a {@link Cancellable} handle for the send operation
     */
    public Cancellable publishAccountCreated(String accountId, String name, String description) {
        try {
            AccountEvent event = buildBaseEvent(accountId, "created")
                    .setCreated(AccountEvent.Created.newBuilder()
                            .setName(name)
                            .setDescription(description != null ? description : "")
                            .build())
                    .build();

            UUID key = KafkaProtobufKeys.uuid(event);

            LOG.infof("Publishing account created event: accountId=%s (UUID: %s)", accountId, key);
            return accountEventEmitter.sendAndForget(Record.of(key, event));
        } catch (Exception e) {
            LOG.errorf(e, "Error publishing account created event: accountId=%s", accountId);
            throw new RuntimeException("Failed to publish account created event", e);
        }
    }

    /**
     * Publish account updated event.
     *
     * @param accountId unique identifier for the account
     * @param name updated display name of the account
     * @param description updated account description (may be {@code null})
     * @return a {@link Cancellable} handle for the send operation
     */
    public Cancellable publishAccountUpdated(String accountId, String name, String description) {
        try {
            AccountEvent event = buildBaseEvent(accountId, "updated")
                    .setUpdated(AccountEvent.Updated.newBuilder()
                            .setName(name)
                            .setDescription(description != null ? description : "")
                            .build())
                    .build();
            
            UUID key = KafkaProtobufKeys.uuid(event);

            LOG.infof("Publishing account updated event: accountId=%s", accountId);
            return accountEventEmitter.sendAndForget(Record.of(key, event));
        } catch (Exception e) {
            LOG.errorf(e, "Error publishing account updated event: accountId=%s", accountId);
            throw new RuntimeException("Failed to publish account updated event", e);
        }
    }

    /**
     * Publish account inactivated event.
     *
     * @param accountId unique identifier for the account
     * @param reason reason for inactivation (may be {@code null})
     * @return a {@link Cancellable} handle for the send operation
     */
    public Cancellable publishAccountInactivated(String accountId, String reason) {
        try {
            AccountEvent event = buildBaseEvent(accountId, "inactivated")
                    .setInactivated(AccountEvent.Inactivated.newBuilder()
                            .setReason(reason != null ? reason : "")
                            .build())
                    .build();
            
            UUID key = KafkaProtobufKeys.uuid(event);

            LOG.infof("Publishing account inactivated event: accountId=%s, reason=%s", accountId, reason);
            return accountEventEmitter.sendAndForget(Record.of(key, event));
        } catch (Exception e) {
            LOG.errorf(e, "Error publishing account inactivated event: accountId=%s", accountId);
            throw new RuntimeException("Failed to publish account inactivated event", e);
        }
    }

    /**
     * Publish account reactivated event.
     *
     * @param accountId unique identifier for the account
     * @param reason reason for reactivation (may be {@code null})
     * @return a {@link Cancellable} handle for the send operation
     */
    public Cancellable publishAccountReactivated(String accountId, String reason) {
        try {
            AccountEvent event = buildBaseEvent(accountId, "reactivated")
                    .setReactivated(AccountEvent.Reactivated.newBuilder()
                            .setReason(reason != null ? reason : "")
                            .build())
                    .build();
            
            UUID key = KafkaProtobufKeys.uuid(event);

            LOG.infof("Publishing account reactivated event: accountId=%s, reason=%s", accountId, reason);
            return accountEventEmitter.sendAndForget(Record.of(key, event));
        } catch (Exception e) {
            LOG.errorf(e, "Error publishing account reactivated event: accountId=%s", accountId);
            throw new RuntimeException("Failed to publish account reactivated event", e);
        }
    }

    /**
     * Helper to build the base event with ID and Timestamp.
     */
    private AccountEvent.Builder buildBaseEvent(String accountId, String operation) {
        return AccountEvent.newBuilder()
                .setEventId(generateEventId(accountId, operation))
                .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(Instant.now().getEpochSecond())
                        .setNanos(Instant.now().getNano())
                        .build())
                .setAccountId(accountId);
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