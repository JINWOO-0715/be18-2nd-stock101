package com.monstersinc.stock101.indicator.model.vo;

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
public class FinancialIndicator {
    /** 재무 지표 ID (PK) */
    private Long financialId;

    /** 주당 순이익 (DB column: EPS) */
    private Double eps;

    /** 주당 순자산 (DB column: BPS) */
    private Double bps;

    /** 자기자본 이익률 */
    private Double roe;

    /** 총 자산 이익률 */
    private Double roa;

    /** 주가수익비율 */
    private Double per;

    /** 주가순자산비율 */
    private Double pbr;

    /** 분기 */
    private Integer quarter;

    /** 종목 ID (FK -> stocks.stock_id) */
    private Long stockId;
}
