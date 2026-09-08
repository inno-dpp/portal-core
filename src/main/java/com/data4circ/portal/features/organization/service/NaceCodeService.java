package com.data4circ.portal.features.organization.service;

import com.data4circ.portal.features.organization.entity.NaceCode;
import com.data4circ.portal.features.organization.repository.NaceCodeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class NaceCodeService {

    @Autowired
    private NaceCodeRepository naceCodeRepository;

    public List<NaceCode> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        // Only expose 4-digit class codes to selection UIs; 2/3-digit codes denote broader
        // families and must not be selectable per the onboarding review feedback.
        return naceCodeRepository.search(query.trim()).stream()
                .filter(n -> NaceCode.isClassCode(n.getCode()))
                .collect(Collectors.toList());
    }

    public List<NaceCode> findAll() {
        return naceCodeRepository.findAllByOrderByCodeAsc();
    }

    public Optional<NaceCode> findByCode(String code) {
        return naceCodeRepository.findById(code);
    }

    public List<NaceCode> findByCodes(List<String> codes) {
        return naceCodeRepository.findByCodeIn(codes);
    }

    public long count() {
        return naceCodeRepository.count();
    }

    public List<NaceCode> findCodesInUse() {
        return naceCodeRepository.findCodesInUse();
    }
}
