package com.data4circ.portal.common.config;

import com.data4circ.portal.common.demo.DemoDataContributor;
import com.data4circ.portal.features.connectors.entity.Connector;
import com.data4circ.portal.features.connectors.enums.ConnectorStatus;
import com.data4circ.portal.features.connectors.enums.ConnectorType;
import com.data4circ.portal.features.connectors.repository.ConnectorRepository;
import com.data4circ.portal.features.organization.entity.*;
import com.data4circ.portal.features.organization.repository.OrganizationRepository;
import com.data4circ.portal.features.organization.repository.UserRepository;
import com.data4circ.portal.features.organization.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Seeds sample organizations, users, and connectors for development and
 * testing. Enabled via app.demo-data.enabled (on in dev/test profiles,
 * off by default). Production admin access is bootstrapped separately by
 * {@link AdminBootstrapInitializer}.
 */
@Component
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
public class DataInitializer {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConnectorRepository connectorRepository;

    @Autowired
    private UserService userService;

    // Modules contributing their own demo data (e.g. SPIP's mock attributes/policies/key
    // statuses) for each sample organization — see DemoDataContributor. required = false:
    // a required List<T> autowiring still demands at least one matching bean, which fails
    // outright when every contributor (e.g. just SPIP today) is disabled.
    @Autowired(required = false)
    private List<DemoDataContributor> demoDataContributors = List.of();

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initializeData() {
        logger.info("Starting data initialization...");

        // Check if data already exists
        if (organizationRepository.count() > 0) {
            logger.info("Organizations already exist, skipping organization/user/connector initialization");

            try {
                seedModuleDemoData();
            } catch (Exception e) {
                logger.error("Error seeding module demo data: {}", e.getMessage(), e);
            }
            return;
        }

        try {
            initializeOrganizations();
            initializeUsers();
            initializeConnectors();
            seedModuleDemoData();
            logger.info("Data initialization completed successfully");
        } catch (Exception e) {
            logger.error("Error during data initialization: {}", e.getMessage(), e);
        }
    }

    private void initializeOrganizations() {
        logger.info("Initializing sample organizations...");

        Organization[] organizations = {
            createOrganization("GreenTech Solutions",
                "Leading provider of circular economy solutions for automotive industry",
                OrganizationType.MANUFACTURER, IndustrySector.TEXTILES,
                "https://greentech-solutions.com", "contact@greentech-solutions.com",
                "+1-555-0101", "123 Innovation Ave, Green City, GC 12345",
                CertificationStatus.ACTIVE),

            createOrganization("EcoRecycle Corp",
                "Specialized in electronic waste management and precious metal recovery",
                OrganizationType.REMANUFACTURER, IndustrySector.METALS,
                "https://ecorecycle.com", "info@ecorecycle.com",
                "+1-555-0102", "456 Sustainable St, EcoTown, ET 67890",
                CertificationStatus.ACTIVE),

            createOrganization("Agricultural Plastics Institute",
                "Research institute focused on agricultural plastic waste solutions",
                OrganizationType.RECYCLER, IndustrySector.PLASTIC_AGRICULTURE,
                "https://agplastics-institute.org", "research@agplastics-institute.org",
                "+1-555-0103", "789 Research Blvd, University City, UC 54321",
                CertificationStatus.ACTIVE),

            createOrganization("CircularData Analytics",
                "Data analytics startup for circular economy optimization",
                OrganizationType.RESEARCH_INSTITUTION, IndustrySector.DATA_ANALYTICS,
                "https://circulardata.io", "hello@circulardata.io",
                "+1-555-0104", "321 Startup Lane, Tech Valley, TV 98765",
                CertificationStatus.RESTRICTED)
        };

        for (Organization org : organizations) {
            organizationRepository.save(org);
            logger.debug("Created organization: {}", org.getName());
        }
    }

