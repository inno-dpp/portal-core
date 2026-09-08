package com.data4circ.portal.features.organization.service;

import com.data4circ.portal.features.organization.entity.NaceCode;
import com.data4circ.portal.features.organization.entity.Organization;
import com.data4circ.portal.features.organization.repository.NaceCodeRepository;
import com.data4circ.portal.features.organization.repository.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class NaceCodeDataLoader implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(NaceCodeDataLoader.class);
    private static final String DEFAULT_NACE_CODE = "96.99";

    @Autowired
    private NaceCodeRepository naceCodeRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (naceCodeRepository.count() > 0) {
            logger.info("NACE codes already loaded ({} entries), skipping import", naceCodeRepository.count());
            assignDefaultNaceCodeToOrganizationsWithout();
            return;
        }

        logger.info("Loading NACE Rev. 2.1 codes from SQL file...");
        try {
            ClassPathResource resource = new ClassPathResource("db/nace_codes_data.sql");
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));

            List<NaceCode> codes = new ArrayList<>();
            Pattern pattern = Pattern.compile(
                    "\\('([^']*)',\\s*'([^']*(?:''[^']*)*)',\\s*'([^']*)',\\s*'([^']*(?:''[^']*)*)'\\)");

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("--") || line.startsWith("INSERT") || line.isEmpty()) {
                    continue;
                }
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    String code = matcher.group(1);
                    String description = matcher.group(2).replace("''", "'");
                    String section = matcher.group(3);
                    String sectionLabel = matcher.group(4).replace("''", "'");
                    codes.add(new NaceCode(code, description, section, sectionLabel));
                }
            }
            reader.close();

            naceCodeRepository.saveAll(codes);
            logger.info("Successfully loaded {} NACE codes", codes.size());

            assignDefaultNaceCodeToOrganizationsWithout();

        } catch (Exception e) {
            throw new RuntimeException("Failed to load NACE codes — application cannot start without NACE data", e);
        }
    }

    private void assignDefaultNaceCodeToOrganizationsWithout() {
        NaceCode defaultCode = naceCodeRepository.findById(DEFAULT_NACE_CODE).orElse(null);
        if (defaultCode == null) {
            logger.warn("Default NACE code {} not found, cannot assign to existing organizations", DEFAULT_NACE_CODE);
            return;
        }

        List<Organization> allOrgs = organizationRepository.findAll();
        int updated = 0;
        for (Organization org : allOrgs) {
            if (org.getNaceCodes() == null || org.getNaceCodes().isEmpty()) {
                org.getNaceCodes().add(defaultCode);
                organizationRepository.save(org);
                updated++;
            }
        }
        if (updated > 0) {
            logger.info("Assigned default NACE code {} to {} existing organizations", DEFAULT_NACE_CODE, updated);
        }
    }
}
