package com.monstersinc.stock101.user.controller;

import com.monstersinc.stock101.common.model.dto.BaseResponseDto;
import com.monstersinc.stock101.common.model.dto.ItemsResponseDto;
import com.monstersinc.stock101.exception.GlobalException;
import com.monstersinc.stock101.exception.message.GlobalExceptionMessage;
import com.monstersinc.stock101.user.model.dto.UserRegisterRequestDto;
import com.monstersinc.stock101.user.model.dto.UpdateProfileRequestDto;
import com.monstersinc.stock101.user.model.service.UserService;
import com.monstersinc.stock101.user.model.vo.User;
import com.monstersinc.stock101.user.model.vo.UserProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    // 사용자 회원가입
    @PostMapping("/register")
    public ResponseEntity<BaseResponseDto<User>> registerUser(@RequestBody @Valid UserRegisterRequestDto userRequestDto) {

        User registeredUser = userService.registerUser(userRequestDto);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new BaseResponseDto<>(HttpStatus.CREATED, registeredUser));
    }


    @GetMapping("/me")
    public ResponseEntity<BaseResponseDto<User>> getMe(@AuthenticationPrincipal Long userId) {
        User user = userService.getUserByUserId(userId);

        // user 정보를 리턴한다.
        return ResponseEntity.ok(new BaseResponseDto<>(HttpStatus.OK, user));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<BaseResponseDto<UserProfile>> getUserById(@PathVariable Long userId) {
        UserProfile profile = userService.getUserProfileById(userId);
        return ResponseEntity.ok(new BaseResponseDto<>(HttpStatus.OK, profile));
    }

    @GetMapping("/check-id")
    public ResponseEntity<BaseResponseDto<Map<String, Object>>> getCheckId(
            @NotBlank
            @Email(message = "이메일은 user@example.com 형식이어야 합니다.")
            @RequestParam @Valid String email) {

        boolean isEmailExist = userService.checkEmailExists(email);

        Map<String, Object> data = new HashMap<>();

        if (isEmailExist) {
            throw new GlobalException(GlobalExceptionMessage.DUPLICATE_EMAIL);
        } else {
            data.put("available", true);
        }

        return ResponseEntity.status(HttpStatus.OK).body(new BaseResponseDto<>(HttpStatus.OK, data));
    }

    @PatchMapping("/me")
    public ResponseEntity<BaseResponseDto<User>> updateUserProfile(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid UpdateProfileRequestDto requestDto) {

        User updateUserInfo = userService.updateProfile(userId, requestDto);

        return ResponseEntity.ok(new BaseResponseDto<>(HttpStatus.OK, updateUserInfo));

    }

    @DeleteMapping("/me")
    public ResponseEntity<BaseResponseDto<User>> deleteMe(@AuthenticationPrincipal Long userId) {

        userService.softDeleteUser(userId);

        return ResponseEntity
                .noContent()
                .build();

    }

    @GetMapping("/best-predictors")
    public ResponseEntity<ItemsResponseDto<User>> getBestPredictors() {

        List<User> bestPredictors = userService.getBestPredictors();

        return ResponseEntity.status(HttpStatus.OK).body(ItemsResponseDto.ofAll(HttpStatus.OK, bestPredictors));
    }

}