    private void initializeUsers() {
        logger.info("Initializing users...");

        // Demo platform admin with no organization (skipped when a bootstrapped
        // admin already claimed the username)
        if (!userRepository.existsByUsername("admin")) {
            User admin = createUser("admin", "admin@example.com", "Platform", "Administrator",
                UserRole.PLATFORM_ADMIN, null);
            userRepository.save(admin);
        }

        // Users linked to organizations
        Organization greenTech = organizationRepository.findByName("GreenTech Solutions").orElse(null);
        Organization ecoRecycle = organizationRepository.findByName("EcoRecycle Corp").orElse(null);
        Organization agPlastics = organizationRepository.findByName("Agricultural Plastics Institute").orElse(null);
        Organization circularData = organizationRepository.findByName("CircularData Analytics").orElse(null);

        User[] users = {
            createUser("john.doe", "john.doe@greentech-solutions.com", "John", "Doe",
                UserRole.ORG_ADMIN, greenTech),
            createUser("jane.smith", "jane.smith@ecorecycle.com", "Jane", "Smith",
                UserRole.SPIP_PRIVILEGED_USER, ecoRecycle),
            createUser("mike.wilson", "mike.wilson@agplastics-institute.org", "Mike", "Wilson",
                UserRole.ORG_MEMBER, agPlastics),
            createUser("sarah.jones", "sarah.jones@circulardata.io", "Sarah", "Jones",
                UserRole.ORG_ADMIN, circularData)
        };

        for (User user : users) {
            userRepository.save(user);
            logger.debug("Created user: {}", user.getUsername());
        }
    }

    private void initializeConnectors() {
        logger.info("Initializing connectors...");

        Organization greenTech = organizationRepository.findByName("GreenTech Solutions").orElse(null);
        Organization agPlastics = organizationRepository.findByName("Agricultural Plastics Institute").orElse(null);
        Organization circularData = organizationRepository.findByName("CircularData Analytics").orElse(null);

        // EcoRecycle's "SPIP Agent" connector used to be hardcoded here too, regardless of
        // whether the SPIP module was even present — moved to SpipDemoDataContributor
        // (spip-plugin), alongside its other demo data, so it only appears when SPIP
        // actually does. See DemoDataContributor and seedModuleDemoData() below.
        Connector[] connectors = {
            createConnector("GreenTech Data Provider",
                "Main data provider for automotive catalytic converter recycling data",
                ConnectorType.DATA_PROVIDER, "https://api.greentech-solutions.com/data",
                ConnectorStatus.ONLINE, greenTech,
                "{\"apiKey\": \"encrypted_key_123\", \"dataTypes\": [\"catalytic_converters\", \"precious_metals\"]}"),

            createConnector("Agricultural Research DB",
                "Database connector for agricultural plastic research data",
                ConnectorType.DATABASE, "postgresql://research-db.agplastics-institute.org:5432/plastics_data",
                ConnectorStatus.OFFLINE, agPlastics,
                "{\"dbType\": \"postgresql\", \"schema\": \"research\", \"tables\": [\"plastic_types\", \"degradation_data\"]}"),

            createConnector("CircularData API Gateway",
                "API gateway for data analytics and visualization services",
                ConnectorType.EXTERNAL_API, "https://api.circulardata.io/gateway",
                ConnectorStatus.MAINTENANCE, circularData,
                "{\"version\": \"v2.1\", \"rateLimits\": {\"requests_per_hour\": 10000}}"),

            createConnector("GreenTech File System",
                "File system connector for historical catalytic converter data",
                ConnectorType.FILE_SYSTEM, "/data/greentech/historical",
                ConnectorStatus.ONLINE, greenTech,
                "{\"path\": \"/data/greentech/historical\", \"fileTypes\": [\".csv\", \".json\", \".xml\"]}"),

                createConnector("GreenTech File System",
                        "File system connector for historical catalytic converter data",
                        ConnectorType.DATA_CONSUMER, "/data/greentech/historical",
                        ConnectorStatus.ONLINE, greenTech,
                        "{\"path\": \"/data/greentech/historical\", \"fileTypes\": [\".csv\", \".json\", \".xml\"]}"),

                createConnector("GreenTech File System",
                        "File system connector for historical catalytic converter data",
                        ConnectorType.DATA_PROVIDER, "/data/greentech/historical",
                        ConnectorStatus.ONLINE, greenTech,
                        "{\"path\": \"/data/greentech/historical\", \"fileTypes\": [\".csv\", \".json\", \".xml\"]}"),

                createConnector("GreenTech File System",
                        "File system connector for historical catalytic converter data",
                        ConnectorType.DATABASE, "/data/greentech/historical",
                        ConnectorStatus.ONLINE, greenTech,
                        "{\"path\": \"/data/greentech/historical\", \"fileTypes\": [\".csv\", \".json\", \".xml\"]}")

        };

        for (Connector connector : connectors) {
            connectorRepository.save(connector);
            logger.debug("Created connector: {}", connector.getName());
        }
    }

