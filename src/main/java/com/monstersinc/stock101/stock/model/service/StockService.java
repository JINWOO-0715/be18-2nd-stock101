package com.monstersinc.stock101.stock.model.service;

import java.util.List;

import com.monstersinc.stock101.stock.model.dto.StocksCacheDto;
import com.monstersinc.stock101.stock.model.vo.Stock;
import org.springframework.cache.annotation.Cacheable;

public interface StockService {

    List<StocksCacheDto> getStockList();

    Stock getStockById(long stockId);
    
    /**
     * 종목 검색 (종목명, 종목 코드로 검색)
     */
    List<Stock> searchStocks(String query);
}
