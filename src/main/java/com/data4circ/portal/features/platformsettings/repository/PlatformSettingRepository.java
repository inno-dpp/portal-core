package com.data4circ.portal.features.platformsettings.repository;

import com.data4circ.portal.features.platformsettings.entity.PlatformSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlatformSettingRepository extends JpaRepository<PlatformSetting, Long> {

    Optional<PlatformSetting> findBySettingKey(String settingKey);

    void deleteBySettingKey(String settingKey);
}
