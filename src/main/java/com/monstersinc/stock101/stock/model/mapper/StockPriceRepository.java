package com.monstersinc.stock101.stock.model.mapper;

import com.monstersinc.stock101.stock.model.vo.StockPrice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 주식 시세 정보 MyBatis Mapper
 */
@Mapper
public interface StockPriceRepository {
    
    /**
     * 종목과 날짜로 시세 정보 조회
     */
    Optional<StockPrice> findByStockIdAndDatetime(@Param("stockId") Long stockId, @Param("datetime") LocalDate datetime);
    
    /**
     * 종목의 최근 시세 조회
     */
    List<StockPrice> findByStockIdOrderByDatetimeDesc(@Param("stockId") Long stockId);
    
    /**
     * 종목의 기간별 시세 조회
     */
    List<StockPrice> findByStockIdAndDatetimeBetweenOrderByDatetimeAsc(
            @Param("stockId") Long stockId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    
    /**
     * 최근 N개 시세 조회
     */
    List<StockPrice> findRecentPrices(@Param("stockId") Long stockId, @Param("limit") int limit);
    
    /**
     * 특정 날짜 이후의 시세 데이터 개수
     */
    long countByStockIdAndDatetimeAfter(@Param("stockId") Long stockId, @Param("datetime") LocalDate datetime);

    /**
     * 종목의 가장 최근 날짜 조회
     */
    LocalDate findLatestDateByStockId(@Param("stockId") Long stockId);
    
    /**
     * 단건 저장
     */
    int insertPrice(StockPrice price);
    
    /**
     * 일괄 저장 (Bulk Insert)
     */
    int insertPrices(List<StockPrice> prices);
}
