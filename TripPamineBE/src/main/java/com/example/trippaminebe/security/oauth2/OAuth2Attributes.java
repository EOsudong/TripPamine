package com.example.trippaminebe.security.oauth2;

import com.example.trippaminebe.domain.user.entity.UserSocialProvider;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter

public class OAuth2Attributes {

  private Map<String, Object> attributes;
  private String nameAttributeKey;
  private String providerUserId;
  private String email;
  private String nickname;
  private UserSocialProvider provider;


  @Builder
  public OAuth2Attributes(
      Map<String, Object> attributes,
      String nameAttributeKey,
      String providerUserId,
      String email,
      String name,
      UserSocialProvider provider) {
    this.attributes = attributes;
    this.nameAttributeKey = nameAttributeKey;
    this.providerUserId = providerUserId;
    this.email = email;
    this.nickname = name;
    this.provider = provider;
  }

  public static OAuth2Attributes oAuth2Attribute(
      String registrationId,
      String userNameAttributeName,
      Map<String, Object> attributes) {
    if ("naver".equals(registrationId)) {
      return ofNaver("id", attributes);
    } else if ("kakao".equals(registrationId)) {
      return ofKakao("id", attributes);
    }
    return ofGoogle(userNameAttributeName, attributes);
  }

  private static OAuth2Attributes ofGoogle(String userNameAttributeName, Map<String, Object> attributes) {

    return OAuth2Attributes.builder()
        .name((String) attributes.get("name"))
        .email((String) attributes.get("email"))
        .provider(UserSocialProvider.GOOGLE)
        .providerUserId((String) attributes.get(userNameAttributeName))
        .attributes(attributes)
        .nameAttributeKey(userNameAttributeName)
        .build();
  }

  @SuppressWarnings("unchecked")
  private static OAuth2Attributes ofKakao(String userNameAttributeName, Map<String, Object> attributes) {
    Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
    Map<String, Object> profile = kakaoAccount != null
        ? (Map<String, Object>) kakaoAccount.get("profile")
        : null;

    String email = kakaoAccount != null ? (String) kakaoAccount.get("email") : null;
    String nickname = profile != null ? (String) profile.get("nickname") : null;

    return OAuth2Attributes.builder()
        .name(nickname)
        .provider(UserSocialProvider.KAKAO)
        .providerUserId(String.valueOf(attributes.get(userNameAttributeName)))
        .attributes(attributes)
        .nameAttributeKey(userNameAttributeName)
        .build();
  }

  @SuppressWarnings("unchecked")
  private static OAuth2Attributes ofNaver(String userNameAttributeName, Map<String, Object> attributes) {
    Map<String, Object> response = (Map<String, Object>) attributes.get("response");
    return OAuth2Attributes.builder()
        .name((String) response.get("name"))
        .email((String) response.get("email"))
        .providerUserId((String) response.get("id"))
        .provider(UserSocialProvider.NAVER)
        .attributes(attributes)
        .nameAttributeKey(userNameAttributeName)
        .build();
  }
}
