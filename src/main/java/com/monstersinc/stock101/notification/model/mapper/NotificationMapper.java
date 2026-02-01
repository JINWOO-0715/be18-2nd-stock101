package com.monstersinc.stock101.notification.model.mapper;

import com.monstersinc.stock101.notification.model.dto.NotificationDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 알림 Mapper 인터페이스
 */
@Mapper
public interface NotificationMapper {

    /**
     * 알림 생성
     */
    void insertNotification(NotificationDto notification);

    /**
     * 사용자별 알림 목록 조회
     */
    List<NotificationDto> findByUserId(@Param("userId") Long userId);

    /**
     * 사용자별 미읽음 알림 목록 조회
     */
    List<NotificationDto> findUnreadByUserId(@Param("userId") Long userId);

    /**
     * 알림 ID로 조회
     */
    Optional<NotificationDto> findById(@Param("id") Long id);

    /**
     * 알림 읽음 처리
     */
    void markAsRead(@Param("id") Long id);

    /**
     * 사용자의 모든 알림 읽음 처리
     */
    void markAllAsReadByUserId(@Param("userId") Long userId);

    /**
     * 사용자의 미읽음 알림 개수 조회
     */
    int countUnreadByUserId(@Param("userId") Long userId);

    /**
     * 알림 삭제
     */
    void deleteById(@Param("id") Long id);
}
