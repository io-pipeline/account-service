package ai.pipestream.account;

import io.grpc.ManagedChannel;
import ai.pipestream.grpc.wiremock.WireMockGrpcTestResource;
import ai.pipestream.grpc.wiremock.AccountManagerMock;
import ai.pipestream.grpc.wiremock.InjectWireMock;
import ai.pipestream.repository.account.AccountServiceGrpc;
import ai.pipestream.repository.account.CreateAccountRequest;
import ai.pipestream.repository.account.GetAccountRequest;
import ai.pipestream.repository.account.InactivateAccountRequest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(WireMockGrpcTestResource.class)
public class AccountManagerWireMockTest {

    @InjectWireMock
    WireMockServer wireMockServer;

    private AccountManagerMock accountManagerMock;
    private ManagedChannel channel;
    private AccountServiceGrpc.AccountServiceBlockingStub accountService;

    @BeforeEach
    void setUp() {
        accountManagerMock = new AccountManagerMock(wireMockServer.port());

        // Create gRPC client that connects to WireMock
        channel = io.grpc.ManagedChannelBuilder.forAddress("localhost", wireMockServer.port())
                .usePlaintext()
                .build();
        accountService = AccountServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        // Properly shutdown the gRPC channel
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
            channel.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testCreateAccount_Success() {
        // Setup mock
        accountManagerMock.mockCreateAccount("test-account-123", "Test Account", "Test description");

        // Test
        var request = CreateAccountRequest.newBuilder()
                .setAccountId("test-account-123")
                .setName("Test Account")
                .setDescription("Test description")
                .build();

        var response = accountService.createAccount(request);

        // Verify
        assertTrue(response.getCreated());
        assertEquals("test-account-123", response.getAccount().getAccountId());
        assertEquals("Test Account", response.getAccount().getName());
        assertEquals("Test description", response.getAccount().getDescription());
        assertTrue(response.getAccount().getActive());
    }

    @Test
    public void testCreateAccount_AlreadyExists() {
        // Setup mock
        accountManagerMock.mockCreateAccountExists("existing-account", "Existing Account", "Already exists");

        // Test
        var request = CreateAccountRequest.newBuilder()
                .setAccountId("existing-account")
                .setName("Existing Account")
                .setDescription("Already exists")
                .build();

        var response = accountService.createAccount(request);

        // Verify
        assertFalse(response.getCreated());
        assertEquals("existing-account", response.getAccount().getAccountId());
    }

    @Test
    public void testGetAccount_Success() {
        // Setup mock
        accountManagerMock.mockGetAccount("test-account", "Test Account", "Test description", true);

        // Test
        var request = GetAccountRequest.newBuilder()
                .setAccountId("test-account")
                .build();

        var account = accountService.getAccount(request);

        // Verify
        assertEquals("test-account", account.getAccountId());
        assertEquals("Test Account", account.getName());
        assertEquals("Test description", account.getDescription());
        assertTrue(account.getActive());
    }

    @Test
    public void testGetAccount_NotFound() {
        // Setup mock
        accountManagerMock.mockAccountNotFound("nonexistent-account");

        // Test
        var request = GetAccountRequest.newBuilder()
                .setAccountId("nonexistent-account")
                .build();

        // Verify exception is thrown
        assertThrows(io.grpc.StatusRuntimeException.class, () -> accountService.getAccount(request));
    }

    @Test
    public void testInactivateAccount_Success() {
        // Setup mock
        accountManagerMock.mockInactivateAccount("test-account");

        // Test
        var request = InactivateAccountRequest.newBuilder()
                .setAccountId("test-account")
                .setReason("Test inactivation")
                .build();

        var response = accountService.inactivateAccount(request);

        // Verify
        assertTrue(response.getSuccess());
        assertEquals("Account inactivated successfully", response.getMessage());
        assertEquals(0, response.getDrivesAffected());
    }

    @Test
    public void testInactivateAccount_NotFound() {
        // Setup mock
        accountManagerMock.mockInactivateAccountNotFound("nonexistent-account");

        // Test
        var request = InactivateAccountRequest.newBuilder()
                .setAccountId("nonexistent-account")
                .setReason("Test inactivation")
                .build();

        var response = accountService.inactivateAccount(request);

        // Verify
        assertFalse(response.getSuccess());
        assertEquals("Account not found: nonexistent-account", response.getMessage());
        assertEquals(0, response.getDrivesAffected());
    }
}
