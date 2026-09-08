package com.data4circ.portal.features.connectors.service;

import com.data4circ.portal.features.organization.entity.Organization;
import com.data4circ.portal.features.connectors.entity.Connector;
import com.data4circ.portal.features.connectors.enums.ConnectorStatus;
import com.data4circ.portal.features.connectors.enums.ConnectorType;
import com.data4circ.portal.features.connectors.repository.ConnectorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ConnectorService
 *
 * These tests focus on business logic validation using mocked dependencies.
 * Tests verify the service layer behavior without touching the database.
 */
@ExtendWith(MockitoExtension.class)
class ConnectorServiceTest {

    @Mock
    private ConnectorRepository connectorRepository;

    @InjectMocks
    private ConnectorService connectorService;

    private Connector testConnector;
    private Organization testOrganization;

    @BeforeEach
    void setUp() {
        // Create test organization
        testOrganization = new Organization();
        testOrganization.setId(1L);
        testOrganization.setName("Test Organization");

        // Create test connector
        testConnector = new Connector();
        testConnector.setId(1L);
        testConnector.setName("Test Data Provider");
        testConnector.setType(ConnectorType.DATA_PROVIDER);
        testConnector.setStatus(ConnectorStatus.OFFLINE);
        testConnector.setOrganization(testOrganization);
        testConnector.setEndpoint("https://api.example.com/data");
        testConnector.setDescription("Test connector for data provisioning");
    }

    @Test
    void testSave_Success() {
        // Arrange
        when(connectorRepository.save(any(Connector.class))).thenReturn(testConnector);

        // Act
        Connector result = connectorService.save(testConnector);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Test Data Provider");
        verify(connectorRepository, times(1)).save(testConnector);
    }

    @Test
    void testFindById_Found() {
        // Arrange
        when(connectorRepository.findByIdWithOrganization(1L)).thenReturn(Optional.of(testConnector));

        // Act
        Optional<Connector> result = connectorService.findById(1L);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(1L);
        assertThat(result.get().getName()).isEqualTo("Test Data Provider");
        verify(connectorRepository, times(1)).findByIdWithOrganization(1L);
    }

    @Test
    void testFindById_NotFound() {
        // Arrange
        when(connectorRepository.findByIdWithOrganization(999L)).thenReturn(Optional.empty());

        // Act
        Optional<Connector> result = connectorService.findById(999L);

        // Assert
        assertThat(result).isEmpty();
        verify(connectorRepository, times(1)).findByIdWithOrganization(999L);
    }

