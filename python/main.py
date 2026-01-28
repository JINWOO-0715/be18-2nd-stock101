import os
import fitz  # PyMuPDF
from fastapi import FastAPI, Body

from docling.document_converter import DocumentConverter, PdfFormatOption
from docling.datamodel.pipeline_options import PdfPipelineOptions
from docling.datamodel.base_models import InputFormat

app = FastAPI()

# 1. Docling 설정 (전역 선언으로 재사용성 높임)
pipeline_options = PdfPipelineOptions()
pipeline_options.do_ocr = False
pipeline_options.do_table_structure = True  # 표 분석은 유지

converter = DocumentConverter(
    format_options={
        InputFormat.PDF: PdfFormatOption(pipeline_options=pipeline_options)
    },
)

def get_core_section_pdf(pdf_path):
    doc = fitz.open(pdf_path)
    toc = doc.get_toc()
    
    if not toc:
        return pdf_path

    # 분석 대상 키워드를 더 유연하게 정의
    target_keywords = [
        "사업의내용", "요약재무정보", "연결재무제표", 
        "재무제표", "배당에관한사항", "이사의경영진단"
    ]

    pages_to_keep = []
    total_pages = len(doc)

    for i, entry in enumerate(toc):
        level, title, page = entry
        # 제목 정규화: 공백 제거 및 대문자화, 로마자 기호 제거 시도
        clean_title = title.replace(" ", "").replace("II.", "").replace("IV.", "")

        if any(target in clean_title for target in target_keywords):
            # '주석'이 포함된 섹션은 제외
            if "주석" in clean_title:
                continue
            
            start_p = page - 1  # 0-based index로 변환
            
            # 다음 '같은 레벨' 혹은 '상위 레벨'의 북마크를 찾을 때까지를 범위로 설정
            end_p = total_pages - 1
            for j in range(i + 1, len(toc)):
                next_level, _, next_page = toc[j]
                if next_level <= level: # 상위 또는 동일 레벨 발견 시 멈춤
                    end_p = next_page - 2 # 직전 페이지까지
                    break
            
            # 범위 검증 및 추가
            if 0 <= start_p <= end_p:
                print(f"DEBUG: 섹션 추출 [ {title} ] -> {start_p + 1}p ~ {end_p + 1}p")
                pages_to_keep.append((start_p, end_p))

    if not pages_to_keep:
        doc.close()
        return pdf_path

    # 중복되거나 겹치는 페이지 범위 병합 (선택 사항이지만 안전함)
    pages_to_keep.sort()
    
    new_doc = fitz.open()
    for start, end in pages_to_keep:
        # insert_pdf는 범위가 겹치면 중복 삽입될 수 있으므로 주의
        new_doc.insert_pdf(doc, from_page=start, to_page=end)

    sliced_path = pdf_path.replace(".pdf", "_optimized_tmp.pdf")
    new_doc.save(sliced_path)
    new_doc.close()
    doc.close()
    
    return sliced_path



@app.post("/analyze")
async def analyze_pdf(payload: dict = Body(...)):
    pdf_path = payload.get("path")
    
    if not pdf_path or not os.path.exists(pdf_path):
        return {"status": "error", "error": "파일 경로가 올바르지 않습니다."}

    target_pdf = None
    try:
        # [단계 1] 핵심 섹션 동적 추출 (주석 제외)
        target_pdf = get_core_section_pdf(pdf_path)
        print(f"DEBUG: Optimized PDF 생성 완료 -> {target_pdf}")

        # [단계 2] Docling으로 Markdown 변환
        result = converter.convert(target_pdf)
        md_output = result.document.export_to_markdown()

        # [단계 3] 결과 저장
        md_file_path = os.path.splitext(pdf_path)[0] + ".md"
        with open(md_file_path, "w", encoding="utf-8") as f:
            f.write(md_output)

        return {
    "status": "success",
    "content": md_output,
    "md_path": md_file_path,
    "metadata": {
        "original_path": pdf_path,
        "extracted_pages": len(result.document.pages) if hasattr(result.document, 'pages') else "unknown"
    }
        }
    except Exception as e:
        import traceback
        traceback.print_exc() 
        return {"status": "error", "error": str(e)}

    finally:
        # 임시 파일 삭제
        if target_pdf and "_optimized_tmp.pdf" in target_pdf and os.path.exists(target_pdf):
            os.remove(target_pdf)