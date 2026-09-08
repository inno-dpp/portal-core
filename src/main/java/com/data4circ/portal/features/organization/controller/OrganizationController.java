package com.data4circ.portal.features.organization.controller;

import jakarta.servlet.http.HttpServletResponse;

import com.data4circ.portal.common.exception.AccessDeniedException;
import com.data4circ.portal.features.organization.dto.MemberDTO;
import com.data4circ.portal.features.organization.dto.ProfileCompletion;
import com.data4circ.portal.features.organization.dto.SetupChecklistItem;
import com.data4circ.portal.features.organization.entity.*;
import com.data4circ.portal.features.organization.entity.OrganizationType;
import com.data4circ.portal.features.organization.entity.User;
import com.data4circ.portal.common.exception.EntityNotFoundException;
import com.data4circ.portal.features.onboardingsync.tool.OnboardingToolConnectorService;
import com.data4circ.portal.features.connectors.entity.Connector;
import com.data4circ.portal.features.connectors.enums.ConnectorType;
import com.data4circ.portal.features.organization.service.EmailService;
import com.data4circ.portal.features.organization.service.OnboardingRequestService;
import com.data4circ.portal.features.organization.service.PasswordResetService;
import com.data4circ.portal.features.organization.service.UserService;
import com.data4circ.portal.features.collaboration.service.CollaborationService;
import com.data4circ.portal.features.collaboration.entity.CollaborationRejection;
import com.data4circ.portal.features.collaboration.entity.CollaborationRequest;
import com.data4circ.portal.features.collaboration.entity.CollaborationStatus;
import com.data4circ.portal.features.onboardingsync.ckan.CkanOnboardingSyncService;
import com.data4circ.portal.features.onboardingsync.ckan.CkanSyncResult;
import com.data4circ.portal.features.dataset.service.DatasetStatisticsService;
import com.data4circ.portal.features.platformsettings.service.CkanSettingsService;
import com.data4circ.portal.features.organization.entity.NaceCode;
import com.data4circ.portal.features.organization.service.NaceCodeService;
import com.data4circ.portal.features.organization.service.OrganizationService;
import com.data4circ.portal.features.organization.service.OrganizationSpipUserService;
import com.data4circ.portal.features.organization.entity.OrganizationSpipUser;
import com.data4circ.portal.features.organization.repository.OrganizationSpipUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/organizations")
public class OrganizationController {

    private static final Logger logger = LoggerFactory.getLogger(OrganizationController.class);

    // Roles that can be assigned through member management; platform-level and
    // SPIP roles are managed elsewhere and must not be touched from this UI
    private static final Set<UserRole> ASSIGNABLE_MEMBER_ROLES =
            EnumSet.of(UserRole.ORG_ADMIN, UserRole.ORG_MEMBER);

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private OrganizationSpipUserService spipUserService;

    @Autowired
    private OnboardingRequestService onboardingRequestService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private UserService userService;

    @Autowired
    private OnboardingToolConnectorService toolConnectorService;

    @Autowired
    private DatasetStatisticsService datasetStatisticsService;

    @Autowired
    private OrganizationSpipUserRepository organizationSpipUserRepository;

    @Autowired
    private CkanOnboardingSyncService ckanSyncService;

    @Autowired
    private CollaborationService collaborationService;

    @Autowired
    private NaceCodeService naceCodeService;

    @Autowired
    private CkanSettingsService ckanSettingsService;

    @Autowired
    private com.data4circ.portal.features.platformsettings.service.KeycloakSettingsService keycloakSettingsService;

    @Autowired
    private com.data4circ.portal.features.onboardingsync.keycloak.KeycloakOnboardingService keycloakOnboardingService;

    @Autowired
    private com.data4circ.portal.features.onboardingsync.tool.OnboardingToolRegistry toolRegistry;

    private static final List<String> COUNTRIES;
    private static final Map<String, String> COUNTRY_PHONE_PREFIXES;

    static {
        String[] isoCodes = Locale.getISOCountries();
        COUNTRIES = Arrays.stream(isoCodes)
                .map(code -> new Locale("", code).getDisplayCountry(Locale.ENGLISH))
                .sorted()
                .toList();

        // Map of ISO country codes to phone prefixes
        Map<String, String> codeToPrefix = new HashMap<>();
        codeToPrefix.put("AF", "+93"); codeToPrefix.put("AL", "+355"); codeToPrefix.put("DZ", "+213");
        codeToPrefix.put("AD", "+376"); codeToPrefix.put("AO", "+244"); codeToPrefix.put("AG", "+1-268");
        codeToPrefix.put("AR", "+54"); codeToPrefix.put("AM", "+374"); codeToPrefix.put("AU", "+61");
        codeToPrefix.put("AT", "+43"); codeToPrefix.put("AZ", "+994"); codeToPrefix.put("BS", "+1-242");
        codeToPrefix.put("BH", "+973"); codeToPrefix.put("BD", "+880"); codeToPrefix.put("BB", "+1-246");
        codeToPrefix.put("BY", "+375"); codeToPrefix.put("BE", "+32"); codeToPrefix.put("BZ", "+501");
        codeToPrefix.put("BJ", "+229"); codeToPrefix.put("BT", "+975"); codeToPrefix.put("BO", "+591");
        codeToPrefix.put("BA", "+387"); codeToPrefix.put("BW", "+267"); codeToPrefix.put("BR", "+55");
        codeToPrefix.put("BN", "+673"); codeToPrefix.put("BG", "+359"); codeToPrefix.put("BF", "+226");
        codeToPrefix.put("BI", "+257"); codeToPrefix.put("CV", "+238"); codeToPrefix.put("KH", "+855");
        codeToPrefix.put("CM", "+237"); codeToPrefix.put("CA", "+1"); codeToPrefix.put("CF", "+236");
        codeToPrefix.put("TD", "+235"); codeToPrefix.put("CL", "+56"); codeToPrefix.put("CN", "+86");
        codeToPrefix.put("CO", "+57"); codeToPrefix.put("KM", "+269"); codeToPrefix.put("CG", "+242");
        codeToPrefix.put("CD", "+243"); codeToPrefix.put("CR", "+506"); codeToPrefix.put("CI", "+225");
        codeToPrefix.put("HR", "+385"); codeToPrefix.put("CU", "+53"); codeToPrefix.put("CY", "+357");
        codeToPrefix.put("CZ", "+420"); codeToPrefix.put("DK", "+45"); codeToPrefix.put("DJ", "+253");
        codeToPrefix.put("DM", "+1-767"); codeToPrefix.put("DO", "+1-809"); codeToPrefix.put("EC", "+593");
        codeToPrefix.put("EG", "+20"); codeToPrefix.put("SV", "+503"); codeToPrefix.put("GQ", "+240");
        codeToPrefix.put("ER", "+291"); codeToPrefix.put("EE", "+372"); codeToPrefix.put("SZ", "+268");
        codeToPrefix.put("ET", "+251"); codeToPrefix.put("FJ", "+679"); codeToPrefix.put("FI", "+358");
        codeToPrefix.put("FR", "+33"); codeToPrefix.put("GA", "+241"); codeToPrefix.put("GM", "+220");
        codeToPrefix.put("GE", "+995"); codeToPrefix.put("DE", "+49"); codeToPrefix.put("GH", "+233");
        codeToPrefix.put("GR", "+30"); codeToPrefix.put("GD", "+1-473"); codeToPrefix.put("GT", "+502");
        codeToPrefix.put("GN", "+224"); codeToPrefix.put("GW", "+245"); codeToPrefix.put("GY", "+592");
        codeToPrefix.put("HT", "+509"); codeToPrefix.put("HN", "+504"); codeToPrefix.put("HU", "+36");
        codeToPrefix.put("IS", "+354"); codeToPrefix.put("IN", "+91"); codeToPrefix.put("ID", "+62");
        codeToPrefix.put("IR", "+98"); codeToPrefix.put("IQ", "+964"); codeToPrefix.put("IE", "+353");
        codeToPrefix.put("IL", "+972"); codeToPrefix.put("IT", "+39"); codeToPrefix.put("JM", "+1-876");
        codeToPrefix.put("JP", "+81"); codeToPrefix.put("JO", "+962"); codeToPrefix.put("KZ", "+7");
        codeToPrefix.put("KE", "+254"); codeToPrefix.put("KI", "+686"); codeToPrefix.put("KP", "+850");
        codeToPrefix.put("KR", "+82"); codeToPrefix.put("KW", "+965"); codeToPrefix.put("KG", "+996");
        codeToPrefix.put("LA", "+856"); codeToPrefix.put("LV", "+371"); codeToPrefix.put("LB", "+961");
        codeToPrefix.put("LS", "+266"); codeToPrefix.put("LR", "+231"); codeToPrefix.put("LY", "+218");
        codeToPrefix.put("LI", "+423"); codeToPrefix.put("LT", "+370"); codeToPrefix.put("LU", "+352");
        codeToPrefix.put("MG", "+261"); codeToPrefix.put("MW", "+265"); codeToPrefix.put("MY", "+60");
        codeToPrefix.put("MV", "+960"); codeToPrefix.put("ML", "+223"); codeToPrefix.put("MT", "+356");
        codeToPrefix.put("MH", "+692"); codeToPrefix.put("MR", "+222"); codeToPrefix.put("MU", "+230");
        codeToPrefix.put("MX", "+52"); codeToPrefix.put("FM", "+691"); codeToPrefix.put("MD", "+373");
        codeToPrefix.put("MC", "+377"); codeToPrefix.put("MN", "+976"); codeToPrefix.put("ME", "+382");
        codeToPrefix.put("MA", "+212"); codeToPrefix.put("MZ", "+258"); codeToPrefix.put("MM", "+95");
        codeToPrefix.put("NA", "+264"); codeToPrefix.put("NR", "+674"); codeToPrefix.put("NP", "+977");
        codeToPrefix.put("NL", "+31"); codeToPrefix.put("NZ", "+64"); codeToPrefix.put("NI", "+505");
        codeToPrefix.put("NE", "+227"); codeToPrefix.put("NG", "+234"); codeToPrefix.put("MK", "+389");
        codeToPrefix.put("NO", "+47"); codeToPrefix.put("OM", "+968"); codeToPrefix.put("PK", "+92");
        codeToPrefix.put("PW", "+680"); codeToPrefix.put("PS", "+970"); codeToPrefix.put("PA", "+507");
        codeToPrefix.put("PG", "+675"); codeToPrefix.put("PY", "+595"); codeToPrefix.put("PE", "+51");
        codeToPrefix.put("PH", "+63"); codeToPrefix.put("PL", "+48"); codeToPrefix.put("PT", "+351");
        codeToPrefix.put("QA", "+974"); codeToPrefix.put("RO", "+40"); codeToPrefix.put("RU", "+7");
        codeToPrefix.put("RW", "+250"); codeToPrefix.put("KN", "+1-869"); codeToPrefix.put("LC", "+1-758");
        codeToPrefix.put("VC", "+1-784"); codeToPrefix.put("WS", "+685"); codeToPrefix.put("SM", "+378");
        codeToPrefix.put("ST", "+239"); codeToPrefix.put("SA", "+966"); codeToPrefix.put("SN", "+221");
        codeToPrefix.put("RS", "+381"); codeToPrefix.put("SC", "+248"); codeToPrefix.put("SL", "+232");
        codeToPrefix.put("SG", "+65"); codeToPrefix.put("SK", "+421"); codeToPrefix.put("SI", "+386");
        codeToPrefix.put("SB", "+677"); codeToPrefix.put("SO", "+252"); codeToPrefix.put("ZA", "+27");
        codeToPrefix.put("SS", "+211"); codeToPrefix.put("ES", "+34"); codeToPrefix.put("LK", "+94");
        codeToPrefix.put("SD", "+249"); codeToPrefix.put("SR", "+597"); codeToPrefix.put("SE", "+46");
        codeToPrefix.put("CH", "+41"); codeToPrefix.put("SY", "+963"); codeToPrefix.put("TW", "+886");
        codeToPrefix.put("TJ", "+992"); codeToPrefix.put("TZ", "+255"); codeToPrefix.put("TH", "+66");
        codeToPrefix.put("TL", "+670"); codeToPrefix.put("TG", "+228"); codeToPrefix.put("TO", "+676");
        codeToPrefix.put("TT", "+1-868"); codeToPrefix.put("TN", "+216"); codeToPrefix.put("TR", "+90");
        codeToPrefix.put("TM", "+993"); codeToPrefix.put("TV", "+688"); codeToPrefix.put("UG", "+256");
        codeToPrefix.put("UA", "+380"); codeToPrefix.put("AE", "+971"); codeToPrefix.put("GB", "+44");
        codeToPrefix.put("US", "+1"); codeToPrefix.put("UY", "+598"); codeToPrefix.put("UZ", "+998");
        codeToPrefix.put("VU", "+678"); codeToPrefix.put("VE", "+58"); codeToPrefix.put("VN", "+84");
        codeToPrefix.put("YE", "+967"); codeToPrefix.put("ZM", "+260"); codeToPrefix.put("ZW", "+263");
        // Territories and special regions
        codeToPrefix.put("HK", "+852"); codeToPrefix.put("MO", "+853"); codeToPrefix.put("PR", "+1-787");
        codeToPrefix.put("GU", "+1-671"); codeToPrefix.put("VI", "+1-340"); codeToPrefix.put("AS", "+1-684");
        codeToPrefix.put("GF", "+594"); codeToPrefix.put("GP", "+590"); codeToPrefix.put("MQ", "+596");
        codeToPrefix.put("RE", "+262"); codeToPrefix.put("YT", "+262"); codeToPrefix.put("NC", "+687");
        codeToPrefix.put("PF", "+689"); codeToPrefix.put("WF", "+681"); codeToPrefix.put("PM", "+508");
        codeToPrefix.put("CK", "+682"); codeToPrefix.put("NU", "+683"); codeToPrefix.put("TK", "+690");
        codeToPrefix.put("GI", "+350"); codeToPrefix.put("FK", "+500"); codeToPrefix.put("SH", "+290");
        codeToPrefix.put("AW", "+297"); codeToPrefix.put("CW", "+599"); codeToPrefix.put("SX", "+1-721");
        codeToPrefix.put("BM", "+1-441"); codeToPrefix.put("KY", "+1-345"); codeToPrefix.put("TC", "+1-649");
        codeToPrefix.put("VG", "+1-284"); codeToPrefix.put("AI", "+1-264"); codeToPrefix.put("MS", "+1-664");

        // Build country name -> phone prefix map
        COUNTRY_PHONE_PREFIXES = new LinkedHashMap<>();
        for (String isoCode : isoCodes) {
            String countryName = new Locale("", isoCode).getDisplayCountry(Locale.ENGLISH);
            String prefix = codeToPrefix.get(isoCode);
            if (prefix != null) {
                COUNTRY_PHONE_PREFIXES.put(countryName, prefix);
            }
        }
    }

