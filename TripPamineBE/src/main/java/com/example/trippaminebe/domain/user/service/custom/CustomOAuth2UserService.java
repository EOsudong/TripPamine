package com.example.trippaminebe.domain.user.service.custom;

import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.entity.UserSocialAccount;
import com.example.trippaminebe.domain.user.repository.UserRepository;
import com.example.trippaminebe.domain.user.repository.UserSocialAccountRepository;
import com.example.trippaminebe.security.oauth2.OAuth2Attributes;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
  private final UserRepository userRepository;
  private final UserSocialAccountRepository userSocialAccountRepository;
  private final PasswordEncoder passwordEncoder;

  @Override
  @Transactional
  public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
    OAuth2User oAuth2User = super.loadUser(userRequest);

    String registrationId = userRequest.getClientRegistration().getRegistrationId();
    String userNameAttributeName = userRequest.getClientRegistration().getProviderDetails()
        .getUserInfoEndpoint().getUserNameAttributeName();
    OAuth2Attributes attributes = OAuth2Attributes
        .oAuth2Attribute(
            registrationId,
            userNameAttributeName,
            oAuth2User.getAttributes());

    User user = saveOrUpdate(attributes);

    return new CustomUserDetails(user, oAuth2User.getAttributes());
  }

  private User saveOrUpdate(OAuth2Attributes attributes) {
    // 이미 연동된 소셜 계정이 있는지 확인
    return userSocialAccountRepository.findByOauthProviderAndProviderUserId(
            attributes.getProvider(),
            attributes.getProviderUserId())
        .map(UserSocialAccount::getUser)
        .orElseGet(() -> {
          // 이메일로 기존 회원 검색 또는 신규 가입 처리
          String email = attributes.getEmail() != null
              ? attributes.getEmail()
              : attributes.getProviderUserId() + "@" + attributes.getProvider();
          User user = userRepository.findByEmail(email)
              .orElseGet(() -> userRepository.save((User.builder()
                  .email(email)
                  .name(attributes.getNickname() != null
                      ? attributes.getNickname()
                      : "소셜회원"))
                  .userName(attributes.getNickname() != null
                      ? attributes.getNickname() + "_" + UUID.randomUUID().toString().substring(0, 4)
                      : "social_" + UUID.randomUUID().toString().substring(0, 8))
                  .password(passwordEncoder.encode(UUID.randomUUID().toString()))
//                  .createDate(LocalDateTime.now(ZoneId.of("Asia/Seoul")))
                  .build()));

          // 소셜 연동 정보 저장
          UserSocialAccount socialAccount = UserSocialAccount.builder()
              .user(user)
              .oauthProvider(attributes.getProvider())
              .providerUserId(attributes.getProviderUserId())
              .build();
          userSocialAccountRepository.save(socialAccount);

          return user;
        });
  }


  @Override
  public void setAttributesConverter(Converter<OAuth2UserRequest, Converter<Map<String, Object>, Map<String, Object>>> attributesConverter) {
    super.setAttributesConverter(attributesConverter);
  }


}//class
