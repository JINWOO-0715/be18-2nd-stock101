package com.monstersinc.stock101.dart.model.mapper;

import com.monstersinc.stock101.dart.domain.DartDisclosureEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * DART 공시정보 MyBatis 매퍼
 */
@Mapper
public interface DartDisclosureMapper {
    
    /**
     * 공시정보 저장
     */
    int insertDisclosure(DartDisclosureEntry entry);
    
    /**
     * 공시정보 일괄 저장
     */
    int insertDisclosures(List<DartDisclosureEntry> entries);
    
    /**
     * 회사 고유번호로 기존 공시정보 삭제
     */
    int deleteByCorpCode(@Param("corpCode") String corpCode);
    
    /**
     * 회사 고유번호로 공시정보 조회
     */
    List<DartDisclosureEntry> selectByCorpCode(@Param("corpCode") String corpCode);
    
    /**
     * 모든 공시정보 조회
     */
    List<DartDisclosureEntry> selectAll();
}
