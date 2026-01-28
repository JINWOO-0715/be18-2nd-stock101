package com.monstersinc.stock101.kis.model.mapper;

import com.monstersinc.stock101.kis.model.vo.ApiToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * API 토큰 MyBatis Mapper
 */
@Mapper
public interface ApiTokenMapper {

    /**
     * API 이름으로 토큰 조회
     */
    ApiToken selectByApiName(@Param("apiName") String apiName);

    /**
     * 토큰 저장 (INSERT or UPDATE)
     */
    int upsertToken(ApiToken apiToken);

    /**
     * 토큰 삭제
     */
    int deleteByApiName(@Param("apiName") String apiName);
}
