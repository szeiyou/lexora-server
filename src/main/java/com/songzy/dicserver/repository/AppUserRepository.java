package com.songzy.dicserver.repository;

import com.songzy.dicserver.model.AppUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    boolean existsByUsernameHash(String usernameHash);

    Optional<AppUser> findByUsernameHash(String usernameHash);
}
