package com.data4circ.portal.features.category.controller;

import com.data4circ.portal.common.exception.EntityNotFoundException;
import com.data4circ.portal.features.category.dto.CategoryFormDTO;
import com.data4circ.portal.features.category.dto.CategorySaveResult;
import com.data4circ.portal.features.category.entity.Category;
import com.data4circ.portal.features.category.enums.CategoryStyle;
import com.data4circ.portal.features.category.service.CategoryService;
import com.data4circ.portal.features.onboardingsync.ckan.CkanOnboardingSyncService;
import com.data4circ.portal.features.onboardingsync.ckan.GroupMembershipBackfillResult;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Platform-admin CRUD for CKAN categories (CKAN groups). See
 * docs/developer/ckan/CKAN-GROUP-MEMBERSHIP-FOR-ONBOARDED-USERS.md for background on why the
 * portal, not CKAN, is authoritative for this registry.
 *
 * <p>{@code /admin/**} is already {@code hasRole('PLATFORM_ADMIN')}-gated and CSRF-protected in
 * {@code SecurityConfig}; the class-level {@code @PreAuthorize} here is defense-in-depth, matching
 * {@code PlatformSettingsController}'s exact pattern.</p>
 */
@Controller
@RequestMapping("/admin/categories")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class CategoryController {

    private final CategoryService categoryService;
    private final CkanOnboardingSyncService ckanOnboardingSyncService;

    public CategoryController(CategoryService categoryService, CkanOnboardingSyncService ckanOnboardingSyncService) {
        this.categoryService = categoryService;
        this.ckanOnboardingSyncService = ckanOnboardingSyncService;
    }

    @GetMapping
    public String listCategories(Model model) {
        List<Category> categories = categoryService.findAll();
        model.addAttribute("categories", categories);
        model.addAttribute("title", "CKAN Categories");
        return "admin/categories/list";
    }

    @GetMapping("/new")
    public String newCategoryForm(Model model) {
        model.addAttribute("categoryForm", new CategoryFormDTO());
        model.addAttribute("categoryId", null);
        model.addAttribute("styleOptions", CategoryStyle.values());
        model.addAttribute("title", "New CKAN Category");
        return "admin/categories/form";
    }

    @PostMapping("/new")
    public String createCategory(@Valid @ModelAttribute("categoryForm") CategoryFormDTO form,
                                  BindingResult result,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        if (!result.hasErrors()) {
            try {
                CategorySaveResult saveResult = categoryService.create(form);
                flashSaveOutcome(redirectAttributes, saveResult, "created");
                return "redirect:/admin/categories";
            } catch (IllegalArgumentException e) {
                result.rejectValue("slug", "slug.duplicate", e.getMessage());
            }
        }
        model.addAttribute("categoryId", null);
        model.addAttribute("styleOptions", CategoryStyle.values());
        model.addAttribute("title", "New CKAN Category");
        return "admin/categories/form";
    }

    @GetMapping("/{id}/edit")
    public String editCategoryForm(@PathVariable Long id, Model model) {
        Category category = categoryService.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Category", id));

        CategoryFormDTO form = new CategoryFormDTO();
        form.setTitle(category.getTitle());
        form.setDescription(category.getDescription());
        form.setIcon(category.getIcon());
        form.setStyle(category.getStyle());

        model.addAttribute("categoryForm", form);
        model.addAttribute("categoryId", id);
        model.addAttribute("categorySlug", category.getSlug());
        model.addAttribute("styleOptions", CategoryStyle.values());
        model.addAttribute("title", "Edit CKAN Category");
        return "admin/categories/form";
    }

    @PostMapping("/{id}/edit")
    public String updateCategory(@PathVariable Long id,
                                  @Valid @ModelAttribute("categoryForm") CategoryFormDTO form,
                                  BindingResult result,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            Category category = categoryService.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Category", id));
            model.addAttribute("categoryId", id);
            model.addAttribute("categorySlug", category.getSlug());
            model.addAttribute("styleOptions", CategoryStyle.values());
            model.addAttribute("title", "Edit CKAN Category");
            return "admin/categories/form";
        }

        CategorySaveResult saveResult = categoryService.update(id, form);
        flashSaveOutcome(redirectAttributes, saveResult, "updated");
        return "redirect:/admin/categories";
    }

    @PostMapping("/{id}/deactivate")
    public String deactivateCategory(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        CategorySaveResult saveResult = categoryService.deactivate(id);
        flashSaveOutcome(redirectAttributes, saveResult, "deactivated");
        return "redirect:/admin/categories";
    }

    @PostMapping("/{id}/reactivate")
    public String reactivateCategory(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        CategorySaveResult saveResult = categoryService.reactivate(id);
        flashSaveOutcome(redirectAttributes, saveResult, "reactivated");
        return "redirect:/admin/categories";
    }

    /**
     * One-off, re-runnable action: grants every active category to every organization that
     * already has a working CKAN account, for organizations onboarded before this feature
     * existed (new onboardings, and newly-created categories, are both granted automatically
     * elsewhere — see {@link CkanOnboardingSyncService#addUserToUseCaseGroupsIdempotent} and
     * {@code CategoryService#create}). No UI button calls this anymore — it's driven by
     * {@code resync-ckan-categories.sh} (repo root), see
     * docs/developer/ckan/RESYNC-EXISTING-ORGANIZATIONS.md. Kept as a normal authenticated admin
     * endpoint (not a script) so the script has something to drive via a plain HTTP POST, same
     * auth/CSRF handling as every other admin action here.
     */
    @PostMapping("/resync-organizations")
    public String resyncOrganizations(RedirectAttributes redirectAttributes) {
        List<GroupMembershipBackfillResult> results = ckanOnboardingSyncService.backfillGroupMembershipForAllOrganizations();

        if (results.isEmpty()) {
            redirectAttributes.addFlashAttribute("message",
                    "No organizations with a CKAN account found to resync.");
            return "redirect:/admin/categories";
        }

        long errorCount = results.stream().filter(r -> !r.isSuccess()).count();
        String message = "Resynced " + results.size() + " organization(s) against "
                + "the current categories.";
        if (errorCount > 0) {
            message += " " + errorCount + " had an error — check server logs for details.";
        }
        redirectAttributes.addFlashAttribute(errorCount > 0 ? "error" : "message", message);
        return "redirect:/admin/categories";
    }

    private void flashSaveOutcome(RedirectAttributes redirectAttributes, CategorySaveResult saveResult, String verb) {
        if (saveResult.hasCkanWarning()) {
            redirectAttributes.addFlashAttribute("error",
                    "CKAN category " + verb + ", but CKAN sync failed: " + saveResult.getCkanWarning()
                            + " — edit and save again to retry.");
            return;
        }

        StringBuilder message = new StringBuilder("CKAN category " + verb + " successfully");
        List<GroupMembershipBackfillResult> grants = saveResult.getExistingOrganizationsGranted();
        if (!grants.isEmpty()) {
            long errorCount = grants.stream().filter(r -> !r.isSuccess()).count();
            message.append(". Granted to ").append(grants.size()).append(" existing organization(s)");
            if (errorCount > 0) {
                message.append(" (").append(errorCount).append(" had an error — check server logs)");
            }
            message.append('.');
        }
        redirectAttributes.addFlashAttribute("message", message.toString());
    }
}
