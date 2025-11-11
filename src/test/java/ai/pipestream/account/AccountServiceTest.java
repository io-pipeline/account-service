package ai.pipestream.account;

import ai.pipestream.grpc.wiremock.MockServiceTestResource;
import ai.pipestream.repository.account.*;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.grpc.GrpcClient;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@QuarkusTestResource(MockServiceTestResource.class)
public class AccountServiceTest {

    @SuppressWarnings("CdiInjectionPointsInspection")
    @GrpcClient("account-manager")
    AccountServiceGrpc.AccountServiceBlockingStub accountService;

    @Test
    public void testCreateAccount() {
        String testAccountId = "test-account-" + System.currentTimeMillis();
        Instant testStartTime = Instant.now();

        var request = CreateAccountRequest.newBuilder()
                .setAccountId(testAccountId)
                .setName("Test Account")
                .setDescription("Test account for unit testing")
                .build();

        var response = accountService.createAccount(request);
        Instant testEndTime = Instant.now();

        assertThat("Account should be successfully created", response.getCreated(), is(true));
        assertThat("Account ID should match the requested ID",
                response.getAccount().getAccountId(), equalTo(testAccountId));
        assertThat("Account name should be set correctly",
                response.getAccount().getName(), equalTo("Test Account"));
        assertThat("Account description should be set correctly",
                response.getAccount().getDescription(), equalTo("Test account for unit testing"));
        assertThat("Account should be active by default",
                response.getAccount().getActive(), is(true));

        // Verify timestamps are set and reasonable
        assertThat("CreatedAt timestamp should be set",
                response.getAccount().hasCreatedAt(), is(true));
        assertThat("UpdatedAt timestamp should be set",
                response.getAccount().hasUpdatedAt(), is(true));

        long createdAtSeconds = response.getAccount().getCreatedAt().getSeconds();
        long updatedAtSeconds = response.getAccount().getUpdatedAt().getSeconds();

        assertThat("CreatedAt should be within test execution timeframe",
                createdAtSeconds, allOf(
                        greaterThanOrEqualTo(testStartTime.getEpochSecond()),
                        lessThanOrEqualTo(testEndTime.getEpochSecond())
                ));
        assertThat("UpdatedAt should be within test execution timeframe",
                updatedAtSeconds, allOf(
                        greaterThanOrEqualTo(testStartTime.getEpochSecond()),
                        lessThanOrEqualTo(testEndTime.getEpochSecond())
                ));
        assertThat("UpdatedAt should be at or after CreatedAt",
                updatedAtSeconds, greaterThanOrEqualTo(createdAtSeconds));
    }

    @Test
    public void testGetAccount() {
        String testAccountId = "test-get-account-" + System.currentTimeMillis();
        Instant testStartTime = Instant.now();

        // First create an account
        var createRequest = CreateAccountRequest.newBuilder()
                .setAccountId(testAccountId)
                .setName("Test Get Account")
                .setDescription("Test account for get testing")
                .build();
        CreateAccountResponse createResponse = accountService.createAccount(createRequest);
        assertThat("Create account response should not be null", createResponse, notNullValue());

        Instant testEndTime = Instant.now();

        // Then get it
        var getRequest = GetAccountRequest.newBuilder()
                .setAccountId(testAccountId)
                .build();

        var account = accountService.getAccount(getRequest);
        
        assertThat("Retrieved account ID should match",
                account.getAccountId(), equalTo(testAccountId));
        assertThat("Retrieved account name should match",
                account.getName(), equalTo("Test Get Account"));
        assertThat("Retrieved account description should match",
                account.getDescription(), equalTo("Test account for get testing"));
        assertThat("Retrieved account should be active",
                account.getActive(), is(true));

        // Verify timestamps
        assertThat("Retrieved account should have CreatedAt timestamp",
                account.hasCreatedAt(), is(true));
        assertThat("Retrieved account should have UpdatedAt timestamp",
                account.hasUpdatedAt(), is(true));

        long createdAtSeconds = account.getCreatedAt().getSeconds();
        long updatedAtSeconds = account.getUpdatedAt().getSeconds();

        assertThat("CreatedAt should be within test execution timeframe",
                createdAtSeconds, allOf(
                        greaterThanOrEqualTo(testStartTime.getEpochSecond()),
                        lessThanOrEqualTo(testEndTime.getEpochSecond())
                ));
        assertThat("UpdatedAt should be within test execution timeframe",
                updatedAtSeconds, allOf(
                        greaterThanOrEqualTo(testStartTime.getEpochSecond()),
                        lessThanOrEqualTo(testEndTime.getEpochSecond())
                ));
    }

    @Test
    public void testInactivateAccount() {
        String testAccountId = "test-inactivate-account-" + System.currentTimeMillis();
        Instant testStartTime = Instant.now();

        // First create an account
        var createRequest = CreateAccountRequest.newBuilder()
                .setAccountId(testAccountId)
                .setName("Test Inactivate Account")
                .setDescription("Test account for inactivate testing")
                .build();
        CreateAccountResponse createAccountResponse = accountService.createAccount(createRequest);

        // Verify the created account has expected fields
        assertThat("Created account should have correct ID",
                createAccountResponse.getAccount().getAccountId(), equalTo(testAccountId));
        assertThat("Created account should have correct name",
                createAccountResponse.getAccount().getName(), equalTo("Test Inactivate Account"));
        assertThat("Created account should have correct description",
                createAccountResponse.getAccount().getDescription(), equalTo("Test account for inactivate testing"));
        assertThat("Created account should be active initially",
                createAccountResponse.getAccount().getActive(), is(true));
        assertThat("Created account should have CreatedAt timestamp",
                createAccountResponse.getAccount().hasCreatedAt(), is(true));
        assertThat("Created account should have UpdatedAt timestamp",
                createAccountResponse.getAccount().hasUpdatedAt(), is(true));

        Instant testEndTime = Instant.now();

        // Verify timestamps on created account
        long createdAtSeconds = createAccountResponse.getAccount().getCreatedAt().getSeconds();
        long updatedAtSeconds = createAccountResponse.getAccount().getUpdatedAt().getSeconds();

        assertThat("CreatedAt should be within test execution timeframe",
                createdAtSeconds, allOf(
                        greaterThanOrEqualTo(testStartTime.getEpochSecond()),
                        lessThanOrEqualTo(testEndTime.getEpochSecond())
                ));
        assertThat("UpdatedAt should be within test execution timeframe",
                updatedAtSeconds, allOf(
                        greaterThanOrEqualTo(testStartTime.getEpochSecond()),
                        lessThanOrEqualTo(testEndTime.getEpochSecond())
                ));
        assertThat("UpdatedAt should be at or after CreatedAt",
                updatedAtSeconds, greaterThanOrEqualTo(createdAtSeconds));

        // Then inactivate it
        var inactivateRequest = InactivateAccountRequest.newBuilder()
                .setAccountId(testAccountId)
                .setReason("Unit test inactivation")
                .build();

        var response = accountService.inactivateAccount(inactivateRequest);
        
        assertThat("Inactivation should be successful",
                response.getSuccess(), is(true));
        assertThat("Inactivation response message should be correct",
                response.getMessage(), equalTo("Account inactivated successfully"));
    }
}
