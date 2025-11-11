package ai.pipestream.account;

import ai.pipestream.account.entity.Account;
import ai.pipestream.account.repository.AccountRepository;
import ai.pipestream.grpc.wiremock.MockServiceTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify that the active field is properly handled in the database
 * and that inactive accounts are correctly identified.
 */
@QuarkusTest
@QuarkusTestResource(MockServiceTestResource.class)
public class AccountActiveFieldTest {

    @Inject
    AccountRepository accountRepository;

    @BeforeEach
    @Transactional
    void cleanupBefore() {
        // Clean up any test accounts from previous runs
        Account.deleteAll();
    }

    @Test
    void testActiveAccountInDatabase() {
        // Create an active account
        String accountId = "test-active-" + System.currentTimeMillis();
        Account account = accountRepository.createAccount(accountId, "Active Account", "Testing active account");
        
        // Verify it's active in the database
        assertNotNull(account);
        assertTrue(account.active, "Account should be active after creation");
        assertEquals(accountId, account.accountId);
        
        // Reload from database to verify persistence
        Account reloaded = accountRepository.findByAccountId(accountId);
        assertNotNull(reloaded);
        assertTrue(reloaded.active, "Reloaded account should be active");
    }

    @Test
    void testInactiveAccountInDatabase() {
        // Create and then inactivate an account
        String accountId = "test-inactive-" + System.currentTimeMillis();
        Account account = accountRepository.createAccount(accountId, "To Be Inactive", "Will be inactivated");
        assertTrue(account.active, "Account should be active after creation");
        
        // Inactivate it
        boolean success = accountRepository.inactivateAccount(accountId, "Test inactivation");
        assertTrue(success, "Inactivation should succeed");
        
        // Reload from database to verify it's inactive
        Account reloaded = accountRepository.findByAccountId(accountId);
        assertNotNull(reloaded);
        assertFalse(reloaded.active, "Reloaded account should be inactive");
        assertNotNull(reloaded.updatedAt, "Updated timestamp should be set");
    }

    @Test
    void testActiveFieldNotNullAfterCreation() {
        // Create an account and verify active field is not null
        String accountId = "test-not-null-" + System.currentTimeMillis();
        Account account = accountRepository.createAccount(accountId, "Test Account", "Testing not null");
        
        assertNotNull(account.active, "Active field should not be null after creation");
        assertTrue(account.active, "Active field should be true after creation");
    }

    @Test
    void testActiveFieldNotNullAfterInactivation() {
        // Create, then inactivate, and verify active field is not null
        String accountId = "test-inactive-not-null-" + System.currentTimeMillis();
        Account account = accountRepository.createAccount(accountId, "Test Account", "Testing inactive not null");
        assertTrue(account.active, "Account should be active after creation");
        
        boolean success = accountRepository.inactivateAccount(accountId, "Test inactivation");
        assertTrue(success, "Inactivation should succeed");
        
        Account reloaded = accountRepository.findByAccountId(accountId);
        assertNotNull(reloaded.active, "Active field should not be null after inactivation");
        assertFalse(reloaded.active, "Active field should be false after inactivation");
    }
}