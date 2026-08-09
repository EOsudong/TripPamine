package com.example.trippaminebe.domain.user.repository;

import com.example.trippaminebe.domain.user.entity.User;
import com.sun.jdi.InterfaceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository <User, Long> {

  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);

  boolean existsByUserName(String userName);

}