    @Test
    void testFindAll() {
        // Arrange
        Connector connector2 = new Connector();
        connector2.setId(2L);
        connector2.setName("Test Consumer");
        connector2.setType(ConnectorType.DATA_CONSUMER);

        List<Connector> connectors = Arrays.asList(testConnector, connector2);
        when(connectorRepository.findAll()).thenReturn(connectors);

        // Act
        List<Connector> result = connectorService.findAll();

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).contains(testConnector, connector2);
        verify(connectorRepository, times(1)).findAll();
    }

    @Test
    void testFindByOrganization() {
        // Arrange
        List<Connector> connectors = Arrays.asList(testConnector);
        when(connectorRepository.findByOrganization(testOrganization)).thenReturn(connectors);

        // Act
        List<Connector> result = connectorService.findByOrganization(testOrganization);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOrganization()).isEqualTo(testOrganization);
        verify(connectorRepository, times(1)).findByOrganization(testOrganization);
    }

    @Test
    void testFindByStatus() {
        // Arrange
        testConnector.setStatus(ConnectorStatus.ONLINE);
        List<Connector> onlineConnectors = Arrays.asList(testConnector);
        when(connectorRepository.findByStatus(ConnectorStatus.ONLINE)).thenReturn(onlineConnectors);

        // Act
        List<Connector> result = connectorService.findByStatus(ConnectorStatus.ONLINE);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(ConnectorStatus.ONLINE);
        verify(connectorRepository, times(1)).findByStatus(ConnectorStatus.ONLINE);
    }

    @Test
    void testFindByType() {
        // Arrange
        List<Connector> providers = Arrays.asList(testConnector);
        when(connectorRepository.findByType(ConnectorType.DATA_PROVIDER)).thenReturn(providers);

        // Act
        List<Connector> result = connectorService.findByType(ConnectorType.DATA_PROVIDER);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo(ConnectorType.DATA_PROVIDER);
        verify(connectorRepository, times(1)).findByType(ConnectorType.DATA_PROVIDER);
    }

    @Test
    void testFindPage_WithFilters() {
        // Arrange
        Page<Connector> page = new PageImpl<>(Arrays.asList(testConnector));
        when(connectorRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(page);

        // Act
        Page<Connector> result = connectorService.findPage(
                0, 10, Sort.by("name"),
                testOrganization, ConnectorStatus.OFFLINE, ConnectorType.DATA_PROVIDER
        );

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0)).isEqualTo(testConnector);
        verify(connectorRepository, times(1)).findAll(any(Specification.class), any(PageRequest.class));
    }

    @Test
    void testFindPage_NormalizesPageSize() {
        // Arrange - test that page size is capped at 50
        Page<Connector> page = new PageImpl<>(Arrays.asList(testConnector));
        when(connectorRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(page);

        // Act - request page size of 100 (should be normalized to 50)
        connectorService.findPage(0, 100, Sort.by("name"), null, null, null);

        // Assert - verify that PageRequest was created with size 50
        verify(connectorRepository).findAll(
                any(Specification.class),
                argThat((PageRequest pageable) -> pageable.getPageSize() == 50)
        );
    }

    @Test
    void testFindPage_NormalizesNegativePage() {
        // Arrange
        Page<Connector> page = new PageImpl<>(Arrays.asList(testConnector));
        when(connectorRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(page);

        // Act - request negative page number (should be normalized to 0)
        connectorService.findPage(-5, 10, Sort.by("name"), null, null, null);

        // Assert - verify that PageRequest was created with page 0
        verify(connectorRepository).findAll(
                any(Specification.class),
                argThat((PageRequest pageable) -> pageable.getPageNumber() == 0)
        );
    }

    @Test
    void testDeleteById() {
        // Arrange
        doNothing().when(connectorRepository).deleteById(1L);

        // Act
        connectorService.deleteById(1L);

        // Assert
        verify(connectorRepository, times(1)).deleteById(1L);
    }

    @Test
    void testUpdate() {
        // Arrange
        testConnector.setStatus(ConnectorStatus.ONLINE);
        when(connectorRepository.save(testConnector)).thenReturn(testConnector);

        // Act
        Connector result = connectorService.update(testConnector);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ConnectorStatus.ONLINE);
        verify(connectorRepository, times(1)).save(testConnector);
    }

    @Test
    void testUpdateHeartbeat_ConnectorExists() {
        // Arrange
        testConnector.setStatus(ConnectorStatus.OFFLINE);
        testConnector.setLastHeartbeat(LocalDateTime.now().minusHours(1));

        when(connectorRepository.findByIdWithOrganization(1L)).thenReturn(Optional.of(testConnector));
        when(connectorRepository.save(any(Connector.class))).thenReturn(testConnector);

        // Act
        connectorService.updateHeartbeat(1L);

        // Assert
        verify(connectorRepository, times(1)).findByIdWithOrganization(1L);
        verify(connectorRepository, times(1)).save(argThat(connector -> {
            assertThat(connector.getStatus()).isEqualTo(ConnectorStatus.ONLINE);
            assertThat(connector.getLastHeartbeat()).isNotNull();
            assertThat(connector.getLastHeartbeat())
                    .isAfter(LocalDateTime.now().minusMinutes(1));
            return true;
        }));
    }

    @Test
    void testUpdateHeartbeat_ConnectorNotFound() {
        // Arrange
        when(connectorRepository.findByIdWithOrganization(999L)).thenReturn(Optional.empty());

        // Act
        connectorService.updateHeartbeat(999L);

        // Assert
        verify(connectorRepository, times(1)).findByIdWithOrganization(999L);
        verify(connectorRepository, never()).save(any(Connector.class));
    }

    @Test
    void testGetTotalCount() {
        // Arrange
        when(connectorRepository.count()).thenReturn(42L);

        // Act
        long result = connectorService.getTotalCount();

        // Assert
        assertThat(result).isEqualTo(42L);
        verify(connectorRepository, times(1)).count();
    }

    @Test
    void testGetOnlineCount() {
        // Arrange
        when(connectorRepository.countByStatus(ConnectorStatus.ONLINE)).thenReturn(15L);

        // Act
        long result = connectorService.getOnlineCount();

        // Assert
        assertThat(result).isEqualTo(15L);
        verify(connectorRepository, times(1)).countByStatus(ConnectorStatus.ONLINE);
    }

    @Test
    void testGetOfflineCount() {
        // Arrange
        when(connectorRepository.countByStatus(ConnectorStatus.OFFLINE)).thenReturn(27L);

        // Act
        long result = connectorService.getOfflineCount();

        // Assert
        assertThat(result).isEqualTo(27L);
        verify(connectorRepository, times(1)).countByStatus(ConnectorStatus.OFFLINE);
    }

    @Test
    void testGetCountByOrganization() {
        // Arrange
        when(connectorRepository.countByOrganization(testOrganization)).thenReturn(5L);

        // Act
        long result = connectorService.getCountByOrganization(testOrganization);

        // Assert
        assertThat(result).isEqualTo(5L);
        verify(connectorRepository, times(1)).countByOrganization(testOrganization);
    }
}
