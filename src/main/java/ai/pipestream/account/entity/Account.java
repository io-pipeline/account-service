package ai.pipestream.account.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * Account entity for multi-tenant support.
 * <p>
 * Represents a tenant account in the system. Each account can own multiple
 * drives (storage buckets) and connectors. Accounts are soft-deleted via
 * the {@code active} field.
 * <p>
 * Database Table: {@code accounts}
 * <p>
 * Timestamps are stored as {@link OffsetDateTime} in the database (TIMESTAMP columns)
 * and mapped to {@code google.protobuf.Timestamp} in the gRPC API.
 */
@Entity
@Table(name = "accounts")
public class Account extends PanacheEntityBase {

    /**
     * Unique account identifier (primary key).
     * Should be a UUID or similar globally unique value.
     */
    @Id
    @Column(name = "account_id", unique = true, nullable = false)
    public String accountId;

    /**
     * Display name for the account.
     * Required field used for UI and logging.
     */
    @Column(name = "name", nullable = false)
    public String name;

    /**
     * Optional description providing additional context about the account.
     */
    @Column(name = "description")
    public String description;

    /**
     * Whether the account is active.
     * Inactive accounts are soft-deleted and should not be used for new operations.
     * Default: true
     */
    @Column(name = "active", nullable = false)
    public Boolean active = true;

    /**
     * Timestamp when the account was created.
     * Set automatically on entity creation.
     */
    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    /**
     * Timestamp when the account was last updated.
     * Updated automatically when the account is modified.
     */
    @Column(name = "updated_at")
    public OffsetDateTime updatedAt;

    /**
     * Default constructor for JPA.
     */
    public Account() {}

    /**
     * Create a new active account with current timestamps.
     *
     * @param accountId Unique identifier
     * @param name Display name
     * @param description Optional description
     */
    public Account(String accountId, String name, String description) {
        this.accountId = accountId;
        this.name = name;
        this.description = description;
        this.active = true;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }
}
