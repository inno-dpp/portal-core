package com.data4circ.portal.features.organization.controller;

import com.data4circ.portal.features.category.dto.CategoryCardView;
import com.data4circ.portal.features.category.entity.Category;
import com.data4circ.portal.features.category.service.CategoryService;
import com.data4circ.portal.features.dataset.service.DatasetStatisticsService;
import com.data4circ.portal.features.organization.entity.Organization;
import com.data4circ.portal.features.connectors.service.ConnectorService;
import com.data4circ.portal.features.organization.service.OrganizationService;
import com.data4circ.portal.features.platformsettings.service.CkanSettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class DashboardController {

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private ConnectorService connectorService;

    @Autowired
    private DatasetStatisticsService ckanStatisticsService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CkanSettingsService ckanSettingsService;

    @GetMapping("/")
    public String dashboard(Model model) {
        // Quick statistics
        model.addAttribute("organizationCount", organizationService.getTotalCount());
        model.addAttribute("connectorCount", connectorService.getOnlineCount());

        model.addAttribute("datasetCount", toDisplay(ckanStatisticsService.getDatasetCount()));

        model.addAttribute("recentOffersCount", 0); // Placeholder for future implementation

        // Recent organizations (latest 5 onboarded, ordered by creation date)
        List<Organization> recentOrganizations = organizationService.findRecentOrganizations(5);
        model.addAttribute("recentOrganizations", recentOrganizations);

        // Use-case category cards, from the portal-managed category registry (/admin/categories);
        // section is hidden when empty.
        model.addAttribute("categories", buildCategoryCards());

        model.addAttribute("title", "Dashboard");
        return "dashboard";
    }

    /** Active categories paired with their resolved CKAN "Explore Data" link. */
    private List<CategoryCardView> buildCategoryCards() {
        String publicUrl = ckanSettingsService.getPublicUrl();
        return categoryService.findAllActive().stream()
                .map(category -> toCardView(category, publicUrl))
                .toList();
    }

    private static CategoryCardView toCardView(Category category, String publicUrl) {
        String exploreUrl = publicUrl + "/group/" + category.getSlug();
        return new CategoryCardView(category.getTitle(), category.getDescription(),
                category.getIcon(), category.getStyle().getCssSuffix(), exploreUrl);
    }

    @GetMapping("/dashboard")
    public String dashboardRedirect() {
        return "redirect:/";
    }

    /** Render a nullable count as either its integer string or "N/A". */
    private static Object toDisplay(Integer count) {
        return count != null ? count : "N/A";
    }
}