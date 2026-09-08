package com.data4circ.portal.features.platformsettings.service;

import com.data4circ.portal.features.platformsettings.entity.PlatformSetting;
import com.data4circ.portal.features.platformsettings.repository.PlatformSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Generic key/value store for platform-wide settings edited by platform admins.
 * Values are encrypted at rest via the entity's {@code EncryptedStringConverter}.
 */
@Service
@Transactional
public class PlatformSettingService {

    private final PlatformSettingRepository repository;

    public PlatformSettingService(PlatformSettingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<String> getValue(String key) {
        return repository.findBySettingKey(key)
                .map(PlatformSetting::getSettingValue)
                .filter(value -> value != null && !value.isBlank());
    }

    public void setValue(String key, String value, String updatedBy) {
        PlatformSetting setting = repository.findBySettingKey(key)
                .orElseGet(() -> new PlatformSetting(key, null));
        setting.setSettingValue(value);
        setting.setUpdatedBy(updatedBy);
        repository.save(setting);
    }

    public void clearValue(String key) {
        repository.deleteBySettingKey(key);
    }
}
