package com.example.trippaminebe.domain.user.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "USER_SOCIAL_ACCOUNTS")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class UserSocialAccount {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_SOCIAL_GEN")
  @SequenceGenerator(name = "SEQ_SOCIAL_GEN", sequenceName = "SEQ_SOCIAL_ACCOUNTS", allocationSize = 1)
  @Column(name = "SOCIAL_ID")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "USER_ID", nullable = false)
  private User user;

  @Column(name = "OAUTH_PROVIDER", nullable = false, length = 20)
  private String oauthProvider; // GOOGLE, KAKAO, NAVER

  @Column(name = "PROVIDER_USER_ID", nullable = false, length = 100)
  private String providerUserId;

  @Column(name = "CREATE_DATE", insertable = false, updatable = false)
  private LocalDateTime createDate;

  public void assignUser(User user) {
    this.user = user;
  }


}
