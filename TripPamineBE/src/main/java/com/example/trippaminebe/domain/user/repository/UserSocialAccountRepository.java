package com.example.trippaminebe.domain.user.repository;

import com.example.trippaminebe.domain.user.entity.UserSocialAccount;
import com.example.trippaminebe.domain.user.entity.UserSocialProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserSocialAccountRepository extends JpaRepository<UserSocialAccount, Long> {

  // join fetch usa.user 를 통해 User 엔티티까지 한 번에(EAGER) 로딩해옵니다.
  @Query("select usa from UserSocialAccount usa join fetch usa.user where usa.oauthProvider = :provider and usa.providerUserId = :providerUserId")
  Optional<UserSocialAccount> findByOauthProviderAndProviderUserId(
      @Param("provider") UserSocialProvider provider,
      @Param("providerUserId") String providerUserId

  );

}
