package com.monstersinc.stock101.disclosure.repository;

import com.monstersinc.stock101.disclosure.domain.DocumentChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 문서 청크 레포지토리 (MyBatis Mapper)
 */
@Mapper
public interface DocumentChunkRepository {

    /**
     * 청크 저장
     */
    void save(DocumentChunk chunk);

    /**
     * 다중 청크 저장 (Bulk Insert)
     */
    void saveAll(@Param("chunks") List<DocumentChunk> chunks);

    /**
     * 문서 ID로 청크 목록 조회
     */
    List<DocumentChunk> findByDocumentId(Long documentId);

    /**
     * 유사도 검색 (간단한 구현, 실제로는 벡터 DB 기능이나 애플리케이션 레벨 필터링 필요할 수 있음)
     * MariaDB에서는 벡터 연산 지원이 제한적이므로 전체 조회 후 메모리에서 계산하거나
     * Python 등 외부 서비스를 호출해야 할 수도 있음.
     * 여기서는 일단 전체 조회를 위한 메서드만 정의함.
     */

    /**
     * 모든 청크의 임베딩 조회 (메모리 내 검색용)
     * 주의: 데이터가 많으면 성능 문제 발생 가능
     */
    List<DocumentChunk> findAllEmbeddingsByStockId(Long stockId);

    /**
     * 문서 삭제 시 청크 삭제
     */
    void deleteByDocumentId(Long documentId);
}
