package com.example.trippaminebe.security.jwt;

import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenBlacklistService {

  private final Set<String> blacklist =
      ConcurrentHashMap.newKeySet();

  public void blacklist(String token) {
    blacklist.add(token);
  }

  public boolean isBlacklisted(String token) {
    return blacklist.contains(token);
  }
}