    private Organization createOrganization(String name, String description, OrganizationType type,
                                          IndustrySector industrySector, String website, String contactEmail,
                                          String contactPhone, String address, CertificationStatus certificationStatus) {
        Organization org = new Organization();
        org.setName(name);
        org.setDescription(description);
        org.setType(type);
        org.setIndustrySector(industrySector);
        org.setWebsite(website);
        org.setContactEmail(contactEmail);
        org.setContactPhone(contactPhone);
        org.setAddress(address);
        org.setCertificationStatus(certificationStatus);
        // createdAt and updatedAt are automatically set by @PrePersist
        return org;
    }

    private User createUser(String username, String email, String firstName, String lastName,
                           UserRole role, Organization organization) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setRole(role);
        user.setOrganization(organization);
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);
        // Password for the demo admin is 'Demo_Admin#2025!', other users use 'password' - encoded
        String password = role == UserRole.PLATFORM_ADMIN ? "Demo_Admin#2025!" : "password";
        user.setPassword(userService.encodePassword(password));
        // createdAt and updatedAt are automatically set by @PrePersist
        return user;
    }

    private Connector createConnector(String name, String description, ConnectorType type,
                                    String endpoint, ConnectorStatus status, Organization organization,
                                    String configuration) {
        Connector connector = new Connector();
        connector.setName(name);
        connector.setDescription(description);
        connector.setType(type);
        connector.setEndpoint(endpoint);
        connector.setStatus(status);
        connector.setOrganization(organization);
        connector.setConfiguration(configuration);

        if (status == ConnectorStatus.ONLINE) {
            connector.setLastHeartbeat(LocalDateTime.now());
        }

        // createdAt and updatedAt are automatically set by @PrePersist
        return connector;
    }

    /**
     * Runs every registered {@link DemoDataContributor} (e.g. SPIP's mock attributes,
     * policies and key statuses) against each sample organization that exists. Each
     * contributor is responsible for its own idempotency (skip if it already seeded a
     * given organization) — this runs both on a fresh DB and as a backfill on an existing
     * one, so it must be safe to call repeatedly.
     */
    private void seedModuleDemoData() {
        if (demoDataContributors.isEmpty()) {
            logger.info("No demo-data contributors registered (e.g. SPIP module disabled) — nothing extra to seed");
            return;
        }

        Organization greenTech = organizationRepository.findByName("GreenTech Solutions").orElse(null);
        Organization ecoRecycle = organizationRepository.findByName("EcoRecycle Corp").orElse(null);
        Organization agPlastics = organizationRepository.findByName("Agricultural Plastics Institute").orElse(null);
        Organization circularData = organizationRepository.findByName("CircularData Analytics").orElse(null);

        for (Organization org : new Organization[]{greenTech, ecoRecycle, agPlastics, circularData}) {
            if (org == null) {
                continue;
            }
            for (DemoDataContributor contributor : demoDataContributors) {
                contributor.seedDemoData(org);
            }
            logger.debug("Seeded module demo data for: {}", org.getName());
        }

        logger.info("Module demo data seeding completed");
    }
}
