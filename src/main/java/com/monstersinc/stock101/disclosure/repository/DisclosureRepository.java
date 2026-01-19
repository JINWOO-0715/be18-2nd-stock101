package com.monstersinc.stock101.disclosure.repository;

import com.monstersinc.stock101.disclosure.domain.DisclosureDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 공시보고서 문서 레포지토리 (MyBatis Mapper)
 */
@Mapper
public interface DisclosureRepository {

    /**
     * 문서 저장
     */
    void save(DisclosureDocument document);

    /**
     * ID로 문서 조회
     */
    Optional<DisclosureDocument> findById(Long documentId);

    /**
     * 종목 ID로 문서 목록 조회
     */
    List<DisclosureDocument> findByStockId(Long stockId);

    /**
     * 문서 상태 업데이트
     */
    void updateStatus(@Param("documentId") Long documentId,
            @Param("status") DisclosureDocument.ProcessingStatus status,
            @Param("errorMessage") String errorMessage);

    /**
     * 문서 메타데이터 업데이트
     */
    void updateMetadata(@Param("documentId") Long documentId,
            @Param("totalPages") Integer totalPages,
            @Param("totalChunks") Integer totalChunks,
            @Param("metadata") String metadata);

    /**
     * 문서 삭제
     */
    void deleteById(Long documentId);
}
