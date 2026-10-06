package com.example.trippaminebe.domain.board.dto.response;

import com.example.trippaminebe.domain.board.entity.BoardPostImage;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BoardImageResponse {

  private Long imageId;
  private String imageUrl;
  private String originalName;

  public static BoardImageResponse from(BoardPostImage image) {
    return new BoardImageResponse(image.getImageId(), image.getImageUrl(), image.getOriginalName());
  }
}
