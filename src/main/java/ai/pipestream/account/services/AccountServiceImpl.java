package ai.pipestream.account.services;

import com.google.protobuf.Timestamp;
import io.grpc.Status;
import ai.pipestream.repository.account.CreateAccountRequest;
import ai.pipestream.repository.account.CreateAccountResponse;
import ai.pipestream.repository.account.GetAccountRequest;
import ai.pipestream.repository.account.InactivateAccountRequest;
import ai.pipestream.repository.account.InactivateAccountResponse;
import ai.pipestream.repository.account.ListAccountsRequest;
import ai.pipestream.repository.account.ListAccountsResponse;
import ai.pipestream.repository.account.MutinyAccountServiceGrpc;
import ai.pipestream.repository.account.ReactivateAccountRequest;
import ai.pipestream.repository.account.ReactivateAccountResponse;
import ai.pipestream.repository.account.UpdateAccountRequest;
import ai.pipestream.repository.account.UpdateAccountResponse;
import ai.pipestream.account.entity.Account;
import ai.pipestream.account.repository.AccountRepository;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
// S3 feature temporarily disabled
// import software.amazon.awssdk.services.s3.S3Client;
// import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

/**
 * gRPC service implementation for Account Management.
 * <p>
 * Provides account CRUD operations for multi-tenant support. This service
 * is responsible for managing tenant accounts that own drives and connectors.
 * <p>
 * All operations run on worker threads to avoid blocking the event loop.
 * Entity ↔ Proto mapping is handled inline with {@link Timestamp} conversion.
 * <p>
 * Proto Definition: grpc/grpc-stubs/src/main/proto/repository/account/account_service.proto
 */
@GrpcService
public class AccountServiceImpl extends MutinyAccountServiceGrpc.AccountServiceImplBase {

    private static final Logger LOG = Logger.getLogger(AccountServiceImpl.class);
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;

    @Inject
    AccountRepository accountRepository;

    // S3 feature temporarily disabled
    // @Inject
    // S3Client s3Client;

    @Inject
    AccountEventPublisher eventPublisher;

    /**
     * Default constructor required for gRPC/CDI.
     */
    public AccountServiceImpl() {
        // Default constructor for framework usage
    }

    // S3 feature temporarily disabled
    // @ConfigProperty(name = "account.dev.s3-bucket-creation", defaultValue = "false")
    // boolean devS3BucketCreation;

