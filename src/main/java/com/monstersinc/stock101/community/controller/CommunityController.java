package com.monstersinc.stock101.community.controller;

import com.monstersinc.stock101.common.model.dto.BaseResponseDto;
import com.monstersinc.stock101.common.model.dto.ItemsResponseDto;
import com.monstersinc.stock101.community.model.dto.CommentRequestDto;
import com.monstersinc.stock101.community.model.dto.CommentResponseDto;
import com.monstersinc.stock101.community.model.dto.PostRequestDto;
import com.monstersinc.stock101.community.model.dto.PostResponseDto;
import com.monstersinc.stock101.community.model.service.CommunityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/v1/board")
@RequiredArgsConstructor
public class CommunityController {
    private final CommunityService communityService;

    // 게시물 등록 + 로그인 필요
    @PostMapping("/posts")
    public ResponseEntity<BaseResponseDto<PostResponseDto>> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PostRequestDto requestDto) {

        long postId = communityService.saveAPost(userId, requestDto);
        PostResponseDto body = communityService.getPostDetail(postId, userId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new BaseResponseDto<>(HttpStatus.CREATED, body));
    }

    // 게시물 상세 조회 + 사용자 좋아요 여부(로그인 했으면)
    @GetMapping("/posts/{postId}")
    public ResponseEntity<BaseResponseDto<PostResponseDto>> detail(
            @AuthenticationPrincipal() Long userId,
            @PathVariable long postId) {

        PostResponseDto dto = communityService.getPostDetail(postId, userId);
        return ResponseEntity.ok(new BaseResponseDto<>(HttpStatus.OK, dto));
    }

    // 종목 별 게시물 리스트 조회 + 사용자 좋아요 여부(로그인 했으면)
    @GetMapping("/posts")
    public ResponseEntity<ItemsResponseDto<PostResponseDto>> list(
            @AuthenticationPrincipal() Long userId, // 로그인 안 했으면 null
            @RequestParam long stockId) {

        List<PostResponseDto> items = communityService.getPostListByStock(stockId, userId);
        return ResponseEntity.ok(ItemsResponseDto.ofAll(HttpStatus.OK, items));
    }

    // 게시물 삭제
    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<BaseResponseDto<String>> delete(@PathVariable long postId) {

        communityService.delete(postId);

        return ResponseEntity.ok(new BaseResponseDto<>(HttpStatus.OK, "게시글이 삭제되었습니다."));
    }

    // 좋아요 등록 및 취소 + 로그인 필요
    @PostMapping("/posts/{postId}/like")
    public ResponseEntity<BaseResponseDto<PostResponseDto>> like(
            @AuthenticationPrincipal Long userId,
            @PathVariable long postId) {

        PostResponseDto post = communityService.likePost(postId, userId);

        return ResponseEntity.ok(new BaseResponseDto<>(HttpStatus.OK, post));
    }

    // 게시물 상세 페이지 댓글 조회
    @GetMapping("/posts/{postId}/comments")
    public ResponseEntity<ItemsResponseDto<CommentResponseDto>> listComments(
            @PathVariable long postId) {

        List<CommentResponseDto> items = communityService.getCommentListByPostId(postId);
        return ResponseEntity.ok(ItemsResponseDto.ofAll(HttpStatus.OK, items));
    }

    // 댓글 등록 + 로그인 필요
    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<BaseResponseDto<CommentResponseDto>> create(
            @AuthenticationPrincipal Long userId,
            @PathVariable long postId,
            @Valid @RequestBody CommentRequestDto requestDto) {

        requestDto.setPostId(postId);
        requestDto.setUserId(userId);

        long commentId = communityService.saveAComment(requestDto);
        CommentResponseDto body = communityService.getAComment(commentId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new BaseResponseDto<>(HttpStatus.CREATED, body));
    }

    // 댓글 삭제(관리자 권한)
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<BaseResponseDto<String>> deleteComment(
            @PathVariable long commentId) {

        communityService.deleteComment(commentId);

        return ResponseEntity.ok(new BaseResponseDto<>(HttpStatus.OK, "댓글이 삭제되었습니다."));
    }

    // 특정 유저가 작성한 게시물 조회 + 사용자 좋아요 여부(로그인 했을 때)
    @GetMapping("/user/{writerId}")
    public ResponseEntity<ItemsResponseDto<PostResponseDto>> getPostByUserId(
            @AuthenticationPrincipal Long userId, // 로그인 안 했으면 null)
            @PathVariable long writerId) {

        List<PostResponseDto> items = communityService.getPostListByUserId(writerId, userId);
        return ResponseEntity.ok(ItemsResponseDto.ofAll(HttpStatus.OK, items));
    }

    // 내가 작성한 게시물 조회 + 로그인 필요 + 사용자 좋아요 여부
    @GetMapping("/me")
    public ResponseEntity<ItemsResponseDto<PostResponseDto>> getMyPosts(
            @AuthenticationPrincipal Long userId) {

        List<PostResponseDto> items = communityService.getPostListByUserId(userId, userId);

        return ResponseEntity.ok(ItemsResponseDto.ofAll(HttpStatus.OK, items));
    }
}
