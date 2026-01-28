package com.monstersinc.stock101.stock.model.service;

import com.monstersinc.stock101.exception.GlobalException;
import com.monstersinc.stock101.exception.message.GlobalExceptionMessage;
import com.monstersinc.stock101.stock.model.mapper.StockMapper;
import com.monstersinc.stock101.stock.model.vo.Stock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StockServiceImpl implements StockService {
    private final StockMapper stockMapper;

    @Override
    public List<Stock> getStockList() {
        List<Stock> stockList = stockMapper.selectStockList();
        if(stockList == null || stockList.isEmpty()) {
            throw new GlobalException(GlobalExceptionMessage.STOCK_NOT_FOUND);
        }
        return stockList;
    }

    @Override
    public Stock getStockById(long stockId) {
        Stock stock = stockMapper.selectStockById(stockId);
        if(stock == null) {
            throw new GlobalException(GlobalExceptionMessage.STOCK_NOT_FOUND);
        }
        return stock;
    }
    
    @Override
    public List<Stock> searchStocks(String query) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }
        
        List<Stock> stockList = stockMapper.selectStocksByKeyword(query.trim());
        return stockList != null ? stockList : List.of();
    }
}