    /** Initial and per-"Load more" page size for the Organizations directory (progressive loading, not classic pagination). */
    private static final int ORGANIZATIONS_PAGE_SIZE = 18;

    private record ResolvedOrgFilters(String sortProperty, Sort sortOrder, CertificationStatus status,
            OrganizationType type, IndustrySector sector, String search, String country, String naceCode) {}

    private ResolvedOrgFilters resolveOrgFilters(String sort, String certificationStatus, String organizationType,
            String industrySector, String search, String country, String naceCode) {
        List<String> allowedSorts = List.of("name", "createdAt", "certificationStatus");
        String sortProperty = allowedSorts.contains(sort) ? sort : "name";
        return new ResolvedOrgFilters(
                sortProperty, Sort.by(Sort.Direction.ASC, sortProperty),
                parseCertificationStatus(certificationStatus),
                parseOrganizationType(organizationType),
                parseIndustrySector(industrySector),
                (search != null && !search.isBlank()) ? search.trim() : null,
                (country != null && !country.isBlank()) ? country.trim() : null,
                (naceCode != null && !naceCode.isBlank()) ? naceCode.trim() : null);
    }

    /**
     * Decision-making signals shown on an organisation card: how many datasets it has published
     * (via CKAN, {@code null} when not CKAN-synced or CKAN is unavailable) and the viewer's own
     * collaboration relationship with it ({@code null} when there's no viewer org to compare against,
     * e.g. a platform admin, or when the card is the viewer's own organisation).
     */
    public record OrganizationCardInfo(Integer datasetCount, String relationshipLabel, String relationshipBadgeClass) {}

