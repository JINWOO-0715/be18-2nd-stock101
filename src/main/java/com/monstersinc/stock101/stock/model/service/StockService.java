package com.monstersinc.stock101.stock.model.service;

import java.util.List;

import com.monstersinc.stock101.stock.model.vo.Stock;

public interface StockService {
    List<Stock> getStockList();

    Stock getStockById(long stockId);
    
    /**
     * 종목 검색 (종목명, 종목 코드로 검색)
     */
    List<Stock> searchStocks(String query);
}
