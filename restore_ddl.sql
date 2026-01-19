-- RAG 기능을 위한 공시보고서 테이블 추가
-- MariaDB용 스키마 (벡터는 JSON 형식으로 저장)

-- 공시문서 테이블
CREATE TABLE IF NOT EXISTS `disclosure_documents` (
  `document_id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '문서 ID',
  `stock_id` BIGINT(20) NOT NULL COMMENT '종목 ID',
  `title` VARCHAR(500) NOT NULL COMMENT '문서 제목',
  `document_type` VARCHAR(50) DEFAULT NULL COMMENT '문서 타입 (10-K, 10-Q, 8-K 등)',
  `file_path` VARCHAR(1000) NOT NULL COMMENT '파일 저장 경로',
  `file_name` VARCHAR(255) NOT NULL COMMENT '원본 파일명',
  `file_size` BIGINT(20) DEFAULT NULL COMMENT '파일 크기 (bytes)',
  `upload_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '업로드 날짜',
  `uploaded_by` BIGINT(20) DEFAULT NULL COMMENT '업로드한 사용자 ID',
  `processing_status` ENUM('UPLOADED', 'PROCESSING', 'COMPLETED', 'FAILED') NOT NULL DEFAULT 'UPLOADED' COMMENT '처리 상태',
  `total_pages` INT(11) DEFAULT NULL COMMENT '총 페이지 수',
  `total_chunks` INT(11) DEFAULT NULL COMMENT '총 청크 수',
  `metadata` JSON DEFAULT NULL COMMENT '메타데이터 (JSON)',
  `error_message` TEXT DEFAULT NULL COMMENT '에러 메시지 (실패 시)',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`document_id`),
  KEY `IDX_disclosure_stock` (`stock_id`),
  KEY `IDX_disclosure_status` (`processing_status`),
  KEY `IDX_disclosure_upload_date` (`upload_date`),
  CONSTRAINT `FK_disclosure_stock` FOREIGN KEY (`stock_id`) REFERENCES `stocks` (`stock_id`),
  CONSTRAINT `FK_disclosure_user` FOREIGN KEY (`uploaded_by`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='공시보고서 문서';

-- 문서 청크 테이블 (벡터 임베딩 저장)
CREATE TABLE IF NOT EXISTS `document_chunks` (
  `chunk_id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '청크 ID',
  `document_id` BIGINT(20) NOT NULL COMMENT '문서 ID',
  `chunk_index` INT(11) NOT NULL COMMENT '청크 순서',
  `chunk_text` TEXT NOT NULL COMMENT '청크 텍스트 내용',
  `page_number` INT(11) DEFAULT NULL COMMENT '페이지 번호',
  `start_char` INT(11) DEFAULT NULL COMMENT '시작 문자 위치',
  `end_char` INT(11) DEFAULT NULL COMMENT '종료 문자 위치',
  `token_count` INT(11) DEFAULT NULL COMMENT '토큰 수',
  `embedding_vector` JSON DEFAULT NULL COMMENT '벡터 임베딩 (JSON 배열)',
  `metadata` JSON DEFAULT NULL COMMENT '메타데이터',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`chunk_id`),
  KEY `IDX_chunk_document` (`document_id`),
  KEY `IDX_chunk_page` (`page_number`),
  KEY `IDX_chunk_index` (`chunk_index`),
  CONSTRAINT `FK_chunk_document` FOREIGN KEY (`document_id`) REFERENCES `disclosure_documents` (`document_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='문서 청크 및 벡터 임베딩';

-- 문서 분석 히스토리 테이블 (선택적)
CREATE TABLE IF NOT EXISTS `disclosure_analysis_history` (
  `analysis_id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '분석 ID',
  `document_id` BIGINT(20) NOT NULL COMMENT '문서 ID',
  `user_id` BIGINT(20) DEFAULT NULL COMMENT '사용자 ID',
  `query` TEXT NOT NULL COMMENT '질문/쿼리',
  `response` TEXT NOT NULL COMMENT 'AI 응답',
  `relevant_chunks` JSON DEFAULT NULL COMMENT '관련 청크 ID 목록',
  `confidence_score` DOUBLE DEFAULT NULL COMMENT '신뢰도 점수',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`analysis_id`),
  KEY `IDX_analysis_document` (`document_id`),
  KEY `IDX_analysis_user` (`user_id`),
  KEY `IDX_analysis_date` (`created_at`),
  CONSTRAINT `FK_analysis_document` FOREIGN KEY (`document_id`) REFERENCES `disclosure_documents` (`document_id`) ON DELETE CASCADE,
  CONSTRAINT `FK_analysis_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='문서 분석 히스토리';

-- 인덱스 추가 (성능 최적화)
-- CREATE INDEX IDX_chunks_composite ON document_chunks(document_id, chunk_index);
