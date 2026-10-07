package com.example.trippaminebe.domain.board.repository;

import com.example.trippaminebe.domain.board.entity.BoardPostLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BoardPostLikeRepository extends JpaRepository<BoardPostLike, Long> {

  boolean existsByPost_PostIdAndUser_Id(Long postId, Long userId);

  Optional<BoardPostLike> findByPost_PostIdAndUser_Id(Long postId, Long userId);
}
