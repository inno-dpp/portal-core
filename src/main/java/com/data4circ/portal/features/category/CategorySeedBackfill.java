package com.data4circ.portal.features.category;

import com.data4circ.portal.features.category.entity.Category;
import com.data4circ.portal.features.category.enums.CategoryStyle;
import com.data4circ.portal.features.category.repository.CategoryRepository;
import com.data4circ.portal.integration.ckan.client.CkanApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-time, idempotent seed of the four categories this app used to ship as static
 * {@code branding.yml} dashboard cards, now that categories are portal-DB rows synced to CKAN
 * (see docs/developer/ckan/CKAN-GROUP-MEMBERSHIP-FOR-ONBOARDED-USERS.md). Runs on every startup;
 * a category already present (by slug) is left untouched.
 *
 * <p>Unlike {@code OnboardingToolSyncBackfill} (pure local-DB work, safely wrapped in one
 * {@code @Transactional}), this runner also calls CKAN's {@code group_create} — a network call
 * at the exact moment the portal and CKAN often start together (e.g. {@code docker-compose up}),
 * so CKAN being transiently unreachable here is an expected scenario, not an edge case. The DB
 * write and the CKAN sync are therefore two separate, independent try/catches per seed: the DB
 * row always persists (that's what the dashboard needs to render), and a failed CKAN sync only
 * logs a warning — it never aborts this runner or app startup. The next time an admin edits/saves
 * that category from {@code /admin/categories} (or creates any new one), {@code CategoryService}
 * retries the sync for free, so no separate scheduled retry job exists here.</p>
 */
@Component
public class CategorySeedBackfill implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(CategorySeedBackfill.class);

    private record SeedCategory(String slug, String title, String description, String icon, CategoryStyle style) {
    }

    private static final List<SeedCategory> SEED_CATEGORIES = List.of(
            new SeedCategory("catalytic-converters", "Catalytic Converters",
                    "Recycling and recovery data for automotive catalytic converters.",
                    "fas fa-car", CategoryStyle.PRIMARY),
            new SeedCategory("electrical-and-electronic-equipment", "Electronic Equipment",
                    "E-waste management and electronic component recovery.",
                    "fas fa-microchip", CategoryStyle.SUCCESS),
            new SeedCategory("plastic-agriculture", "Agricultural Plastic",
                    "Agricultural plastic waste and recycling solutions.",
                    "fas fa-seedling", CategoryStyle.WARNING),
            new SeedCategory("others", "Other Categories",
                    "Testing data and uncategorized circular economy datasets.",
                    "fas fa-ellipsis-h", CategoryStyle.INFO)
    );

    private final CategoryRepository categoryRepository;
    private final CkanApiClient ckanApiClient;

    public CategorySeedBackfill(CategoryRepository categoryRepository, CkanApiClient ckanApiClient) {
        this.categoryRepository = categoryRepository;
        this.ckanApiClient = ckanApiClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        int seeded = 0;
        for (SeedCategory seed : SEED_CATEGORIES) {
            if (categoryRepository.existsBySlug(seed.slug())) {
                continue;
            }

            Category category = new Category(seed.slug(), seed.title(), seed.description(), seed.icon(), seed.style());
            try {
                categoryRepository.save(category);
                seeded++;
            } catch (Exception e) {
                logger.error("Failed to seed category '{}' in portal DB: {}", seed.slug(), e.getMessage(), e);
                continue;
            }

            try {
                ckanApiClient.createGroup(seed.slug(), seed.title(), seed.description());
            } catch (Exception e) {
                logger.warn("Seeded category '{}' in portal DB but CKAN group sync failed (CKAN may be "
                        + "unavailable at startup) - it will retry the next time an admin edits/saves this "
                        + "category from /admin/categories: {}", seed.slug(), e.getMessage());
            }
        }
        if (seeded > 0) {
            logger.info("Seeded {} default categor{} into the portal DB", seeded, seeded == 1 ? "y" : "ies");
        }
    }
}
