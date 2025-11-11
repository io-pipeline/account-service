package ai.pipestream.account.repository;

import ai.pipestream.account.entity.Account;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;

/**
 * Repository for Account entity CRUD operations.
 * <p>
 * Provides transactional access to the accounts table with support for:
 * <ul>
 *   <li>Idempotent account creation</li>
 *   <li>Account lookup by ID</li>
 *   <li>Soft deletion via inactivation</li>
 * </ul>
 * <p>
 * This repository follows the Panache active record pattern where the entity
 * itself provides persistence methods (persist, delete, etc.) while this
 * repository adds business logic and query methods.
 * <p>
 * Thread Safety: All methods are transactional and safe for concurrent access.
 * Hibernate manages entity state and ensures consistency.
 */
@ApplicationScoped
public class AccountRepository {

    private static final Logger LOG = Logger.getLogger(AccountRepository.class);

    @Inject
    EntityManager entityManager;

    /**
     * Create a new account or return existing if accountId already exists.
     * <p>
     * This method is idempotent - calling it multiple times with the same
     * accountId will return the existing account without modification.
     *
     * @param accountId Unique identifier for the account
     * @param name Display name for the account (required)
     * @param description Optional account description
     * @return The created or existing Account entity
     */
    @Transactional
    public Account createAccount(String accountId, String name, String description) {
        Account existing = findByAccountId(accountId);
        if (existing != null) {
            LOG.debugf("Account already exists: %s", accountId);
            return existing;
        }

        Account account = new Account(accountId, name, description);
        account.persist();
        LOG.infof("Created account: %s", accountId);
        return account;
    }

    /**
     * Find account by ID.
     * <p>
     * Returns both active and inactive accounts. Use the {@code active} field
     * to determine account status.
     *
     * @param accountId Unique identifier for the account
     * @return Account entity or null if not found
     */
    @Transactional
    public Account findByAccountId(String accountId) {
        try {
            return entityManager.createQuery(
                "SELECT a FROM Account a WHERE a.accountId = :accountId", Account.class)
                .setParameter("accountId", accountId)
                .getSingleResult();
        } catch (jakarta.persistence.NoResultException e) {
            return null;
        }
    }

    /**
     * Inactivate an account (soft delete).
     * <p>
     * This method is idempotent - calling it on an already inactive account
     * will succeed without error. Sets active=false and updates the
     * updatedAt timestamp.
     * <p>
     * Note: Drive inactivation is handled separately by repo-service via
     * gRPC calls or events. This method only updates the account status.
     *
     * @param accountId Unique identifier for the account
     * @param reason Reason for inactivation (logged but not stored)
     * @return true if account was inactivated or already inactive, false if account not found
     */
    @Transactional
    public boolean inactivateAccount(String accountId, String reason) {
        Account account = findByAccountId(accountId);
        if (account == null) {
            LOG.warnf("Cannot inactivate account - not found: %s", accountId);
            return false;
        }

        if (!account.active) {
            LOG.debugf("Account already inactive: %s", accountId);
            return true; // Idempotent
        }

        account.active = false;
        account.updatedAt = OffsetDateTime.now();
        account.persist();

        LOG.infof("Inactivated account: %s, reason: %s", accountId, reason);

        // Note: Drive inactivation will be handled by repo-service via gRPC calls
        // or events when needed

        return true;
    }

    /**
     * Reactivate an account.
     * <p>
     * This method is idempotent - calling it on an already active account
     * will succeed without error. Sets active=true and updates the
     * updatedAt timestamp.
     *
     * @param accountId Unique identifier for the account
     * @param reason Reason for reactivation (logged but not stored)
     * @return true if account was reactivated or already active, false if account not found
     */
    @Transactional
    public boolean reactivateAccount(String accountId, String reason) {
        Account account = findByAccountId(accountId);
        if (account == null) {
            LOG.warnf("Cannot reactivate account - not found: %s", accountId);
            return false;
        }

        if (account.active) {
            LOG.debugf("Account already active: %s", accountId);
            return true; // Idempotent
        }

        account.active = true;
        account.updatedAt = OffsetDateTime.now();
        account.persist();

        LOG.infof("Reactivated account: %s, reason: %s", accountId, reason);

        return true;
    }

    /**
     * Update mutable account fields.
     *
     * @param accountId unique identifier for the account
     * @param name new display name (required)
     * @param description new description (optional)
     * @return updated Account or null if not found
     */
    @Transactional
    public Account updateAccount(String accountId, String name, String description) {
        Account account = findByAccountId(accountId);
        if (account == null) {
            LOG.warnf("Cannot update account - not found: %s", accountId);
            return null;
        }

        boolean changed = false;
        if (name != null && !name.equals(account.name)) {
            account.name = name;
            changed = true;
        }

        // Allow clearing description by passing empty string
        if (description != null && !description.equals(account.description)) {
            account.description = description;
            changed = true;
        }

        if (changed) {
            account.updatedAt = OffsetDateTime.now();
            account.persist();
            LOG.infof("Updated account %s", accountId);
        } else {
            LOG.debugf("No changes detected when updating account %s", accountId);
        }

        return account;
    }

    /**
     * List accounts with optional filtering.
     *
     * @param query optional partial search against accountId and name
     * @param includeInactive whether to include inactive accounts
     * @param pageSize maximum number of results to return
     * @param offset zero-based result offset for pagination
     * @return list of matching accounts
     */
    @Transactional
    public java.util.List<Account> listAccounts(String query, boolean includeInactive, int pageSize, int offset) {
        StringBuilder jpql = new StringBuilder("SELECT a FROM Account a");
        boolean hasCondition = false;

        if (query != null && !query.isBlank()) {
            jpql.append(" WHERE (LOWER(a.accountId) LIKE :query OR LOWER(a.name) LIKE :query)");
            hasCondition = true;
        }

        if (!includeInactive) {
            jpql.append(hasCondition ? " AND" : " WHERE");
            jpql.append(" a.active = true");
        }

        jpql.append(" ORDER BY a.createdAt DESC");

        var typedQuery = entityManager.createQuery(jpql.toString(), Account.class)
            .setFirstResult(Math.max(offset, 0))
            .setMaxResults(Math.max(pageSize, 1));

        if (query != null && !query.isBlank()) {
            typedQuery.setParameter("query", "%" + query.trim().toLowerCase() + "%");
        }

        return typedQuery.getResultList();
    }

    /**
     * Count accounts for pagination metadata.
     */
    @Transactional
    public long countAccounts(String query, boolean includeInactive) {
        StringBuilder jpql = new StringBuilder("SELECT COUNT(a) FROM Account a");
        boolean hasCondition = false;

        if (query != null && !query.isBlank()) {
            jpql.append(" WHERE (LOWER(a.accountId) LIKE :query OR LOWER(a.name) LIKE :query)");
            hasCondition = true;
        }

        if (!includeInactive) {
            jpql.append(hasCondition ? " AND" : " WHERE");
            jpql.append(" a.active = true");
        }

        var typedQuery = entityManager.createQuery(jpql.toString(), Long.class);

        if (query != null && !query.isBlank()) {
            typedQuery.setParameter("query", "%" + query.trim().toLowerCase() + "%");
        }

        return typedQuery.getSingleResult();
    }
}
