package com.monstersinc.stock101.stock.model.dto;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import java.nio.charset.Charset;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Slf4j
public class StockMstDto {
    private String mkscShrnIscd;           // 단축코드
    private String stndIscd;               // 표준코드
    private String htsKorIsnm;             // 한글종목명
    private String securityType;           // 유가증권 종류
    private String scrtGrpClsCode;         // 증권그룹구분코드
    private String bstpLargDivCode;        // 업종 대분류
    private String bstpMedmDivCode;        // 업종 중분류
    private String bstpSmalDivCode;        // 업종 소분류
    private String kospi200ApntClsCode;    
    private String kospi100IssuYn;
    private String kospi50IssuYn;
    private String krx300IssuYn;
    private String kospiIssuYn;
    private String stckFcam;               // 액면가
    private String stckLstnDate;           // 상장일자
    private String cpfn;                   // 자본금
    private String poPrc;                  // 공모가
    private String prstClsCode;
    private String saleAccount;            // 매출액
    private String bsopPrfi;               // 영업이익
    private String thtrNtin;               // 당기순이익
    private String roe;
    private String baseDate;
    private String prdyAvlsScal;           // 시가총액
    private String mangIssuYn;
    private String trhtYn;

    public static StockMstDto parseLine(String line) {
        if (line == null || line.isEmpty()) return null;

        try {
            // 반드시 EUC-KR 바이트로 변환
            byte[] b = line.getBytes(Charset.forName("EUC-KR"));
            
            // MST 파일의 한 라인은 최소 280바이트 이상입니다.
            if (b.length < 280) return null;

            StockMstDto dto = new StockMstDto();

            // --- Part 1: 헤더 (표준 71바이트 고정 영역) ---
            dto.mkscShrnIscd = byteSub(b, 0, 9);   // 0~9
            dto.stndIscd     = byteSub(b, 9, 12);  // 9~21
            dto.htsKorIsnm   = byteSub(b, 21, 40); // 21~61 (종목명 고정 40바이트)

            // --- Part 2: 상세 정보 (오프셋 71바이트부터 시작) ---
            int p = 61; // 규격상 한글명 뒤의 위치

            dto.scrtGrpClsCode = byteSub(b, p, 2); 
            dto.securityType   = byteSub(b, p, 2); p += 2; // ST(주권) 등
            
            p += 1; // 시가총액규모 skip
            dto.bstpLargDivCode = byteSub(b, p, 4); p += 4;
            dto.bstpMedmDivCode = byteSub(b, p, 4); p += 4;
            dto.bstpSmalDivCode = byteSub(b, p, 4); p += 4;

            // 지수 포함 여부
            dto.kospi200ApntClsCode = byteSub(b, p + 3, 1);
            dto.kospi100IssuYn       = byteSub(b, p + 4, 1);
            dto.kospi50IssuYn        = byteSub(b, p + 5, 1);
            p += 26; 

            p += 19; // 가격/수량 단위 영역 skip

            dto.trhtYn     = byteSub(b, p, 1); p += 1; // 거래정지
            p += 1;                                    // 정리매매
            dto.mangIssuYn = byteSub(b, p, 1); p += 1; // 관리종목
            
            p += 30; // 락/증자/신용/거래량 skip

            // --- Part 3: 주요 정보 (상장일, 액면가 등) ---
            dto.stckFcam     = byteSub(b, p, 12); p += 12; // 액면가
            dto.stckLstnDate = byteSub(b, p, 8);  p += 8;  // 상장일자 (YYYYMMDD)
            p += 15;                                       // 상장주수
            dto.cpfn         = byteSub(b, p, 21); p += 21; // 자본금
            p += 2;                                        // 결산월
            dto.poPrc        = byteSub(b, p, 7);  p += 7;  // 공모가
            dto.prstClsCode  = byteSub(b, p, 1);  p += 1;  // 우선주구분
            
            p += 2; // 공매도/이상급등 skip
            dto.krx300IssuYn = byteSub(b, p, 1); p += 1;
            dto.kospiIssuYn  = byteSub(b, p, 1); p += 1;
            
            dto.saleAccount  = byteSub(b, p, 9); p += 9; // 매출액
            dto.bsopPrfi     = byteSub(b, p, 9); p += 9; // 영업이익
            p += 9;                                      // 경상이익
            dto.thtrNtin     = byteSub(b, p, 5); p += 5; // 당기순이익
            dto.roe          = byteSub(b, p, 9); p += 9; // ROE
            dto.baseDate     = byteSub(b, p, 8); p += 8; // 기준년월
            dto.prdyAvlsScal = byteSub(b, p, 9); p += 9; // 시가총액

            return dto;
        } catch (Exception e) {
            log.error("MST 파싱 중 오류 발생: {}", e.getMessage());
            return null;
        }
    }

    private static String byteSub(byte[] data, int offset, int length) {
        if (data.length < offset + length) return "";
        try {
            return new String(data, offset, length, "EUC-KR").trim();
        } catch (Exception e) {
            return "";
        }
    }
}