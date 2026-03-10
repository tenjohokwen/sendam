package com.softropic.sendam.security.repo;



import com.softropic.sendam.security.contract.LoginData;
import com.softropic.sendam.security.repo.LoginInfo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA persistence for the Login entity.
 */
public interface LoginInfoRepository extends JpaRepository<LoginInfo, Long> {

    Optional<LoginData> findOneById(Long id);
}