    /**
     * Create a new account.
     * <p>
     * Behavior:
     * <ul>
     *   <li>If account_id is provided and exists: returns existing account with created=false</li>
     *   <li>If account_id is provided and doesn't exist: creates new account with that ID</li>
     *   <li>Validates that name is not empty</li>
     * </ul>
     * <p>
     * In dev mode only, can optionally create an S3 bucket for the account.
     *
     * @param request CreateAccountRequest with account_id, name, and optional description
     * @return CreateAccountResponse with the account and created flag
     */
    @Override
    public Uni<CreateAccountResponse> createAccount(CreateAccountRequest request) {
        return Uni.createFrom().item(() -> {
            LOG.infof("Creating account: accountId=%s, name=%s", request.getAccountId(), request.getName());

            if (request.getAccountId() == null || request.getAccountId().isEmpty()) {
                throw new IllegalArgumentException("Account ID is required");
            }
            if (request.getName() == null || request.getName().isEmpty()) {
                throw new IllegalArgumentException("Account name is required");
            }

            Account existing = accountRepository.findByAccountId(request.getAccountId());
            boolean wasCreated = existing == null;

            Account account = wasCreated
                ? accountRepository.createAccount(
                    request.getAccountId(),
                    request.getName(),
                    request.getDescription()
                )
                : existing;

            // DEV-ONLY: Create S3 bucket for development (temporarily disabled)
            // if (wasCreated && devS3BucketCreation) {
            //     try {
            //         createS3BucketForAccount(account.accountId);
            //         LOG.infof("Created S3 bucket for account: %s (dev-only)", account.accountId);
            //     } catch (Exception e) {
            //         LOG.warnf(e, "Failed to create S3 bucket for account %s (dev-only): %s", 
            //             account.accountId, e.getMessage());
            //         // Don't fail account creation if S3 bucket creation fails
            //     }
            // }

            LOG.infof("Account %s: accountId=%s", wasCreated ? "created" : "already existed",
                request.getAccountId());

            // Publish account created event if newly created
            if (wasCreated) {
                try {
                    eventPublisher.publishAccountCreated(account.accountId, account.name, account.description);
                } catch (Exception e) {
                    LOG.warnf(e, "Failed to publish account created event for %s", account.accountId);
                    // Don't fail account creation if event publishing fails
                }
            }

            return CreateAccountResponse.newBuilder()
                .setAccount(toProtoAccount(account))
                .setCreated(wasCreated)
                .build();
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    /**
     * Retrieve an account by ID.
     * <p>
     * Returns both active and inactive accounts. Throws NOT_FOUND status
     * if the account doesn't exist.
     *
     * @param request GetAccountRequest with account_id
     * @return Account proto message
     * @throws io.grpc.StatusRuntimeException with NOT_FOUND if account doesn't exist
     */
    @Override
    public Uni<ai.pipestream.repository.account.Account> getAccount(GetAccountRequest request) {
        return Uni.createFrom().item(() -> {
            LOG.infof("Getting account: accountId=%s", request.getAccountId());

            Account account = accountRepository.findByAccountId(request.getAccountId());
            if (account == null) {
                throw Status.NOT_FOUND
                    .withDescription("Account not found: " + request.getAccountId())
                    .asRuntimeException();
            }

            LOG.infof("Account found - ID: %s, Active: %s, Active type: %s", 
                account.accountId, account.active, account.active != null ? account.active.getClass().getSimpleName() : "null");

            return toProtoAccount(account);
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    /**
     * Inactivate an account (soft delete).
     * <p>
     * Sets active=false and updates the timestamp. This method is idempotent -
     * calling it on an already inactive account will succeed.
     * <p>
     * Note: Drive inactivation is deferred to repo-service. The drives_affected
     * field returns 0 in this MVP implementation.
     *
     * @param request InactivateAccountRequest with account_id and reason
     * @return InactivateAccountResponse with success status and message
     */
    @Override
    public Uni<InactivateAccountResponse> inactivateAccount(InactivateAccountRequest request) {
        return Uni.createFrom().item(() -> {
            LOG.infof("Inactivating account: accountId=%s, reason=%s",
                request.getAccountId(), request.getReason());

            boolean success = accountRepository.inactivateAccount(request.getAccountId(), request.getReason());

            if (success) {
                // Note: Drive counting and inactivation will be handled by repo-service
                // via gRPC calls or events when needed. For now, we just return success
                // without drive information.
                
                LOG.infof("Inactivated account %s", request.getAccountId());

                // Publish account inactivated event
                try {
                    eventPublisher.publishAccountInactivated(request.getAccountId(), request.getReason());
                } catch (Exception e) {
                    LOG.warnf(e, "Failed to publish account inactivated event for %s", request.getAccountId());
                    // Don't fail account inactivation if event publishing fails
                }

                return InactivateAccountResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Account inactivated successfully")
                    .setDrivesAffected(0) // Will be updated when repo-service integration is added
                    .build();
            } else {
                return InactivateAccountResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Account not found: " + request.getAccountId())
                    .setDrivesAffected(0)
                    .build();
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    /**
     * Reactivate an account.
     * <p>
     * Sets active=true and updates the timestamp. This method is idempotent -
     * calling it on an already active account will succeed.
     *
     * @param request ReactivateAccountRequest with account_id and reason
     * @return ReactivateAccountResponse with success status and message
     */
    @Override
    public Uni<ReactivateAccountResponse> reactivateAccount(ReactivateAccountRequest request) {
        return Uni.createFrom().item(() -> {
            LOG.infof("Reactivating account: accountId=%s, reason=%s",
                request.getAccountId(), request.getReason());

            boolean success = accountRepository.reactivateAccount(request.getAccountId(), request.getReason());

            if (success) {
                LOG.infof("Reactivated account %s", request.getAccountId());

                // Publish account reactivated event
                try {
                    eventPublisher.publishAccountReactivated(request.getAccountId(), request.getReason());
                } catch (Exception e) {
                    LOG.warnf(e, "Failed to publish account reactivated event for %s", request.getAccountId());
                    // Don't fail account reactivation if event publishing fails
                }

                return ReactivateAccountResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Account reactivated successfully")
                    .build();
            } else {
                return ReactivateAccountResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Account not found: " + request.getAccountId())
                    .build();
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<ListAccountsResponse> listAccounts(ListAccountsRequest request) {
        return Uni.createFrom().item(() -> {
            String rawQuery = request.getQuery();
            String query = rawQuery != null ? rawQuery.trim() : "";
            boolean includeInactive = request.getIncludeInactive();
            int pageSize = request.getPageSize() > 0 ? request.getPageSize() : DEFAULT_PAGE_SIZE;
            if (pageSize > MAX_PAGE_SIZE) {
                pageSize = MAX_PAGE_SIZE;
            }

            int offset = 0;
            String pageToken = request.getPageToken();
            if (pageToken != null && !pageToken.isBlank()) {
                try {
                    offset = Integer.parseInt(pageToken.trim());
                    if (offset < 0) {
                        offset = 0;
                    }
                } catch (NumberFormatException e) {
                    LOG.warnf("Invalid page token '%s', defaulting to 0", pageToken);
                }
            }

            LOG.debugf("Listing accounts query=%s includeInactive=%s pageSize=%d offset=%d",
                query, includeInactive, pageSize, offset);

            var accounts = accountRepository.listAccounts(query, includeInactive, pageSize + 1, offset);
            long totalCount = accountRepository.countAccounts(query, includeInactive);

            String nextPageToken = "";
            if (accounts.size() > pageSize) {
                accounts = accounts.subList(0, pageSize);
                nextPageToken = String.valueOf(offset + pageSize);
            }

            ListAccountsResponse.Builder builder = ListAccountsResponse.newBuilder()
                .setTotalCount((int) Math.min(totalCount, Integer.MAX_VALUE));

            if (!nextPageToken.isBlank()) {
                builder.setNextPageToken(nextPageToken);
            }

            for (Account account : accounts) {
                builder.addAccounts(toProtoAccount(account));
            }

            return builder.build();
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<UpdateAccountResponse> updateAccount(UpdateAccountRequest request) {
        return Uni.createFrom().item(() -> {
            String accountId = request.getAccountId() != null ? request.getAccountId().trim() : "";
            String name = request.getName() != null ? request.getName().trim() : "";
            String description = request.getDescription();

            LOG.infof("Updating account: accountId=%s", accountId);

            if (accountId.isEmpty()) {
                throw new IllegalArgumentException("Account ID is required");
            }
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Account name is required");
            }

            Account updated = accountRepository.updateAccount(accountId, name, description);
            if (updated == null) {
                throw Status.NOT_FOUND
                    .withDescription("Account not found: " + accountId)
                    .asRuntimeException();
            }

            // Publish account updated event
            try {
                eventPublisher.publishAccountUpdated(updated.accountId, updated.name, updated.description);
            } catch (Exception e) {
                LOG.warnf(e, "Failed to publish account updated event for %s", updated.accountId);
                // Don't fail account update if event publishing fails
            }

            return UpdateAccountResponse.newBuilder()
                .setAccount(toProtoAccount(updated))
                .build();
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    // S3 feature temporarily disabled - will be re-enabled when needed
    /*
    private void createS3BucketForAccount(String accountId) {
        String bucketName = "account-" + accountId.toLowerCase().replaceAll("[^a-z0-9-]", "-");
        
        try {
            s3Client.createBucket(CreateBucketRequest.builder()
                .bucket(bucketName)
                .build());
            LOG.infof("Created S3 bucket '%s' for account '%s' (dev-only)", bucketName, accountId);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to create S3 bucket '%s' for account '%s' (dev-only): %s", 
                bucketName, accountId, e.getMessage());
            throw e;
        }
    }
    */

    private ai.pipestream.repository.account.Account toProtoAccount(Account account) {
        boolean isActive = account.active != null ? account.active : false;
        String description = account.description != null ? account.description : "";

        return ai.pipestream.repository.account.Account.newBuilder()
            .setAccountId(account.accountId)
            .setName(account.name)
            .setDescription(description)
            .setActive(isActive)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(account.createdAt.toEpochSecond())
                .setNanos(account.createdAt.getNano())
                .build())
            .setUpdatedAt(Timestamp.newBuilder()
                .setSeconds(account.updatedAt.toEpochSecond())
                .setNanos(account.updatedAt.getNano())
                .build())
            .build();
    }
}