    @GetMapping
    public String listOrganizations(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "" + ORGANIZATIONS_PAGE_SIZE) int size,
                                    @RequestParam(defaultValue = "name") String sort,
                                    @RequestParam(required = false) String certificationStatus,
                                    @RequestParam(required = false) String organizationType,
                                    @RequestParam(required = false) String industrySector,
                                    @RequestParam(required = false) String search,
                                    @RequestParam(required = false) String country,
                                    @RequestParam(required = false) String naceCode,
                                    @AuthenticationPrincipal User currentUser,
                                    Model model) {
        ResolvedOrgFilters filters = resolveOrgFilters(
                sort, certificationStatus, organizationType, industrySector, search, country, naceCode);

        Page<Organization> organizationsPage = organizationService.findPage(
                page, size, filters.sortOrder(), filters.status(), filters.type(), filters.sector(),
                filters.search(), filters.country(), filters.naceCode());

        model.addAttribute("cardInfoByOrgId", buildCardInfoByOrgId(organizationsPage.getContent(), currentUser));

        model.addAttribute("organizationsPage", organizationsPage);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("sort", filters.sortProperty());
        model.addAttribute("certificationStatus", filters.status() != null ? filters.status().name() : null);
        model.addAttribute("organizationType", filters.type() != null ? filters.type().name() : null);
        model.addAttribute("industrySector", filters.sector() != null ? filters.sector().name() : null);
        model.addAttribute("search", filters.search());
        model.addAttribute("country", filters.country());
        model.addAttribute("naceCode", filters.naceCode());
        model.addAttribute("naceCodesInUse", naceCodeService.findCodesInUse());
        model.addAttribute("certificationStatuses", CertificationStatus.values());
        model.addAttribute("organizationTypes", OrganizationType.values());
        model.addAttribute("industrySectors", IndustrySector.values());
        model.addAttribute("countries", COUNTRIES);
        model.addAttribute("countryPhonePrefixes", COUNTRY_PHONE_PREFIXES);
        model.addAttribute("title", "Organizations");
        return "organizations/list";
    }

    /**
     * Returns just the rendered card-grid fragment for a given page/filter combination, used by the
     * "Load more" button on the Organizations directory to append cards without a full page reload
     * (progressive loading in place of numbered pagination). Total count and whether more pages remain
     * are exposed as response headers so the client doesn't need to parse the HTML to update its state.
     */
    @GetMapping("/cards")
    public String organizationCards(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "" + ORGANIZATIONS_PAGE_SIZE) int size,
                                    @RequestParam(defaultValue = "name") String sort,
                                    @RequestParam(required = false) String certificationStatus,
                                    @RequestParam(required = false) String organizationType,
                                    @RequestParam(required = false) String industrySector,
                                    @RequestParam(required = false) String search,
                                    @RequestParam(required = false) String country,
                                    @RequestParam(required = false) String naceCode,
                                    @AuthenticationPrincipal User currentUser,
                                    Model model,
                                    HttpServletResponse response) {
        ResolvedOrgFilters filters = resolveOrgFilters(
                sort, certificationStatus, organizationType, industrySector, search, country, naceCode);

        Page<Organization> organizationsPage = organizationService.findPage(
                page, size, filters.sortOrder(), filters.status(), filters.type(), filters.sector(),
                filters.search(), filters.country(), filters.naceCode());

        model.addAttribute("organizationsPage", organizationsPage);
        model.addAttribute("cardInfoByOrgId", buildCardInfoByOrgId(organizationsPage.getContent(), currentUser));
        response.setHeader("X-Has-Next", String.valueOf(organizationsPage.hasNext()));
        response.setHeader("X-Total-Elements", String.valueOf(organizationsPage.getTotalElements()));
        return "organizations/list :: cardGrid";
    }

    /**
     * Per-card decision signals: dataset count (CKAN, cached) and the viewer's own collaboration
     * relationship with each listed org. Shared between the full page and the "Load more" fragment
     * endpoint so both render identical card content. Page size is small (18 by default), so a
     * per-card lookup here mirrors the same calls already made individually on the detail page.
     */
    private Map<Long, OrganizationCardInfo> buildCardInfoByOrgId(List<Organization> organizations, User currentUser) {
        Long viewerOrgId = (currentUser != null && currentUser.getOrganization() != null)
                ? currentUser.getOrganization().getId() : null;
        Map<Long, OrganizationCardInfo> cardInfoByOrgId = new java.util.HashMap<>();
        for (Organization org : organizations) {
            Integer datasetCount = null;
            OrganizationSpipUser spipUser = organizationSpipUserRepository
                    .findByOrganizationId(org.getId()).orElse(null);
            if (spipUser != null && spipUser.getCkanOrganizationName() != null
                    && !spipUser.getCkanOrganizationName().isBlank()) {
                datasetCount = datasetStatisticsService.getDatasetCountByOwnerOrg(spipUser.getCkanOrganizationName());
            }

            String relationshipLabel = null;
            String relationshipBadgeClass = null;
            if (viewerOrgId != null && !viewerOrgId.equals(org.getId())) {
                Optional<CollaborationRequest> relationship = collaborationService.findRelationship(viewerOrgId, org.getId());
                CollaborationStatusView statusView = describeCollaborationStatus(
                        relationship.map(CollaborationRequest::getStatus).orElse(null));
                relationshipLabel = statusView.label();
                relationshipBadgeClass = statusView.badgeClass();
            }

            cardInfoByOrgId.put(org.getId(), new OrganizationCardInfo(datasetCount, relationshipLabel, relationshipBadgeClass));
        }
        return cardInfoByOrgId;
    }

    private record CollaborationStatusView(String label, String badgeClass) {}

    /**
     * Narrow, display-only projection of the onboarding request behind the Dataspace Participation
     * block. Field names mirror {@link OrganizationOnboardingRequest}'s so the template is unchanged;
     * everything else on that entity (SPIP password, CKAN API token, contact email/phone, admin
     * notes) is intentionally left out — this view is rendered to any authenticated platform user.
     */
    private record DataspaceParticipationView(String participationGoals, String dataTypes, String dataNeeds,
            String currentSystems, String processedBy, java.time.LocalDateTime processedAt) {}

    /** One tile in the restructured Data Tools section. {@code configured=false} marks a
     * placeholder for a named ecosystem tool the organisation hasn't set up yet. */
    private record DataToolCard(String name, String friendlyDescription, String adminDescription,
            String statusLabel, String statusBadgeClass, String accessLabel, String openUrl,
            String credentialHint, boolean configured) {}

    private record EcosystemToolMeta(String purposeGroup, String friendlyDescription, String credentialHint) {}

    private static final String OTHER_CONNECTORS_GROUP = "Other Connectors";

    /** Named ecosystem tools, grouped by what a user is trying to accomplish rather than
     * by technical connector type — directly answers "what is this for" without requiring prior
     * knowledge of the platform's architecture. Connector types with no entry here (generic data
     * provider/consumer, database, file system, etc.) fall into {@link #OTHER_CONNECTORS_GROUP}. */
    private static final Map<ConnectorType, EcosystemToolMeta> ECOSYSTEM_TOOLS = new LinkedHashMap<>();
    static {
        ECOSYSTEM_TOOLS.put(ConnectorType.FEDERATED_CATALOG_CKAN, new EcosystemToolMeta(
                "Publish & Manage Datasets",
                "Publish datasets to the catalog and manage what your organisation shares with the ecosystem.",
                "Your CKAN API token is shown in the Technical Identifiers section above."));
        ECOSYSTEM_TOOLS.put(ConnectorType.SPIP_PLATFORM, new EcosystemToolMeta(
                "Secure Document & Data Sharing",
                "Manage attribute-based access policies and secure key exchange for controlled data sharing.",
                "Your SPIP credentials are shown in the Technical Identifiers section above."));
        ECOSYSTEM_TOOLS.put(ConnectorType.SPIP_AGENT, new EcosystemToolMeta(
                "Secure Document & Data Sharing",
                "Encrypt and decrypt data locally using your organisation's SPIP credentials.",
                "Your SPIP credentials are shown in the Technical Identifiers section above."));
        ECOSYSTEM_TOOLS.put(ConnectorType.DOCUMENTS_MANAGER, new EcosystemToolMeta(
                "Secure Document & Data Sharing",
                "Securely store, share, and exchange documents with your partners.",
                "Sign in with your SPIP credentials, shown in the Technical Identifiers section above."));
        ECOSYSTEM_TOOLS.put(ConnectorType.EDC_CONNECTOR, new EcosystemToolMeta(
                "Sovereign Data Exchange",
                "Exchange data directly and securely with partner organisations using the Eclipse Dataspace Connector.",
                null));
    }
    private static final List<String> DATA_TOOL_GROUP_ORDER = List.of(
            "Publish & Manage Datasets", "Secure Document & Data Sharing", "Sovereign Data Exchange",
            OTHER_CONNECTORS_GROUP);

    /**
     * Groups this organisation's connectors by purpose instead of showing them as a flat technical
     * list. Named ecosystem tools (CKAN, SPIP, EDC, Documents Manager) get a task-oriented
     * description and, when missing, a "not yet configured" placeholder — visible only to the
     * organisation's own members, since telling a collaborator what a partner hasn't set up yet
     * isn't useful to them. Everything else (custom/generic connectors) lands in "Other Connectors"
     * so it doesn't get mixed in with the named ecosystem tools and dilute their meaning.
     */
    private Map<String, List<DataToolCard>> buildDataToolGroups(Organization organization, boolean isMyOrganization,
            boolean canViewOrgCredentials, String ckanBaseUrl, String ckanOrgSlug) {
        Map<String, List<DataToolCard>> groups = new LinkedHashMap<>();
        for (String group : DATA_TOOL_GROUP_ORDER) {
            groups.put(group, new java.util.ArrayList<>());
        }

        Set<ConnectorType> presentTypes = new HashSet<>();
        for (Connector connector : organization.getConnectors()) {
            presentTypes.add(connector.getType());
            EcosystemToolMeta meta = ECOSYSTEM_TOOLS.get(connector.getType());
            String group = meta != null ? meta.purposeGroup() : OTHER_CONNECTORS_GROUP;
            String friendlyDescription = meta != null ? meta.friendlyDescription() : connector.getDescription();
            String credentialHint = meta != null ? meta.credentialHint() : null;
            String accessLabel = credentialHint == null ? null
                    : canViewOrgCredentials ? "Available to your organisation admin"
                    : "Requires credentials — ask your organisation admin";

            String openUrl = null;
            if (connector.getType() == ConnectorType.FEDERATED_CATALOG_CKAN && ckanOrgSlug != null) {
                // Opens the org's catalog page rather than the raw connector endpoint, which points
                // at the CKAN API base, not a page a user would want to browse to.
                openUrl = ckanBaseUrl + "/organization/" + ckanOrgSlug;
            } else if (connector.getEndpoint() != null
                    && (connector.getEndpoint().startsWith("http://") || connector.getEndpoint().startsWith("https://"))) {
                openUrl = connector.getEndpoint();
            }

            groups.get(group).add(new DataToolCard(connector.getName(), friendlyDescription,
                    connector.getDescription(), connector.getStatus().getDisplayName(),
                    connector.getStatusBadgeClass(), accessLabel, openUrl, credentialHint, true));
        }

        if (isMyOrganization) {
            for (Map.Entry<ConnectorType, EcosystemToolMeta> entry : ECOSYSTEM_TOOLS.entrySet()) {
                if (presentTypes.contains(entry.getKey())) {
                    continue;
                }
                EcosystemToolMeta meta = entry.getValue();
                groups.get(meta.purposeGroup()).add(new DataToolCard(entry.getKey().getDisplayName(),
                        meta.friendlyDescription(), null, "Not configured", "bg-light text-dark border",
                        null, null, null, false));
            }
        }

        groups.values().removeIf(List::isEmpty);
        return groups;
    }

    /**
     * Maps a viewer org's relationship with another org to the same label/badge pair shown on both
     * the Organizations directory cards and the Organization Detail page, so the two never drift out
     * of sync. REJECTED (and no relationship at all) both read as "No collaboration yet" since, from
     * the viewer's perspective, nothing is currently active or awaiting a decision.
     */
    private CollaborationStatusView describeCollaborationStatus(CollaborationStatus status) {
        if (status == CollaborationStatus.APPROVED) {
            return new CollaborationStatusView("Collaboration active", "bg-success");
        }
        if (status == CollaborationStatus.PENDING) {
            return new CollaborationStatusView("Request pending", "bg-warning");
        }
        return new CollaborationStatusView("No collaboration yet", "bg-secondary");
    }

    @GetMapping("/my-organization")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_MEMBER', 'SPIP_PRIVILEGED_USER')")
    public String viewMyOrganization(@AuthenticationPrincipal User currentUser,
                                    RedirectAttributes redirectAttributes) {
        // The principal could not be resolved to a User (e.g. a session whose SecurityContext
        // was restored with only the username). Send the visitor to login instead of throwing
        // a 500 NPE — mirrors the unauthenticated handling in GlobalExceptionHandler.
        if (currentUser == null) {
            return "redirect:/login";
        }

        // Check if user has an organization
        if (currentUser.getOrganization() == null) {
            logger.warn("User {} attempted to access My Organization but has no organization assigned",
                    currentUser.getUsername());
            redirectAttributes.addFlashAttribute("error",
                    "You are not currently associated with any organization.");
            return "redirect:/";
        }

        // Redirect to the organization view page
        Long organizationId = currentUser.getOrganization().getId();
        logger.info("User {} accessing their organization (ID: {})",
                currentUser.getUsername(), organizationId);
        return "redirect:/organizations/" + organizationId;
    }

    @GetMapping("/{id}")
    public String viewOrganization(@PathVariable Long id, Model model,
                                  @AuthenticationPrincipal User currentUser) {
        Organization organization = organizationService.findByIdWithMembersAndConnectors(id)
                .orElseThrow(() -> new EntityNotFoundException("Organization", id));

        model.addAttribute("organization", organization);
        model.addAttribute("title", organization.getName());

        // Set active page based on whether user is viewing their own organization
        boolean isMyOrganization = currentUser != null
                && currentUser.getOrganization() != null
                && currentUser.getOrganization().getId().equals(id);
        model.addAttribute("activePage", isMyOrganization ? "my-organization" : "organizations");
        model.addAttribute("isMyOrganization", isMyOrganization);

        // Collaborators list (always shown)
        model.addAttribute("collaborators", collaborationService.findApprovedCollaborators(id));

        boolean isPlatformAdmin = currentUser != null && currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_PLATFORM_ADMIN"));
        boolean isOrgAdmin = currentUser != null && currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ORG_ADMIN"));
        boolean userHasOrg = currentUser != null && currentUser.getOrganization() != null;

        // Fetch relationship once for all users with an org viewing another org
        java.util.Optional<CollaborationRequest> relationship = java.util.Optional.empty();
        if (userHasOrg && !isMyOrganization) {
            relationship = collaborationService.findRelationship(currentUser.getOrganization().getId(), id);
        }

        // Collaboration Status block: a passive, always-visible-to-org-members summary of where
        // things stand with this org, distinct from the actionable button below (which only
        // ORG_ADMIN can use, since only admins can actually send/cancel requests).
        if (userHasOrg && !isMyOrganization) {
            CollaborationStatusView statusView = describeCollaborationStatus(
                    relationship.map(CollaborationRequest::getStatus).orElse(null));
            model.addAttribute("collaborationStatusLabel", statusView.label());
            model.addAttribute("collaborationStatusBadgeClass", statusView.badgeClass());
        }

        // Gate: contact info, members, connectors only visible to own org, platform admin, or approved collaborator
        boolean canViewDetails = isPlatformAdmin || isMyOrganization
                || (relationship.isPresent() && relationship.get().getStatus() == CollaborationStatus.APPROVED);
        model.addAttribute("canViewDetails", canViewDetails);

        // CKAN org slug for this organisation, used by the "Publish & Manage Datasets" Data Tools
        // card below to link out to the org's catalog page in CKAN. Renders as null when the org
        // isn't CKAN-synced (the Data Tools card falls back to the raw connector endpoint then).
        OrganizationSpipUser orgSpipUser = organizationSpipUserRepository
                .findByOrganizationId(id).orElse(null);
        String ckanOrgSlug = null;
        String ckanBaseUrl = null;
        if (orgSpipUser != null
                && orgSpipUser.getCkanOrganizationName() != null
                && !orgSpipUser.getCkanOrganizationName().isBlank()) {
            ckanOrgSlug = orgSpipUser.getCkanOrganizationName();
            ckanBaseUrl = ckanSettingsService.getBaseUrl();
        }

        // Dataspace Participation block: the applicant's own words on why they joined and what
        // data they bring/seek, captured at onboarding time. Organizations created directly by a
        // platform admin (skipping /join) have no such request; the template shows an empty state.
        // Deliberately narrowed to a view record rather than exposing the OrganizationOnboardingRequest
        // entity itself, which also carries the SPIP password, CKAN API token, contact email/phone,
        // and admin notes — none of which belong on a page visible to any authenticated platform user.
        onboardingRequestService.findByOrganizationId(id).ifPresent(req ->
                model.addAttribute("dataspaceRequest", new DataspaceParticipationView(
                        req.getParticipationGoals(), req.getDataTypes(), req.getDataNeeds(),
                        req.getCurrentSystems(), req.getProcessedBy(), req.getProcessedAt())));

        // Organisation-level connector credentials (SPIP password, CKAN API token). The approval
        // email intentionally no longer carries these and points the new admin here instead, so
        // surface them to the org's own admin (and platform admins) — never to ordinary members
        // or other organisations. The SPIP password is read from organization.spipUser in the
        // template; here we expose only the CKAN token, pulled from the org's CKAN connector.
        boolean canViewOrgCredentials = isPlatformAdmin || (isMyOrganization && isOrgAdmin);
        model.addAttribute("canViewOrgCredentials", canViewOrgCredentials);
        // Whether the current user can edit this org (drives the "Add address/website/..." prompts
        // on empty public fields).
        model.addAttribute("canEdit", isPlatformAdmin || (isMyOrganization && isOrgAdmin));
        if (canViewOrgCredentials) {
            organization.getConnectors().stream()
                    .filter(c -> c.getType() == ConnectorType.FEDERATED_CATALOG_CKAN)
                    .map(Connector::getApiToken)
                    .filter(token -> token != null && !token.isBlank())
                    .findFirst()
                    .ifPresent(token -> model.addAttribute("ckanApiToken", token));

            // Keycloak first-user credentials: the secure-mode approval email carries no
            // secrets, so this panel is the delivery channel for the temporary password.
            // Source: the onboarding request (approval flow) or the org's SPIP-user record
            // (direct admin creation flow).
            onboardingRequestService.findByOrganizationId(id)
                    .filter(req -> req.getKeycloakUsername() != null)
                    .ifPresentOrElse(req -> {
                        model.addAttribute("keycloakUsername", req.getKeycloakUsername());
                        model.addAttribute("keycloakTempPassword", req.getKeycloakTempPassword());
                        if (req.getKeycloakBaseUrl() != null && req.getKeycloakRealm() != null) {
                            model.addAttribute("keycloakAccountUrl",
                                    req.getKeycloakBaseUrl() + "/realms/" + req.getKeycloakRealm() + "/account");
                        }
                    }, () -> {
                        if (organization.getSpipUser() != null
                                && organization.getSpipUser().getKeycloakUsername() != null) {
                            model.addAttribute("keycloakUsername", organization.getSpipUser().getKeycloakUsername());
                            model.addAttribute("keycloakTempPassword", organization.getSpipUser().getKeycloakTempPassword());
                            model.addAttribute("keycloakAccountUrl", organization.getSpipUser().getKeycloakAccountUrl());
                        }
                    });
        }

        // Data Tools: restructured by purpose (rather than a flat technical list) so the user can
        // recognise what each tool is for without prior knowledge of the platform's architecture.
        // Only computed when the card itself is visible.
        if (canViewDetails) {
            model.addAttribute("dataToolGroups",
                    buildDataToolGroups(organization, isMyOrganization, canViewOrgCredentials, ckanBaseUrl, ckanOrgSlug));
        }

        // Collaboration button state (ORG_ADMIN viewing another org only)
        if (isOrgAdmin && !isMyOrganization && userHasOrg) {
            if (relationship.isEmpty()) {
                model.addAttribute("collabButtonState", "NONE");
            } else {
                CollaborationRequest req = relationship.get();
                model.addAttribute("collaborationRequest", req);
                model.addAttribute("collabButtonState", req.getStatus().name());
            }
        }

        // Combined rejection + cancellation history between the two orgs (both sides can see it)
        if (userHasOrg && !isMyOrganization) {
            model.addAttribute("collaborationHistory",
                    collaborationService.findHistoryBetween(currentUser.getOrganization().getId(), id));
        }

        // Organization setup-status checklist (own org admin only) — an actionable summary of what
        // still needs attention before the organisation is fully usable.
        if (isMyOrganization && isOrgAdmin) {
            List<SetupChecklistItem> checklist = buildSetupChecklist(organization, currentUser);
            model.addAttribute("setupChecklist", checklist);
            long doneCount = checklist.stream().filter(SetupChecklistItem::isDone).count();
            int pending = checklist.size() - (int) doneCount;
            model.addAttribute("setupNeedsAttention", pending > 0);
            // Feed the "Organisation Status" summary in the sidebar.
            model.addAttribute("profileCompletionPercent",
                    checklist.isEmpty() ? 100 : Math.round((doneCount * 100f) / checklist.size()));
            model.addAttribute("pendingActionsCount", pending);
        }

        return "organizations/view";
    }

    /**
     * Build the "Organization setup status" checklist for the org's own admin. Each item reflects
     * a concrete setup step and links to where the admin can complete it.
     */
    private List<SetupChecklistItem> buildSetupChecklist(Organization org, User currentUser) {
        String editUrl = "/organizations/" + org.getId() + "/edit";
        boolean naceDone = org.getNaceCodes() != null && !org.getNaceCodes().isEmpty()
                && !org.hasDefaultNaceCodeOnly();
        boolean contactDone = !isBlank(org.getPrimaryContactName())
                && !isBlank(org.getPrimaryContactTitle())
                && !isBlank(org.getContactPhone());
        boolean toolsDone = org.getConnectors() != null && !org.getConnectors().isEmpty();
        boolean membersDone = org.getMembers() != null && org.getMembers().size() > 1;

        List<SetupChecklistItem> items = new java.util.ArrayList<>();
        items.add(new SetupChecklistItem("Change your temporary password",
                currentUser != null && !currentUser.isMustChangePassword(),
                "Change password", "/profile/change-password"));
        items.add(new SetupChecklistItem("Update your NACE Code",
                naceDone, "Update NACE Code", editUrl + "#naceCodes"));
        items.add(new SetupChecklistItem("Complete contact information",
                contactDone, "Complete contact info", editUrl));
        items.add(new SetupChecklistItem("Review available data tools",
                toolsDone, "Review data tools", "/connectors"));
        items.add(new SetupChecklistItem("Invite or confirm organisation members",
                membersDone, "Manage members", editUrl));
        return items;
    }

    @GetMapping("/new")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public String newOrganizationForm(Model model,
                                     @RequestParam(required = false) String spipUsername,
                                     @RequestParam(required = false) String spipPassword,
                                     @RequestParam(required = false) String orgName,
                                     @RequestParam(required = false) String orgType,
                                     @RequestParam(required = false) String industrySector) {
        Organization organization = new Organization();

        // If SPIP credentials are provided, pre-populate organization data
        if (spipUsername != null && !spipUsername.isEmpty()) {
            model.addAttribute("spipUsername", spipUsername);
            model.addAttribute("spipPassword", spipPassword);
            model.addAttribute("fromSpip", true);

            // Pre-populate organization name from orgId attribute
            if (orgName != null && !orgName.isEmpty()) {
                organization.setName(orgName);
                logger.info("Pre-populated organization name: {}", orgName);
            } else {
                // Fallback: Use SPIP username as a basis for organization name
                organization.setName(generateOrganizationNameFromSpipUser(spipUsername));
                logger.info("Fallback: Generated organization name from username: {}", spipUsername);
            }

            // Track if orgType was successfully set from SPIP
            boolean orgTypeSetFromSpip = false;

            // Pre-populate organization type from orgType attribute
            if (orgType != null && !orgType.isEmpty()) {
                try {
                    // Convert SPIP orgType values to enum format
                    // SPIP uses lowercase with underscores (e.g., "technology_provider")
                    // Enum expects uppercase with underscores (e.g., "TECHNOLOGY_PROVIDER")
                    String normalizedOrgType = orgType.toUpperCase().replaceAll("-", "_");
                    OrganizationType type = OrganizationType.valueOf(normalizedOrgType);
                    organization.setType(type);
                    orgTypeSetFromSpip = true;
                    logger.info("Pre-populated organization type: {} -> {}", orgType, type);
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid orgType from SPIP: {}. User will need to select manually.", orgType);
                    model.addAttribute("invalidOrgType", true);
                    model.addAttribute("invalidOrgTypeValue", orgType);
                }
            }

            // Pre-populate industry sector from industrySector attribute
            if (industrySector != null && !industrySector.isEmpty()) {
                // Map SPIP industrySector values to IndustrySector enum
                IndustrySector mappedIndustrySector = mapIndustrySectorToEnum(industrySector);
                if (mappedIndustrySector != null) {
                    organization.setIndustrySector(mappedIndustrySector);
                    logger.info("Pre-populated industry sector: {} -> {}", industrySector, mappedIndustrySector);
                }
            }

            // Set flags for template to control field locking
            model.addAttribute("orgTypeSetFromSpip", orgTypeSetFromSpip);
            model.addAttribute("message", "Organization form pre-populated from SPIP user: " + spipUsername);
        } else {
            // Explicitly set fromSpip to false for manual creation
            model.addAttribute("fromSpip", false);
            model.addAttribute("orgTypeSetFromSpip", false);
        }

        model.addAttribute("organization", organization);
        model.addAttribute("organizationTypes", OrganizationType.values());
        model.addAttribute("countries", COUNTRIES);
        model.addAttribute("countryPhonePrefixes", COUNTRY_PHONE_PREFIXES);
        model.addAttribute("selectedNaceCodes", List.of());
        model.addAttribute("title", "New Organization");
        return "organizations/form";
    }

    /**
     * Generate a suggested organization name from SPIP username.
     * Converts username to a more readable format.
     */
    private String generateOrganizationNameFromSpipUser(String spipUsername) {
        if (spipUsername == null || spipUsername.isEmpty()) {
            return "";
        }

        // Convert underscores/hyphens to spaces and capitalize words
        String name = spipUsername.replaceAll("[_-]", " ");
        String[] words = name.split("\\s+");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
                result.append(" ");
            }
        }

        return result.toString().trim();
    }

    @PostMapping("/new")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public String createOrganization(@Valid @ModelAttribute Organization organization,
                                   BindingResult result,
                                   @RequestParam(required = false) String spipUsername,
                                   @RequestParam(required = false) String spipPassword,
                                   @RequestParam(required = false) List<String> naceCodes,
                                   Model model,
                                   RedirectAttributes redirectAttributes,
                                   @AuthenticationPrincipal User currentUser) {

        // Principal could not be resolved to a User (see viewMyOrganization) — bail to login
        // rather than throwing a 500 NPE further down.
        if (currentUser == null) {
            return "redirect:/login";
        }

        // Check if SPIP credentials are provided
        boolean hasSpipCredentials = spipUsername != null && !spipUsername.isEmpty()
                                    && spipPassword != null && !spipPassword.isEmpty();

        // Validate NACE codes - at least one required, must be 4-digit class codes, and all must resolve
        if (naceCodes == null || naceCodes.isEmpty()) {
            result.reject("naceCodes", "At least one NACE code is required");
        } else if (naceCodes.stream().anyMatch(c -> !NaceCode.isClassCode(c))) {
            result.reject("naceCodes", "Only 4-digit NACE class codes are allowed (e.g. '01.11'). 2- and 3-digit codes denote broader families and cannot be selected.");
        } else {
            List<NaceCode> resolvedCodes = naceCodeService.findByCodes(naceCodes);
            if (resolvedCodes.size() != naceCodes.size()) {
                result.reject("naceCodes", "One or more NACE codes are invalid");
            } else {
                organization.setNaceCodes(new HashSet<>(resolvedCodes));
            }
        }

        if (organizationService.existsByName(organization.getName())) {
            result.rejectValue("name", "error.organization", "Organization name already exists");
        }

        // Check if email already exists (for both SPIP and manual creation)
        if (organization.getContactEmail() != null && !organization.getContactEmail().isEmpty()) {
            // Check if email exists in other organizations
            if (organizationService.existsByContactEmail(organization.getContactEmail())) {
                result.rejectValue("contactEmail", "error.organization",
                    "An organization with this email address already exists in the system");
            }

            // Check if email exists in existing users
            if (userService.existsByEmail(organization.getContactEmail())) {
                result.rejectValue("contactEmail", "error.organization",
                    "A user with this email address already exists in the system");
            }

            // Check if email exists in pending onboarding requests
            if (onboardingRequestService.existsByEmail(organization.getContactEmail())) {
                result.rejectValue("contactEmail", "error.organization",
                    "An onboarding request with this email address already exists");
            }
        }

        // Check if organization already exists in CKAN (for SPIP-based creation)
        // Store flag for later use instead of blocking creation
        boolean ckanOrganizationExists = false;
        if (hasSpipCredentials) {
            try {
                ckanOrganizationExists = ckanSyncService.checkOrganizationExists(organization.getName());
                if (ckanOrganizationExists) {
                    logger.info("Organization '{}' already exists in CKAN. Will proceed with portal creation and link to existing CKAN organization.",
                        organization.getName());
                }
            } catch (Exception e) {
                logger.warn("Failed to check CKAN organization existence: {}", e.getMessage());
                // Continue with creation if check fails
            }
        }

        if (result.hasErrors()) {
            model.addAttribute("organizationTypes", OrganizationType.values());
            model.addAttribute("countries", COUNTRIES);
            model.addAttribute("countryPhonePrefixes", COUNTRY_PHONE_PREFIXES);
            model.addAttribute("selectedNaceCodes",
                    naceCodes != null ? naceCodeService.findByCodes(naceCodes) : List.of());
            model.addAttribute("title", "New Organization");
            // Preserve SPIP data if present
            if (hasSpipCredentials) {
                model.addAttribute("spipUsername", spipUsername);
                model.addAttribute("spipPassword", spipPassword);
                model.addAttribute("fromSpip", true);
            } else {
                model.addAttribute("fromSpip", false);
            }
            return "organizations/form";
        }

        if (hasSpipCredentials) {
            // FLOW 1: Organization created FROM SPIP USER
            logger.info("Creating organization FROM SPIP USER: {}, Email: {}",
                organization.getName(), organization.getContactEmail());

            // Decode Base64 password from URL - will be stored with EncryptedStringConverter
            String decodedSpipPassword = new String(Base64.getDecoder().decode(spipPassword.getBytes()));

            // Create the organization directly and link SPIP credentials
            Organization savedOrganization = organizationService.save(organization);
            logger.info("Organization created successfully from SPIP user: {} (ID: {})",
                savedOrganization.getName(), savedOrganization.getId());

            // Materialize config-only tool connectors (e.g. EDC) for manually created organizations too
            toolConnectorService.materializeConfigOnlyConnectors(savedOrganization);

            try {
                OrganizationSpipUser spipUser = spipUserService.createOrganizationSpipUser(savedOrganization, spipUsername, decodedSpipPassword);
                logger.info("SPIP user credentials linked to organization: {}", savedOrganization.getName());

                // Provision Keycloak like the onboarding sync tool does (group named after the
                // SPIP username + first user from the contact email). Best-effort: a failure is
                // logged and surfaced but never blocks the organization creation.
                KeycloakDirectProvisioningResult keycloak =
                        provisionKeycloakForDirectCreation(savedOrganization, spipUser, spipUsername,
                                organization.getContactEmail(), organization.getPrimaryContactName());

                // Create initial admin user for the organization with temporary password
                logger.info("Attempting to create admin user and send email for organization: {}", savedOrganization.getName());
                String temporaryPassword = null;
                if (organization.getContactEmail() != null && !organization.getContactEmail().isEmpty()) {
                    logger.info("Contact email provided: {}", organization.getContactEmail());
                    try {
                        // Check if user with this email already exists
                        if (!userService.existsByEmail(organization.getContactEmail())) {
                            logger.info("User does not exist, creating new user for email: {}", organization.getContactEmail());
                            User adminUser = createAdminUserForOrganization(savedOrganization);
                            if (keycloak.userId() != null) {
                                adminUser.setKeycloakUserId(keycloak.userId());
                            }
                            User savedAdminUser = userService.saveWithoutEncoding(adminUser);
                            temporaryPassword = adminUser.getTemporaryPasswordForEmail();
                            logger.info("Admin user created for organization: {} with email: {}",
                                savedOrganization.getName(), adminUser.getEmail());

                            // Mint a one-time secure link for the user to set their own password.
                            String setPasswordUrl = passwordResetService.createSetPasswordLink(
                                    savedAdminUser, PasswordResetService.ONBOARDING_TOKEN_EXPIRY_HOURS);

                            // Send approval email. Secure mode (default/prod) uses only setPasswordUrl;
                            // legacy mode (dev/test) inlines the credentials below.
                            emailService.sendOnboardingApproval(
                                com.data4circ.portal.features.organization.dto.OnboardingApprovalEmail.builder()
                                    .toEmail(organization.getContactEmail())
                                    .companyName(savedOrganization.getName())
                                    .contactName(organization.getPrimaryContactName())
                                    .username(adminUser.getUsername())
                                    .temporaryPassword(temporaryPassword)
                                    .spipUsername(spipUsername)
                                    .spipPassword(decodedSpipPassword)
                                    .keycloakUsername(keycloak.username())
                                    .keycloakPassword(keycloak.tempPassword())
                                    .keycloakAccountUrl(keycloak.accountUrl())
                                    .setPasswordUrl(setPasswordUrl)
                                    .build());
                            logger.info("Onboarding approval email sent to: {}", organization.getContactEmail());
                        } else {
                            logger.warn("User with email {} already exists, skipping user creation and email",
                                organization.getContactEmail());
                        }
                    } catch (Exception e) {
                        logger.error("Failed to create admin user or send email: {}", e.getMessage(), e);
                    }
                }

                // Synchronize with CKAN (regardless of user creation outcome)
                logger.info("Synchronizing with CKAN...");
                String ckanMessage = "";
                try {
                    CkanSyncResult ckanResult =
                        ckanSyncService.synchronizeDirectCreation(
                            savedOrganization.getName(),
                            spipUsername,
                            decodedSpipPassword,
                            organization.getContactEmail()
                        );

                    // Update OrganizationSpipUser with CKAN sync info
                    if (ckanResult.isSuccess() && ckanResult.getCkanOrgShortName() != null) {
                        spipUser.setCkanOrganizationName(ckanResult.getCkanOrgShortName());
                        spipUser.setCkanSynchronized(true);
                        spipUserService.save(spipUser);
                        logger.info("Updated SpipUser with CKAN organization: {}", ckanResult.getCkanOrgShortName());

                        // Sync NACE codes to CKAN (blocks creation if sync fails)
                        if (savedOrganization.getNaceCodes() != null && !savedOrganization.getNaceCodes().isEmpty()) {
                            ckanSyncService.syncNaceCodes(ckanResult.getCkanOrgShortName(), savedOrganization.getNaceCodes());
                        }
                    } else if (!ckanResult.isSuccess()) {
                        throw new RuntimeException("CKAN synchronization failed: " + ckanResult.getErrorMessage());
                    }

                    ckanMessage = ckanResult.buildSummaryMessage();
                } catch (Exception e) {
                    logger.error("Failed to synchronize with CKAN: {}", e.getMessage(), e);
                    redirectAttributes.addFlashAttribute("error",
                            "Organization was created but CKAN synchronization failed: " + e.getMessage() +
                            ". Please edit the organization to retry NACE code synchronization.");
                    return "redirect:/organizations/" + savedOrganization.getId();
                }

                // Build user feedback message
                StringBuilder message = new StringBuilder("Organization created successfully, linked with SPIP user: " + spipUsername);
                if (temporaryPassword != null) {
                    message.append(", credentials sent to ").append(organization.getContactEmail());
                } else if (organization.getContactEmail() != null && !organization.getContactEmail().isEmpty()) {
                    message.append(". Note: User with email ").append(organization.getContactEmail()).append(" already exists");
                } else {
                    message.append(". No contact email provided, please create admin user manually");
                }
                message.append(". ").append(ckanMessage);

                if (ckanOrganizationExists) {
                    message.append(" Note: The organization already existed in CKAN and has been linked to the portal.");
                    redirectAttributes.addFlashAttribute("ckanExistsWarning", true);
                }

                redirectAttributes.addFlashAttribute("message", message.toString());
            } catch (Exception e) {
                logger.error("Failed to link SPIP credentials to organization: {}", e.getMessage(), e);
                String message = "Organization created successfully, but failed to link SPIP credentials. You can link them later.";

                // Add info message if organization already existed in CKAN
                if (ckanOrganizationExists) {
                    message += " Note: The organization already existed in CKAN and has been linked to the portal.";
                    redirectAttributes.addFlashAttribute("ckanExistsWarning", true);
                }

                redirectAttributes.addFlashAttribute("message", message);
            }

            return "redirect:/organizations/" + savedOrganization.getId();

        } else {
            // FLOW 2: Organization created FROM SCRATCH (no SPIP user)
            // Create OnboardingRequest in PENDING state for admin review
            try {
                OrganizationOnboardingRequest onboardingRequest =
                    new OrganizationOnboardingRequest();

                // Map organization data to onboarding request
                onboardingRequest.setCompanyName(organization.getName());
                onboardingRequest.setCompanyType(organization.getType() != null ? organization.getType().name() : null);
                onboardingRequest.setIndustry(organization.getIndustrySector() != null ?
                    organization.getIndustrySector().getDisplayName() : null);
                onboardingRequest.setCompanySize(organization.getCompanySize());
                onboardingRequest.setAddress(organization.getAddress());
                onboardingRequest.setCountry(organization.getCountry());
                onboardingRequest.setOrganizationDescription(organization.getDescription());
                onboardingRequest.setWebsite(organization.getWebsite());

                // Contact information
                onboardingRequest.setContactName(organization.getPrimaryContactName());
                onboardingRequest.setContactTitle(organization.getPrimaryContactTitle());
                onboardingRequest.setEmail(organization.getContactEmail());
                onboardingRequest.setPhone(organization.getContactPhone());

                // Set default values for required fields
                onboardingRequest.setParticipationGoals("Created by admin from portal");
                onboardingRequest.setGdprConsent(true);

                // Set status to PENDING for admin review
                onboardingRequest.setStatus(OnboardingRequestStatus.PENDING);

                // Set admin notes to indicate this was created manually
                onboardingRequest.setAdminNotes("Organization created manually by admin: " + currentUser.getUsername());

                // Set NACE codes as comma-separated string
                if (naceCodes != null && !naceCodes.isEmpty()) {
                    onboardingRequest.setNaceCodes(String.join(",", naceCodes));
                }

                // Same checks the public /join flow runs (unique email/company name, SPIP
                // access-policy label collision, well-formed NACE codes) - this admin path must
                // not bypass them just because it saves the request directly.
                onboardingRequestService.validateForCreation(onboardingRequest);

                // Save the onboarding request
                OrganizationOnboardingRequest savedRequest =
                    onboardingRequestService.save(onboardingRequest);

                logger.info("Onboarding request created for organization '{}' in PENDING state",
                    organization.getName());

                // Send confirmation email to the organization contact
                try {
                    emailService.sendOnboardingConfirmation(
                        savedRequest.getEmail(),
                        savedRequest.getCompanyName(),
                        savedRequest.getContactName(),
                        savedRequest.getId(),
                        savedRequest.getCreatedAt()
                    );
                    logger.info("Onboarding confirmation email sent to: {}", savedRequest.getEmail());
                } catch (Exception e) {
                    logger.error("Failed to send onboarding confirmation email: {}", e.getMessage(), e);
                    // Don't fail the entire operation if email fails
                }

                redirectAttributes.addFlashAttribute("message",
                    "Organization request created successfully and placed in review queue. " +
                    "Confirmation email sent to " + savedRequest.getEmail() + ". " +
                    "Please go to Onboarding tab to complete SPIP synchronization and approve.");

                return "redirect:/admin/onboarding";

            } catch (Exception e) {
                logger.error("Failed to create onboarding request: {}", e.getMessage(), e);
                redirectAttributes.addFlashAttribute("error",
                    "Failed to create organization request: " + e.getMessage());
                // Back to the form, not the list: organizations/list.html has no ${error}
                // rendering at all, so the flash message above would silently disappear there.
                return "redirect:/organizations/new";
            }
        }
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or (hasRole('ORG_ADMIN') and @organizationService.findById(#id).get().id == authentication.principal.organization.id)")
    public String editOrganizationForm(@PathVariable Long id, Model model, Authentication authentication) {
        Organization organization = organizationService.findByIdWithMembersAndConnectors(id)
                .orElseThrow(() -> new EntityNotFoundException("Organization", id));

        // Explicit authorization check (defense in depth). Reject a principal that is not a
        // User (e.g. a session whose SecurityContext was restored with only the username) so
        // the cast below cannot throw a ClassCastException.
        if (authentication == null || !(authentication.getPrincipal() instanceof User)) {
            throw new AccessDeniedException(
                    "You must be authenticated to edit organizations");
        }

        User currentUser = (User) authentication.getPrincipal();
        boolean isPlatformAdmin = authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_PLATFORM_ADMIN"));
        boolean isOrgAdmin = authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ORG_ADMIN"));

        // Check if user belongs to this organization
        boolean isOwnOrganization = false;
        if (currentUser.getOrganization() != null) {
            isOwnOrganization = currentUser.getOrganization().getId().equals(id);
        }

        if (!isPlatformAdmin && !(isOrgAdmin && isOwnOrganization)) {
            throw new AccessDeniedException(
                    "You do not have permission to edit this organization");
        }

        model.addAttribute("organization", organization);
        model.addAttribute("organizationTypes", OrganizationType.values());
        model.addAttribute("countries", COUNTRIES);
        model.addAttribute("countryPhonePrefixes", COUNTRY_PHONE_PREFIXES);
        model.addAttribute("selectedNaceCodes", organization.getNaceCodes());
        model.addAttribute("title", "Edit Organization");
        model.addAttribute("fromSpip", false); // Editing existing org, not from SPIP

        // Collaboration requests management
        List<CollaborationRequest> pendingRequests =
                collaborationService.findPendingIncomingRequests(id);
        model.addAttribute("pendingCollaborationRequests", pendingRequests);
        model.addAttribute("collaborators",
                collaborationService.findApprovedCollaboratorViews(id));

        // Prior rejections this org has issued against each pending requester, so the admin
        // has context before deciding. Keyed by requester org id.
        Map<Long, List<CollaborationRejection>> priorRejectionsByRequester = new HashMap<>();
        for (CollaborationRequest req : pendingRequests) {
            Long requesterId = req.getRequesterOrg().getId();
            priorRejectionsByRequester.put(requesterId,
                    collaborationService.findRejectionsByTargetAgainst(requesterId, id));
        }
        model.addAttribute("priorRejectionsByRequester", priorRejectionsByRequester);

        // Standalone traceable history of all requests this org has rejected.
        model.addAttribute("pastRejections", collaborationService.findRejectionsIssuedBy(id));

        // Profile completeness indicator (visibility of system status).
        model.addAttribute("profileCompletion", computeProfileCompletion(organization));

        return "organizations/form";
    }

    /**
     * Compute how complete an organisation's editable profile is, for the completeness indicator
     * on the edit page. Each check is an editable field that meaningfully improves the public
     * profile; the percentage is the share of checks satisfied, and the pending list gives the
     * user concrete next actions. Locked fields (name, type, sector, email) are not counted
     * because the user cannot change them.
     */
    private ProfileCompletion computeProfileCompletion(Organization org) {
        java.util.List<String> pending = new java.util.ArrayList<>();
        int total = 0;

        total++; if (isBlank(org.getDescription())) pending.add("Add an organisation description");
        total++; if (isBlank(org.getCountry())) pending.add("Select a country");
        total++; if (isBlank(org.getAddress())) pending.add("Add a company address");
        total++; if (isBlank(org.getWebsite())) pending.add("Add a company website");
        total++; if (org.hasDefaultNaceCodeOnly()) pending.add("Update your NACE Code");
        total++; if (isBlank(org.getPrimaryContactName())) pending.add("Add a primary contact name");
        total++; if (isBlank(org.getPrimaryContactTitle())) pending.add("Add a contact job title");
        total++; if (isBlank(org.getContactPhone())) pending.add("Add a phone number");

        int completed = total - pending.size();
        int percentage = total == 0 ? 100 : Math.round((completed * 100f) / total);
        return new ProfileCompletion(percentage, pending);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or (hasRole('ORG_ADMIN') and @organizationService.findById(#id).get().id == authentication.principal.organization.id)")
    public String updateOrganization(@PathVariable Long id,
                                   @Valid @ModelAttribute Organization organization,
                                   BindingResult result,
                                   @RequestParam(required = false) List<String> naceCodes,
                                   Model model,
                                   RedirectAttributes redirectAttributes,
                                   Authentication authentication) {

        Organization existingOrg = organizationService.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Organization", id));

        // Validate NACE codes - at least one required, must be 4-digit class codes, and all must resolve
        if (naceCodes == null || naceCodes.isEmpty()) {
            result.reject("naceCodes", "At least one NACE code is required");
        } else if (naceCodes.stream().anyMatch(c -> !NaceCode.isClassCode(c))) {
            result.reject("naceCodes", "Only 4-digit NACE class codes are allowed (e.g. '01.11'). 2- and 3-digit codes denote broader families and cannot be selected.");
        } else {
            List<NaceCode> resolvedCodes = naceCodeService.findByCodes(naceCodes);
            if (resolvedCodes.size() != naceCodes.size()) {
                result.reject("naceCodes", "One or more NACE codes are invalid");
            } else {
                organization.setNaceCodes(new HashSet<>(resolvedCodes));
            }
        }

        // Explicit authorization check (defense in depth). Reject a principal that is not a
        // User (e.g. a session whose SecurityContext was restored with only the username) so
        // the cast below cannot throw a ClassCastException.
        if (authentication == null || !(authentication.getPrincipal() instanceof User)) {
            throw new AccessDeniedException(
                    "You must be authenticated to edit organizations");
        }

        User currentUser = (User) authentication.getPrincipal();
        boolean isPlatformAdmin = authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_PLATFORM_ADMIN"));
        boolean isOrgAdmin = authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ORG_ADMIN"));

        // Check if user belongs to this organization
        boolean isOwnOrganization = false;
        if (currentUser.getOrganization() != null) {
            isOwnOrganization = currentUser.getOrganization().getId().equals(id);
        }

        if (!isPlatformAdmin && !(isOrgAdmin && isOwnOrganization)) {
            throw new AccessDeniedException(
                    "You do not have permission to edit this organization");
        }

        if (!existingOrg.getName().equals(organization.getName()) &&
            organizationService.existsByName(organization.getName())) {
            result.rejectValue("name", "error.organization", "Organization name already exists");
        }

        // Check if contact email is being changed to one that already exists in other organizations
        if (organization.getContactEmail() != null && !organization.getContactEmail().isEmpty() &&
            !organization.getContactEmail().equals(existingOrg.getContactEmail())) {

            // Check if new email exists in other organizations
            if (organizationService.existsByContactEmail(organization.getContactEmail())) {
                result.rejectValue("contactEmail", "error.organization",
                    "An organization with this email address already exists in the system");
            }

            // Check if new email exists in existing users
            if (userService.existsByEmail(organization.getContactEmail())) {
                result.rejectValue("contactEmail", "error.organization",
                    "A user with this email address already exists in the system");
            }

            // Check if new email exists in pending onboarding requests
            if (onboardingRequestService.existsByEmail(organization.getContactEmail())) {
                result.rejectValue("contactEmail", "error.organization",
                    "An onboarding request with this email address already exists");
            }
        }

        if (result.hasErrors()) {
            // Keep the submitted organization in the model (it's already there from @ModelAttribute)
            // This preserves the BindingResult association with validation errors
            // Set the ID so the form action URL is correct
            organization.setId(id);
            model.addAttribute("organizationTypes", OrganizationType.values());
            model.addAttribute("countries", COUNTRIES);
            model.addAttribute("countryPhonePrefixes", COUNTRY_PHONE_PREFIXES);
            model.addAttribute("selectedNaceCodes",
                    naceCodes != null ? naceCodeService.findByCodes(naceCodes) : List.of());
            model.addAttribute("title", "Edit Organization");
            model.addAttribute("fromSpip", false); // Editing existing org, not from SPIP
            return "organizations/form";
        }

        organization.setId(id);

        // Check if NACE codes changed and sync to CKAN before saving
        // Use eager fetch to avoid LazyInitializationException
        Set<String> existingNaceCodeStrings = organizationService.findByIdWithNaceCodes(id)
                .map(org -> org.getNaceCodes().stream().map(NaceCode::getCode).collect(Collectors.toSet()))
                .orElse(java.util.Collections.emptySet());
        Set<String> newNaceCodeStrings = naceCodes != null ? new HashSet<>(naceCodes) : java.util.Collections.emptySet();
        boolean naceCodesChanged = !existingNaceCodeStrings.equals(newNaceCodeStrings);
        if (naceCodesChanged) {
            Optional<OrganizationSpipUser> spipUserOpt = spipUserService.findByOrganization(existingOrg);
            if (spipUserOpt.isPresent()) {
                OrganizationSpipUser spipUser = spipUserOpt.get();
                if (Boolean.TRUE.equals(spipUser.getCkanSynchronized()) && spipUser.getCkanOrganizationName() != null) {
                    try {
                        ckanSyncService.syncNaceCodes(spipUser.getCkanOrganizationName(), organization.getNaceCodes());
                    } catch (Exception e) {
                        logger.error("NACE code sync to CKAN failed during organization update: {}", e.getMessage());
                        redirectAttributes.addFlashAttribute("error",
                                "Organization update failed: NACE code synchronization with CKAN failed. " +
                                "Please verify that CKAN is available and try again.");
                        return "redirect:/organizations/" + id + "/edit";
                    }
                }
            }
        }

        organizationService.update(organization);

        redirectAttributes.addFlashAttribute("message", "Organisation updated successfully.");
        return "redirect:/organizations/" + id;
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public String updateOrganizationStatus(@PathVariable Long id,
                                         @RequestParam String newStatus,
                                         RedirectAttributes redirectAttributes,
                                         @AuthenticationPrincipal User currentUser) {
        
        Organization organization = organizationService.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Organization", id));
        
        CertificationStatus oldStatus = organization.getCertificationStatus();
        CertificationStatus status;
        
        try {
            status = CertificationStatus.valueOf(newStatus);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", "Invalid status value");
            return "redirect:/organizations/" + id;
        }
        
        // Check if status is actually changing
        if (oldStatus == status) {
            redirectAttributes.addFlashAttribute("message", "Status unchanged");
            return "redirect:/organizations/" + id;
        }
        
        organization.setCertificationStatus(status);
        organizationService.update(organization);
        
        // Log the status change
        logStatusChange(organization, oldStatus, status, currentUser);
        
        redirectAttributes.addFlashAttribute("message", 
            String.format("Organization status changed from %s to %s", 
                         oldStatus.getDisplayName(), 
                         status.getDisplayName()));
        
        return "redirect:/organizations/" + id;
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public String deleteOrganization(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        organizationService.deleteById(id);
        redirectAttributes.addFlashAttribute("message", "Organization deleted successfully");
        return "redirect:/organizations";
    }
    
    private void logStatusChange(Organization org, CertificationStatus oldStatus,
                               CertificationStatus newStatus, User changedBy) {
        // Implementation for audit logging
        logger.info("AUDIT: Organization '{}' (ID: {}) status changed from {} to {} by user '{}' (ID: {}) at {}",
            org.getName(), org.getId(),
            oldStatus.getDisplayName(), newStatus.getDisplayName(),
            changedBy.getUsername(), changedBy.getId(),
            java.time.LocalDateTime.now()
        );
    }

    private CertificationStatus parseCertificationStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return Arrays.stream(CertificationStatus.values())
                .filter(s -> s.name().equalsIgnoreCase(status))
                .findFirst()
                .orElse(null);
    }

    private OrganizationType parseOrganizationType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        return Arrays.stream(OrganizationType.values())
                .filter(t -> t.name().equalsIgnoreCase(type))
                .findFirst()
                .orElse(null);
    }

    private IndustrySector parseIndustrySector(String sector) {
        if (sector == null || sector.isBlank()) {
            return null;
        }
        return Arrays.stream(IndustrySector.values())
                .filter(s -> s.name().equalsIgnoreCase(sector))
                .findFirst()
                .orElse(null);
    }

    /**
     * Map SPIP industrySector values to IndustrySector enum.
     * SPIP uses lowercase values like "automotive", "electronics", "plastics"
     *
     * @param spipIndustrySector Industry sector value from SPIP
     * @return Mapped IndustrySector enum value, or OTHERS if no match found
     */
    private IndustrySector mapIndustrySectorToEnum(String spipIndustrySector) {
        if (spipIndustrySector == null || spipIndustrySector.isEmpty()) {
            return IndustrySector.OTHERS;
        }

        // Convert to lowercase for case-insensitive matching
        String normalized = spipIndustrySector.toLowerCase().trim();

        return switch (normalized) {
            case "electronics", "weee", "electrical_and_electronic_equipment", "electrical", "electronic" ->
                    IndustrySector.ELECTRICAL_ELECTRONICS;
            case "catalytic", "catalytic_converters", "converters" ->
                    IndustrySector.CATALYTIC_CONVERTERS;
            case "plastics", "plastic", "agriculture", "plastic_agriculture" ->
                    IndustrySector.PLASTIC_AGRICULTURE;
            case "metals", "metals_and_materials", "metals_materials" ->
                    IndustrySector.METALS;
            case "textiles", "textile" ->
                    IndustrySector.TEXTILES;
            case "construction", "building" ->
                    IndustrySector.CONSTRUCTION;
            case "chemicals", "chemical" ->
                    IndustrySector.CHEMICALS;
            case "data", "data_analytics", "analytics" ->
                    IndustrySector.DATA_ANALYTICS;
            case "research", "research_institution" ->
                    IndustrySector.RESEARCH;
            default -> {
                logger.warn("Unknown industrySector value from SPIP: {}. Mapping to OTHERS.", spipIndustrySector);
                yield IndustrySector.OTHERS;
            }
        };
    }

    /**
     * Extract user ID from JWT token.
     * JWT format: header.payload.signature
     * Payload is Base64 encoded JSON containing uid field.
     *
     * @param token JWT token
     * @return User ID extracted from token
     * @throws RuntimeException if token format is invalid
     */
    private Integer extractUserIdFromToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new RuntimeException("Invalid JWT token format");
            }

            // Decode the payload (second part)
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            logger.debug("JWT payload: {}", payload);

            // Parse JSON to extract uid
            // Simple extraction without full JSON parsing library
            int uidIndex = payload.indexOf("\"uid\":");
            if (uidIndex == -1) {
                throw new RuntimeException("uid field not found in JWT payload");
            }

            // Find the value after "uid":
            int valueStart = uidIndex + 6; // length of "uid":
            int valueEnd = payload.indexOf(",", valueStart);
            if (valueEnd == -1) {
                valueEnd = payload.indexOf("}", valueStart);
            }

            String uidValue = payload.substring(valueStart, valueEnd).trim();
            return Integer.parseInt(uidValue);

        } catch (Exception e) {
            logger.error("Failed to extract user ID from token: {}", e.getMessage());
            throw new RuntimeException("Failed to extract user ID from token: " + e.getMessage(), e);
        }
    }

    /**
     * Create organization admin user with temporary password.
     * This creates a user account for the organization's contact person
     * so they can log in to the portal.
     *
     * @param organization The organization for which to create user
     * @return User entity with temporary password
     */
    /** Outcome of the best-effort Keycloak provisioning during direct org creation. */
    private record KeycloakDirectProvisioningResult(String userId, String username,
                                                    String tempPassword, String accountUrl) {
        static KeycloakDirectProvisioningResult none() {
            return new KeycloakDirectProvisioningResult(null, null, null, null);
        }
    }

    /**
     * Provision the organization in the default Keycloak instance (group named after
     * the SPIP username + first user from the contact email), mirroring what the
     * onboarding sync tool does for approved requests. Best-effort: any failure is
     * logged and the organization creation continues without a Keycloak identity.
     */
    private KeycloakDirectProvisioningResult provisionKeycloakForDirectCreation(
            Organization organization, OrganizationSpipUser spipUser, String spipUsername,
            String contactEmail, String contactName) {
        if (!toolRegistry.isEnabled(
                com.data4circ.portal.features.onboardingsync.keycloak.KeycloakOnboardingToolProvisioner.TOOL_KEY)) {
            return KeycloakDirectProvisioningResult.none();
        }
        if (contactEmail == null || contactEmail.isBlank()) {
            logger.info("Skipping Keycloak provisioning for '{}': no contact email for the first user.",
                    organization.getName());
            return KeycloakDirectProvisioningResult.none();
        }
        try {
            String clientSecret = keycloakSettingsService.getClientSecret();
            if (clientSecret == null || clientSecret.isBlank()) {
                logger.info("Skipping Keycloak provisioning for '{}': no admin client secret configured.",
                        organization.getName());
                return KeycloakDirectProvisioningResult.none();
            }

            com.data4circ.portal.integration.keycloak.KeycloakInstance instance =
                    new com.data4circ.portal.integration.keycloak.KeycloakInstance(
                            keycloakSettingsService.getBaseUrl(),
                            keycloakSettingsService.getRealm(),
                            keycloakSettingsService.getClientId(),
                            clientSecret);
            String keycloakUsername = contactEmail.trim().toLowerCase();
            String temporaryPassword = com.data4circ.portal.common.util.PasswordGenerator
                    .generate(16, "!@#$%^&*", true);

            var result = keycloakOnboardingService.initialize(instance, spipUsername,
                    keycloakUsername, contactEmail, contactName, temporaryPassword,
                    organization.getName());
            if (!result.isSuccess()) {
                logger.warn("Keycloak provisioning failed for '{}' (organization created without it): {}",
                        organization.getName(), result.getErrorMessage());
                return KeycloakDirectProvisioningResult.none();
            }

            String accountUrl = instance.getBaseUrl() + "/realms/" + instance.getRealm() + "/account";

            // Store the credentials on the SPIP-user record — for direct-created orgs this
            // is where the Technical Identifiers panel reads them from (no onboarding request).
            spipUser.setKeycloakUsername(keycloakUsername);
            spipUser.setKeycloakTempPassword(temporaryPassword);
            spipUser.setKeycloakAccountUrl(accountUrl);
            spipUserService.save(spipUser);

            // "My Tools" connector, same as the approval flow materializes.
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.node.ObjectNode configuration = objectMapper.createObjectNode();
            configuration.put("realm", instance.getRealm());
            configuration.put("group", spipUsername);
            configuration.put("username", keycloakUsername);
            toolConnectorService.materializeDirectToolConnector(organization,
                    com.data4circ.portal.features.onboardingsync.keycloak.KeycloakOnboardingToolProvisioner.TOOL_KEY,
                    null, instance.getBaseUrl(), configuration.toPrettyString());

            logger.info("Keycloak provisioned for directly created organization '{}' (user '{}', group '{}')",
                    organization.getName(), keycloakUsername, spipUsername);
            return new KeycloakDirectProvisioningResult(
                    result.getUserId(), keycloakUsername, temporaryPassword, accountUrl);
        } catch (Exception e) {
            logger.warn("Keycloak provisioning failed for '{}' (organization created without it): {}",
                    organization.getName(), e.getMessage());
            return KeycloakDirectProvisioningResult.none();
        }
    }

    private User createAdminUserForOrganization(Organization organization) {
        User adminUser = new User();
        adminUser.setUsername(organization.getContactEmail());
        adminUser.setEmail(organization.getContactEmail());
        adminUser.setFirstName(extractFirstName(organization.getPrimaryContactName()));
        adminUser.setLastName(extractLastName(organization.getPrimaryContactName()));
        adminUser.setRole(UserRole.ORG_ADMIN);
        adminUser.setOrganization(organization);
        adminUser.setEnabled(true);

        // Generate temporary password
        String temporaryPassword = generateTemporaryPassword();
        adminUser.setPassword(userService.encodePassword(temporaryPassword));
        adminUser.setMustChangePassword(true);

        // Store the temporary password in a transient field for email sending
        adminUser.setTemporaryPasswordForEmail(temporaryPassword);

        return adminUser;
    }

    /**
     * Generate a secure random temporary password.
     *
     * @return Generated temporary password
     */
    private String generateTemporaryPassword() {
        // 12 characters; specials exclude problematic chars: ()[]{}|;:,.<> and spaces
        return com.data4circ.portal.common.util.PasswordGenerator.generate(12, "!@#$%^&*_+-=?", false);
    }

    /**
     * Extract first name from full name.
     *
     * @param fullName Full contact name
     * @return First name
     */
    private String extractFirstName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return "";
        }
        int spaceIndex = fullName.indexOf(' ');
        return spaceIndex > 0 ? fullName.substring(0, spaceIndex).trim() : fullName.trim();
    }

    /**
     * Extract last name from full name.
     * If no space found (single word name), returns the same name to satisfy @NotBlank constraint.
     *
     * @param fullName Full contact name
     * @return Last name (or full name if no space)
     */
    private String extractLastName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return "Unknown";  // Default value to satisfy @NotBlank constraint
        }
        int spaceIndex = fullName.indexOf(' ');
        // If no space found, return the full name (single word name)
        return spaceIndex > 0 ? fullName.substring(spaceIndex + 1).trim() : fullName.trim();
    }

    // ==================== MEMBER MANAGEMENT ENDPOINTS ====================

    /**
     * Add a new member to an organization
     */
    @PostMapping("/{id}/members")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or (hasRole('ORG_ADMIN') and @organizationService.findById(#id).get().id == authentication.principal.organization.id)")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addMember(
            @PathVariable Long id,
            @Valid @RequestBody MemberDTO memberDTO,
            BindingResult bindingResult) {

        Map<String, Object> response = new HashMap<>();

        if (bindingResult.hasErrors()) {
            response.put("success", false);
            response.put("error", "Validation failed: " + bindingResult.getAllErrors().get(0).getDefaultMessage());
            return ResponseEntity.badRequest().body(response);
        }

        try {
            Organization organization = organizationService.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Organization", id));

            // Check if user with this email already exists
            if (userService.existsByEmail(memberDTO.getEmail())) {
                response.put("success", false);
                response.put("error", "A user with this email already exists");
                return ResponseEntity.badRequest().body(response);
            }

            UserRole role = memberDTO.getRole() != null ? memberDTO.getRole() : UserRole.ORG_MEMBER;
            if (!ASSIGNABLE_MEMBER_ROLES.contains(role)) {
                response.put("success", false);
                response.put("error", "Only Organization Admin and Organization Member roles can be assigned");
                return ResponseEntity.badRequest().body(response);
            }

            // Create new user
            User newMember = new User();
            newMember.setUsername(memberDTO.getEmail());
            newMember.setEmail(memberDTO.getEmail());
            newMember.setFirstName(memberDTO.getFirstName());
            newMember.setLastName(memberDTO.getLastName());
            newMember.setPhone(memberDTO.getPhone());
            newMember.setJobTitle(memberDTO.getJobTitle());
            newMember.setRole(role);
            newMember.setOrganization(organization);
            newMember.setEnabled(true);

            // Generate temporary password
            String temporaryPassword = generateTemporaryPassword();
            newMember.setPassword(userService.encodePassword(temporaryPassword));
            newMember.setMustChangePassword(true);

            // Save user
            User savedMember = userService.saveWithoutEncoding(newMember);
            logger.info("New member added to organization {}: {} ({}) with role {}",
                    organization.getName(), savedMember.getFullName(), savedMember.getEmail(), savedMember.getRole());

            // Send invitation email
            try {
                emailService.sendMemberInvitation(
                        savedMember.getEmail(),
                        organization.getName(),
                        savedMember.getFullName(),
                        savedMember.getUsername(),
                        temporaryPassword
                );
                logger.info("Member invitation email sent to {}", savedMember.getEmail());
            } catch (Exception e) {
                logger.error("Failed to send invitation email: {}", e.getMessage(), e);
                // Don't fail the operation if email fails
            }

            response.put("success", true);
            response.put("message", "Member added successfully");
            response.put("memberId", savedMember.getId());
            return ResponseEntity.ok(response);

        } catch (EntityNotFoundException e) {
            response.put("success", false);
            response.put("error", "Organization not found");
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error adding member: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", "Failed to add member: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Update an existing member
     */
    @PutMapping("/{id}/members/{memberId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or (hasRole('ORG_ADMIN') and @organizationService.findById(#id).get().id == authentication.principal.organization.id)")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateMember(
            @PathVariable Long id,
            @PathVariable Long memberId,
            @Valid @RequestBody MemberDTO memberDTO,
            BindingResult bindingResult) {

        Map<String, Object> response = new HashMap<>();

        if (bindingResult.hasErrors()) {
            response.put("success", false);
            response.put("error", "Validation failed: " + bindingResult.getAllErrors().get(0).getDefaultMessage());
            return ResponseEntity.badRequest().body(response);
        }

        try {
            // Members are fetched eagerly for the last-admin check on role changes
            Organization organization = organizationService.findByIdWithMembers(id)
                    .orElseThrow(() -> new EntityNotFoundException("Organization", id));

            User member = userService.findById(memberId)
                    .orElseThrow(() -> new EntityNotFoundException("User", memberId));

            // Verify member belongs to this organization
            if (member.getOrganization() == null || !member.getOrganization().getId().equals(id)) {
                response.put("success", false);
                response.put("error", "Member does not belong to this organization");
                return ResponseEntity.badRequest().body(response);
            }

            // Check if email is being changed to one that already exists
            if (!member.getEmail().equals(memberDTO.getEmail()) && 
                userService.existsByEmail(memberDTO.getEmail())) {
                response.put("success", false);
                response.put("error", "A user with this email already exists");
                return ResponseEntity.badRequest().body(response);
            }

            // Role change (optional): promote to admin or demote back to member
            UserRole newRole = memberDTO.getRole();
            if (newRole != null && newRole != member.getRole()) {
                // Platform-level and SPIP roles cannot be assigned or taken away here
                if (!ASSIGNABLE_MEMBER_ROLES.contains(newRole) || !ASSIGNABLE_MEMBER_ROLES.contains(member.getRole())) {
                    response.put("success", false);
                    response.put("error", "Only Organization Admin and Organization Member roles can be changed");
                    return ResponseEntity.badRequest().body(response);
                }

                // An organization must always keep at least one active Organization Admin
                if (member.getRole() == UserRole.ORG_ADMIN && !hasAnotherActiveAdmin(organization, member)) {
                    response.put("success", false);
                    response.put("error", "You cannot change the role of the only Organization Admin. "
                            + "Add another admin before changing this member's role.");
                    return ResponseEntity.badRequest().body(response);
                }

                logger.info("Member role changed in organization {}: {} ({}) from {} to {}",
                        organization.getName(), member.getFullName(), member.getEmail(),
                        member.getRole(), newRole);
                member.setRole(newRole);
            }

            // Update member details
            member.setFirstName(memberDTO.getFirstName());
            member.setLastName(memberDTO.getLastName());
            member.setEmail(memberDTO.getEmail());
            member.setUsername(memberDTO.getEmail()); // Username follows email
            member.setPhone(memberDTO.getPhone());
            member.setJobTitle(memberDTO.getJobTitle());

            User updatedMember = userService.update(member);
            logger.info("Member updated in organization {}: {} ({})",
                    organization.getName(), updatedMember.getFullName(), updatedMember.getEmail());

            response.put("success", true);
            response.put("message", "Member updated successfully");
            return ResponseEntity.ok(response);

        } catch (EntityNotFoundException e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error updating member: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", "Failed to update member: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Delete a member from an organization
     */
    @DeleteMapping("/{id}/members/{memberId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or (hasRole('ORG_ADMIN') and @organizationService.findById(#id).get().id == authentication.principal.organization.id)")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteMember(
            @PathVariable Long id,
            @PathVariable Long memberId,
            @AuthenticationPrincipal User currentUser) {

        Map<String, Object> response = new HashMap<>();

        // Principal could not be resolved to a User (see viewMyOrganization) — reject rather
        // than throwing a 500 NPE on the self-deletion check below.
        if (currentUser == null) {
            response.put("success", false);
            response.put("error", "Authentication required");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        try {
            // Members are fetched eagerly for the last-admin check below
            Organization organization = organizationService.findByIdWithMembers(id)
                    .orElseThrow(() -> new EntityNotFoundException("Organization", id));

            User member = userService.findById(memberId)
                    .orElseThrow(() -> new EntityNotFoundException("User", memberId));

            // Verify member belongs to this organization
            if (member.getOrganization() == null || !member.getOrganization().getId().equals(id)) {
                response.put("success", false);
                response.put("error", "Member does not belong to this organization");
                return ResponseEntity.badRequest().body(response);
            }

            // Prevent deleting yourself
            if (member.getId().equals(currentUser.getId())) {
                response.put("success", false);
                response.put("error", "You cannot delete your own account");
                return ResponseEntity.badRequest().body(response);
            }

            // An organization must always keep at least one active Organization Admin:
            // an admin can only be removed if another active admin remains
            if (member.getRole() == UserRole.ORG_ADMIN && !hasAnotherActiveAdmin(organization, member)) {
                response.put("success", false);
                response.put("error", "You cannot remove the only Organization Admin. "
                        + "Add another admin before removing this member.");
                return ResponseEntity.badRequest().body(response);
            }

            String memberName = member.getFullName();
            String memberEmail = member.getEmail();
            userService.deleteById(memberId);
            logger.info("Member deleted from organization {}: {} ({})", 
                    organization.getName(), memberName, memberEmail);

            response.put("success", true);
            response.put("message", "Member deleted successfully");
            return ResponseEntity.ok(response);

        } catch (EntityNotFoundException e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error deleting member: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", "Failed to delete member: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Check whether the organization has at least one active Organization Admin
     * other than the given member.
     */
    private boolean hasAnotherActiveAdmin(Organization organization, User member) {
        return organization.getMembers().stream()
                .filter(u -> !u.getId().equals(member.getId()))
                .anyMatch(u -> u.getRole() == UserRole.ORG_ADMIN && u.isEnabled());
    }

    /**
     * Reset password for a member
     */
    @PostMapping("/{id}/members/{memberId}/reset-password")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or (hasRole('ORG_ADMIN') and @organizationService.findById(#id).get().id == authentication.principal.organization.id)")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> resetMemberPassword(
            @PathVariable Long id,
            @PathVariable Long memberId) {

        Map<String, Object> response = new HashMap<>();

        try {
            Organization organization = organizationService.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Organization", id));

            User member = userService.findById(memberId)
                    .orElseThrow(() -> new EntityNotFoundException("User", memberId));

            // Verify member belongs to this organization
            if (member.getOrganization() == null || !member.getOrganization().getId().equals(id)) {
                response.put("success", false);
                response.put("error", "Member does not belong to this organization");
                return ResponseEntity.badRequest().body(response);
            }

            // Generate new temporary password
            String newPassword = generateTemporaryPassword();
            member.setPassword(userService.encodePassword(newPassword));
            member.setMustChangePassword(true);
            userService.update(member);
            
            logger.info("Password reset for member in organization {}: {} ({})", 
                    organization.getName(), member.getFullName(), member.getEmail());

            // Send password reset email
            try {
                emailService.sendPasswordReset(
                        member.getEmail(),
                        organization.getName(),
                        member.getFullName(),
                        member.getUsername(),
                        newPassword
                );
                logger.info("Password reset email sent to {}", member.getEmail());
            } catch (Exception e) {
                logger.error("Failed to send password reset email: {}", e.getMessage(), e);
                // Don't fail the operation if email fails
            }

            response.put("success", true);
            response.put("message", "Password reset successfully. Email sent to member.");
            return ResponseEntity.ok(response);

        } catch (EntityNotFoundException e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error resetting password: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", "Failed to reset password: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
