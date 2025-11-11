package ai.pipestream.account.repository;

import ai.pipestream.account.entity.Account;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository CRUD tests for Account entity against MySQL.
 * Tests the repository layer in isolation without gRPC.
 */
@QuarkusTest
public class AccountRepositoryTest {

    @Inject
    AccountRepository accountRepository;

    @BeforeEach
    @Transactional
    void cleanupBefore() {
        // Clean up any test accounts from previous runs
        Account.deleteAll();
    }

    @Test
    void testCreateAccount() {
        // Create account
        String accountId = "test-create-" + System.currentTimeMillis();
        Account account = accountRepository.createAccount(accountId, "Test Account", "Test description");

        // Verify
        assertNotNull(account);
        assertEquals(accountId, account.accountId);
        assertEquals("Test Account", account.name);
        assertEquals("Test description", account.description);
        assertTrue(account.active);
        assertNotNull(account.createdAt);
        assertNotNull(account.updatedAt);
    }

    @Test
    void testCreateAccount_Idempotent() {
        // Create account twice with same ID
        String accountId = "test-idempotent-" + System.currentTimeMillis();
        Account first = accountRepository.createAccount(accountId, "First", "First description");
        Account second = accountRepository.createAccount(accountId, "Second", "Second description");

        // Verify returns existing account, not creates new one
        assertEquals(first.accountId, second.accountId);
        assertEquals("First", second.name); // Original name preserved
        assertEquals("First description", second.description);
    }

    @Test
    void testFindByAccountId() {
        // Create account
        String accountId = "test-find-" + System.currentTimeMillis();
        accountRepository.createAccount(accountId, "Find Test", "Find description");

        // Find it
        Account found = accountRepository.findByAccountId(accountId);

        // Verify
        assertNotNull(found);
        assertEquals(accountId, found.accountId);
        assertEquals("Find Test", found.name);
        assertEquals("Find description", found.description);
        assertTrue(found.active);
    }

    @Test
    void testFindByAccountId_NotFound() {
        // Try to find non-existent account
        Account notFound = accountRepository.findByAccountId("does-not-exist");

        // Verify returns null
        assertNull(notFound);
    }

    @Test
    void testInactivateAccount() {
        // Create account
        String accountId = "test-inactivate-" + System.currentTimeMillis();
        Account account = accountRepository.createAccount(accountId, "To Inactivate", "Will be inactive");
        assertTrue(account.active);

        // Inactivate it
        boolean success = accountRepository.inactivateAccount(accountId, "Test reason");

        // Verify
        assertTrue(success);

        // Reload and verify inactive
        Account inactivated = accountRepository.findByAccountId(accountId);
        assertNotNull(inactivated);
        assertFalse(inactivated.active);
        assertTrue(inactivated.updatedAt.isAfter(account.createdAt));
    }

    @Test
    void testInactivateAccount_Idempotent() {
        // Create and inactivate account
        String accountId = "test-idempotent-inactivate-" + System.currentTimeMillis();
        accountRepository.createAccount(accountId, "To Inactivate", "Idempotent test");
        accountRepository.inactivateAccount(accountId, "First inactivation");

        // Inactivate again
        boolean secondSuccess = accountRepository.inactivateAccount(accountId, "Second inactivation");

        // Verify still succeeds (idempotent)
        assertTrue(secondSuccess);

        // Verify still inactive
        Account account = accountRepository.findByAccountId(accountId);
        assertFalse(account.active);
    }

    @Test
    void testInactivateAccount_NotFound() {
        // Try to inactivate non-existent account
        boolean success = accountRepository.inactivateAccount("does-not-exist", "Test reason");

        // Verify returns false
        assertFalse(success);
    }

    @Test
    void testUpdateAccount() {
        String accountId = "test-update-" + System.currentTimeMillis();
        Account created = accountRepository.createAccount(accountId, "Original Name", "Original description");

        Account updated = accountRepository.updateAccount(accountId, "Updated Name", "Updated description");

        assertNotNull(updated);
        assertEquals("Updated Name", updated.name);
        assertEquals("Updated description", updated.description);
        assertTrue(updated.updatedAt.isAfter(created.updatedAt) || updated.updatedAt.isEqual(created.updatedAt));
    }

    @Test
    void testUpdateAccountNotFound() {
        Account updated = accountRepository.updateAccount("unknown-account", "New Name", "New description");

        assertNull(updated);
    }

    @Test
    void testListAccounts_DefaultsExcludeInactive() {
        String prefix = "list-default-" + System.currentTimeMillis();
        Account activeOne = accountRepository.createAccount(prefix + "-1", "Alpha", "Active account 1");
        Account activeTwo = accountRepository.createAccount(prefix + "-2", "Beta", "Active account 2");
        accountRepository.createAccount(prefix + "-3", "Gamma", "Will be inactive");
        accountRepository.inactivateAccount(prefix + "-3", "Testing exclude");

        var results = accountRepository.listAccounts(null, false, 25, 0);

        assertTrue(results.stream().allMatch(a -> a.active));
        assertTrue(results.stream().anyMatch(a -> a.accountId.equals(activeOne.accountId)));
        assertTrue(results.stream().anyMatch(a -> a.accountId.equals(activeTwo.accountId)));
        assertFalse(results.stream().anyMatch(a -> a.accountId.equals(prefix + "-3")));
    }

    @Test
    void testListAccountsIncludeInactiveAndFilter() {
        String prefix = "list-filter-" + System.currentTimeMillis();
        accountRepository.createAccount(prefix + "-1", "Marketing", "Department account");
        accountRepository.createAccount(prefix + "-2", "Mariner", "Another account");
        accountRepository.createAccount(prefix + "-3", "Sales", "Sales account");
        accountRepository.inactivateAccount(prefix + "-2", "Testing filter");

        var filtered = accountRepository.listAccounts("mar", true, 50, 0);

        assertEquals(2, filtered.size());
        assertTrue(filtered.stream().anyMatch(a -> a.accountId.equals(prefix + "-1")));
        assertTrue(filtered.stream().anyMatch(a -> a.accountId.equals(prefix + "-2")));
    }

    @Test
    void testListAccountsPagination() {
        String prefix = "list-paged-" + System.currentTimeMillis();
        for (int i = 0; i < 7; i++) {
            accountRepository.createAccount(prefix + "-" + i, "Paged " + i, "Account " + i);
        }

        var firstPage = accountRepository.listAccounts("", false, 3, 0);
        var secondPage = accountRepository.listAccounts("", false, 3, 3);

        assertEquals(3, firstPage.size());
        assertEquals(3, secondPage.size());
        assertNotEquals(firstPage.get(0).accountId, secondPage.get(0).accountId);
    }
}
