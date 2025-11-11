package ai.pipestream.account;

import ai.pipestream.grpc.wiremock.MockServiceTestResource;
import ai.pipestream.repository.account.AccountServiceGrpc;
import ai.pipestream.repository.account.CreateAccountRequest;
import ai.pipestream.repository.account.GetAccountRequest;
import ai.pipestream.repository.account.InactivateAccountRequest;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Debug test to understand the protobuf serialization behavior
 * for the active field in both active and inactive accounts.
 */
@QuarkusTest
@QuarkusTestResource(MockServiceTestResource.class)
public class AccountServiceDebugTest {

    @GrpcClient("account-manager")
    AccountServiceGrpc.AccountServiceBlockingStub accountService;

    @BeforeEach
    void cleanupBefore() {
        // Clean up any test accounts from previous runs
    }

    @Test
    public void debugActiveFieldSerialization() {
        String testAccountId = "debug-test-" + System.currentTimeMillis();
        
        // Create an active account
        var createRequest = CreateAccountRequest.newBuilder()
                .setAccountId(testAccountId)
                .setName("Debug Test Account")
                .setDescription("Debugging active field serialization")
                .build();

        var createResponse = accountService.createAccount(createRequest);
        System.out.println("=== CREATE RESPONSE ===");
        System.out.println("Created: " + createResponse.getCreated());
        System.out.println("Account Active: " + createResponse.getAccount().getActive());
        System.out.println("Account toString: " + createResponse.getAccount().toString());
        System.out.println();

        // Get the active account
        var getRequest = GetAccountRequest.newBuilder()
                .setAccountId(testAccountId)
                .build();

        var activeAccount = accountService.getAccount(getRequest);
        System.out.println("=== ACTIVE ACCOUNT ===");
        System.out.println("Active: " + activeAccount.getActive());
        System.out.println("toString: " + activeAccount.toString());
        System.out.println();

        // Inactivate the account
        var inactivateRequest = InactivateAccountRequest.newBuilder()
                .setAccountId(testAccountId)
                .setReason("Debug test")
                .build();

        var inactivateResponse = accountService.inactivateAccount(inactivateRequest);
        System.out.println("=== INACTIVATE RESPONSE ===");
        System.out.println("Success: " + inactivateResponse.getSuccess());
        System.out.println("Message: " + inactivateResponse.getMessage());
        System.out.println();

        // Get the inactive account
        var inactiveAccount = accountService.getAccount(getRequest);
        System.out.println("=== INACTIVE ACCOUNT ===");
        System.out.println("Active: " + inactiveAccount.getActive());
        System.out.println("toString: " + inactiveAccount.toString());
        System.out.println();

        // Verify the behavior
        assertTrue(activeAccount.getActive(), "Active account should have active=true");
        assertFalse(inactiveAccount.getActive(), "Inactive account should have active=false");
        
        // The key test: both should have the active field accessible
        // (even if it's not shown in grpcurl JSON output)
        assertNotNull(activeAccount.getActive());
        assertNotNull(inactiveAccount.getActive());
    }
}