package com.data4circ.portal.features.organization.repository;

import com.data4circ.portal.features.organization.entity.NaceCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NaceCodeRepository extends JpaRepository<NaceCode, String> {

    @Query("SELECT n FROM NaceCode n WHERE LOWER(n.code) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(n.description) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "ORDER BY n.code")
    List<NaceCode> search(String search);

    List<NaceCode> findBySection(String section);

    List<NaceCode> findAllByOrderByCodeAsc();

    List<NaceCode> findByCodeIn(List<String> codes);

    @Query("SELECT n FROM NaceCode n WHERE n.code IN (SELECT nc.code FROM Organization o JOIN o.naceCodes nc) ORDER BY n.code")
    List<NaceCode> findCodesInUse();
}
