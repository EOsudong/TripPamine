package com.example.trippaminebe.domain.user.repository;

import com.example.trippaminebe.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository <User, Long> {

  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);

  boolean existsByUserName(String userName);

  // [닉네임 변경] 나를 제외한 다른 회원이 이미 쓰고 있는 닉네임인지
  boolean existsByUserNameAndIdNot(String userName, Long id);

  // [마스코트 첫 포획 보상] 같은 사용자의 포획 확정 요청이 동시에 들어와도
  // "첫 포획 여부 확인 → 포인트 지급"
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.id = :id")
  Optional<User> findByIdForUpdate(@Param("id") Long id);

}