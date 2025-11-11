package ai.pipestream.account;

import io.quarkus.test.common.DevServicesContext;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;

/**
 * Test resource that starts Apicurio Registry for integration tests.
 *
 * Quarkus DevServices automatically starts MySQL and Kafka.
 * This resource starts Apicurio and connects it to the DevServices MySQL instance.
 */
public class ApicurioTestResource implements QuarkusTestResourceLifecycleManager, DevServicesContext.ContextAware {

    private GenericContainer<?> apicurioContainer;
    private DevServicesContext devServicesContext;

    @Override
    public void setIntegrationTestContext(DevServicesContext context) {
        this.devServicesContext = context;
    }

    @Override
    public Map<String, String> start() {
        // Get the datasource URL from DevServices
        String mysqlJdbcUrl = devServicesContext.devServicesProperties().get("quarkus.datasource.jdbc.url");

        if (mysqlJdbcUrl == null) {
            throw new IllegalStateException(
                "MySQL DevServices URL not found in context! Make sure Quarkus DevServices is enabled for datasource. " +
                "Available properties: " + devServicesContext.devServicesProperties().keySet());
        }

        System.out.println("=== MySQL DevServices JDBC URL: " + mysqlJdbcUrl + " ===");

        // Parse the MySQL JDBC URL to get connection details
        // Format: jdbc:mysql://host:port/database?params
        // We need to create a separate database for Apicurio on the same MySQL instance
        String mysqlHost = extractHost(mysqlJdbcUrl);
        int mysqlPort = extractPort(mysqlJdbcUrl);

        // MySQL DevServices credentials (default)
        String mysqlUsername = "quarkus";
        String mysqlPassword = "quarkus";

        // Build Apicurio's MySQL JDBC URL
        // Use the MySQL container's internal hostname and port (not the mapped port)
        // Note: We'll use the same MySQL instance but a different database name
        String apicurioDbUrl = String.format(
            "jdbc:mysql://%s:%d/apicurio_registry?createDatabaseIfNotExist=true",
            mysqlHost, mysqlPort
        );

        // Start Apicurio Registry container
        // Use SHARED network mode so it can reach DevServices containers
        apicurioContainer = new GenericContainer<>(DockerImageName.parse("apicurio/apicurio-registry:3.0.11"))
            .withNetwork(Network.SHARED)  // Use the shared Testcontainers network
            .withExposedPorts(8080)
            .withEnv("APICURIO_DATASOURCE_URL", apicurioDbUrl)
            .withEnv("APICURIO_DATASOURCE_USERNAME", mysqlUsername)
            .withEnv("APICURIO_DATASOURCE_PASSWORD", mysqlPassword)
            .withEnv("APICURIO_SQL_INIT", "true")
            .withEnv("APICURIO_STORAGE_KIND", "sql")
            .withEnv("APICURIO_STORAGE_SQL_KIND", "mysql")
            .withEnv("QUARKUS_HTTP_PORT", "8080")
            .withEnv("QUARKUS_LOG_LEVEL", "INFO")
            .withEnv("LOG_LEVEL", "INFO")
            .withEnv("QUARKUS_PROFILE", "prod")
            .waitingFor(Wait.forHttp("/health").forPort(8080));

        apicurioContainer.start();

        // Build the Apicurio Registry URL that our app will use
        String apicurioUrl = String.format(
            "http://%s:%d/apis/registry/v3",
            apicurioContainer.getHost(),
            apicurioContainer.getFirstMappedPort()
        );

        // Return configuration overrides
        Map<String, String> config = new HashMap<>();

        // Override the connector-level Apicurio URL
        // All channels inherit from this
        config.put("mp.messaging.connector.smallrye-kafka.apicurio.registry.url", apicurioUrl);

        // Also set the direct property for good measure
        config.put("mp.messaging.outgoing.account-events.apicurio.registry.url", apicurioUrl);

        System.out.println("=== Apicurio Test Resource Started ===");
        System.out.println("Apicurio URL: " + apicurioUrl);
        System.out.println("Apicurio DB URL: " + apicurioDbUrl);
        System.out.println("======================================");

        return config;
    }

    @Override
    public void stop() {
        if (apicurioContainer != null) {
            apicurioContainer.stop();
        }
    }

    /**
     * Extract hostname from JDBC URL
     * jdbc:mysql://hostname:port/database -> hostname
     */
    private String extractHost(String jdbcUrl) {
        // Remove jdbc:mysql:// prefix
        String withoutPrefix = jdbcUrl.substring("jdbc:mysql://".length());
        // Get host:port part (before /)
        String hostPort = withoutPrefix.split("/")[0];
        // Get host part (before :)
        return hostPort.split(":")[0];
    }

    /**
     * Extract port from JDBC URL
     * jdbc:mysql://hostname:port/database -> port
     */
    private int extractPort(String jdbcUrl) {
        // Remove jdbc:mysql:// prefix
        String withoutPrefix = jdbcUrl.substring("jdbc:mysql://".length());
        // Get host:port part (before /)
        String hostPort = withoutPrefix.split("/")[0];
        // Get port part (after :)
        String portStr = hostPort.split(":")[1];
        return Integer.parseInt(portStr);
    }
}
