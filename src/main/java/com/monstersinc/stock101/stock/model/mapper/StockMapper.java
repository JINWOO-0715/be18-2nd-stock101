package com.monstersinc.stock101.stock.model.mapper;

import com.monstersinc.stock101.stock.model.vo.Stock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StockMapper {
    Stock selectStockById(long stockId);

    List<Stock> selectStockList();
    
    /**
     * 키워드로 종목 검색 (종목명, 종목 코드)
     */
    List<Stock> selectStocksByKeyword(@Param("keyword") String keyword);
    
    /**
     * 종목 코드로 종목 조회
     */
    Stock selectStockByCode(@Param("stockCode") String stockCode);
    
    /**
     * 종목 기본 정보 업데이트 (종목명, 산업/섹터 코드)
     */
    int updateStockBasicInfo(Stock stock);
    
    /**
     * 신규 종목 삽입
     */
    int insertStock(Stock stock);
}
