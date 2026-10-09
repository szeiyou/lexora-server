package com.songzy.dicserver.repository;

import com.songzy.dicserver.model.AppUser;
import com.songzy.dicserver.model.UserStatus;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class AppUserRepositoryTest {

    @Autowired
    private AppUserRepository appUserRepository;

    @Test
    void saveAndFlushShouldAllowCaseDistinctUsernames() {
        appUserRepository.saveAndFlush(user("alice"));
        appUserRepository.saveAndFlush(user("Alice"));

        assertThat(appUserRepository.findByUsernameHash(AppUser.hashUsername("alice")))
                .map(AppUser::getUsername)
                .contains("alice");
        assertThat(appUserRepository.findByUsernameHash(AppUser.hashUsername("Alice")))
                .map(AppUser::getUsername)
                .contains("Alice");
    }

    @Test
    void saveAndFlushShouldRejectExactDuplicateUsername() {
        appUserRepository.saveAndFlush(user("alice"));

        assertThatThrownBy(() -> appUserRepository.saveAndFlush(user("alice")))
                .isInstanceOfAny(DataIntegrityViolationException.class, PersistenceException.class);
    }

    @Test
    void existsByUsernameHashShouldRequireExactUsernameHash() {
        appUserRepository.saveAndFlush(user("alice"));

        assertThat(appUserRepository.existsByUsernameHash(AppUser.hashUsername("alice"))).isTrue();
        assertThat(appUserRepository.existsByUsernameHash(AppUser.hashUsername("Alice"))).isFalse();
    }

    private static AppUser user(String username) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash("encoded-password");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
