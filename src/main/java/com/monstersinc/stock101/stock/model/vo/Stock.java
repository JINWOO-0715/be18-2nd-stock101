package com.monstersinc.stock101.stock.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Stock {
    private Long stockId; // 종목 ID
    private String name; // 종목명
    private String isDelisted; // 상폐 여부 (Y/N)
    private String stockCode; // 단축코드 (6자리)
    private String corpCode; // DART 고유번호 (8자리)
    private String stdCode; // 표준코드 (12자리)
    private String marketType; // 시장구분 (KOSPI/KOSDAQ/KONEX)
    private String securityType; // 증권그룹구분코드 (ST:주권, EF:ETF 등)
    
    // MST 업종 분류
    private String largeIndustryCode; // 지수 업종 대분류 코드
    private String mediumIndustryCode; // 지수 업종 중분류 코드
    private String smallIndustryCode; // 지수 업종 소분류 코드
    private String kospi200SectorCode; // KOSPI200 섹터업종코드
    
    // 업종 참조 (FK to industries)
    private String industryCode; // 업종 코드 (idxcode.mst 기반, 5자리)
    private String industryName; // 업종명 (JOIN으로 가져옴)
    private String sectorName; // 한국 업종명 (음식료품 등)
    
    // 지수 포함 여부
    private String kospi100Yn; // KOSPI100 종목 여부 (Y/N)
    private String kospi50Yn; // KOSPI50 종목 여부 (Y/N)
    private String kospi300Yn; // KRX300 종목 여부 (Y/N)
    private String kospiYn; // KOSPI여부 (Y/N)
    private String krx300Yn; // KRX300 종목 여부 (Y/N)
    
    // 상장 정보
    private Long faceValue; // 액면가
    private String listingDate; // 상장 일자 (YYYYMMDD -> DB는 DATE)
    private Long capitalAmount; // 자본금
    private Long ipoPrice; // 공모 가격
    private String preferredStockCode; // 우선주 구분 코드 (0:보통주, 1:구형, 2:신형)
    
    // 상태 정보
    private String isManaged; // 관리종목 여부 (Y/N)
    private String isSuspended; // 거래정지 여부 (Y/N)
    
    // 재무 정보
    private Long salesAmount; // 매출액
    private Long operatingProfit; // 영업이익
    private Long netIncome; // 당기순이익
    private Double roe; // ROE(자기자본이익률)
    private String baseDate; // 기준년월 (YYYYMMDD)
    private Long marketCap; // 시가총액 (억)
    
    // 현재 시세
    private Double price; // 현재가
    private Double fluctuation; // 등락률
    
    // 지표
    private String individualIndicator; // 개미 지표 (STRONG_SELL, SELL, HOLD, BUY, STRONG_BUY)
    private String analystIndicator; // 전문가 지표 (STRONG_SELL, SELL, HOLD, BUY, STRONG_BUY)
    private String newsIndicator; // 뉴스 지표 (NEGATIVE, NEUTRAL, POSITIVE)
}