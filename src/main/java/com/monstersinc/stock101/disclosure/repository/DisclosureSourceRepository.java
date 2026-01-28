package com.monstersinc.stock101.disclosure.repository;

import com.monstersinc.stock101.disclosure.domain.DisclosureSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 공시보고서 소스 파일 레포지토리 (MyBatis Mapper)
 * 테이블: disclosure_source
 */
@Mapper
public interface DisclosureSourceRepository {

    /**
     * 소스 파일 저장
     */
    void save(DisclosureSource source);

    void updateStatus(Long sourceId, String status);

    /**
     * ID로 소스 파일 조회
     */
    Optional<DisclosureSource> findById(Long sourceId);


     /**
     * 파일 해시로 중복 체크
     */
    Optional<DisclosureSource> findByFileHash(String fileHash);


    /**
     * 소스 파일 삭제
     */
    void deleteById(Long sourceId);

}
