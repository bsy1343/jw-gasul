// WorkerImportTest.java — 근로자 엑셀 일괄등록(명단+계좌) 파싱/검증 및 화면·템플릿 다운로드 검증
package com.jwgasul;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jwgasul.dto.ImportResult;
import com.jwgasul.service.WorkerService;
import java.io.ByteArrayOutputStream;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "importer")
class WorkerImportTest {

    @Autowired
    private WorkerService workerService;

    @Autowired
    private MockMvc mockMvc;

    // 한 행 유효(계좌 포함) + 한 행 필수값 누락 → 1건 생성, 1건 오류로 기록
    @Test
    void importsValidRowsAndReportsInvalid() throws Exception {
        byte[] xlsx = buildSheet(new String[][] {
                {"외국인", "임포트테스트일", "Import One", "1992-03-04", "010-7777-0001",
                        "베트남", "E-9", "2027-01-01", "2026-02-02", "N", "",
                        "국민", "임포트테스트일", "111-22-333444", "", "", "", "", "", ""},
                // 생년월일 누락 → 오류 행
                {"한국인", "임포트테스트이", "", "", "010-7777-0002",
                        "", "", "", "2026-02-02", "Y", "", "", "", "", "", "", "", "", "", ""}
        });
        MockMultipartFile file = new MockMultipartFile(
                "file", "in.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx);

        ImportResult result = workerService.importExcel(file);

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.errors()).anyMatch(e -> e.contains("필수"));
    }

    // 동일 인원 두 행 → 두 번째는 중복으로 건너뜀
    @Test
    void skipsDuplicateWithinFile() {
        byte[] xlsx = buildSheet(new String[][] {
                {"외국인", "임포트중복", "Dup", "1988-08-08", "010-8888-0001",
                        "네팔", "E-9", "", "", "N", "", "", "", "", "", "", "", "", "", ""},
                {"외국인", "임포트중복", "Dup", "1988-08-08", "010-8888-0001",
                        "네팔", "E-9", "", "", "N", "", "", "", "", "", "", "", "", "", ""}
        });
        MockMultipartFile file = new MockMultipartFile("file", "dup.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx);

        ImportResult result = workerService.importExcel(file);

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
    }

    // 일괄등록 화면 렌더링
    @Test
    void importScreenRenders() throws Exception {
        mockMvc.perform(get("/workers/import")).andExpect(status().isOk());
    }

    // 템플릿 다운로드가 xlsx로 내려온다
    @Test
    void templateDownloads() throws Exception {
        mockMvc.perform(get("/workers/import/template"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("worker-import-template.xlsx")));
    }

    // 헤더 1행 + 데이터 N행 xlsx 바이트 생성
    private byte[] buildSheet(String[][] rows) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("근로자");
            Row header = sheet.createRow(0);
            for (int i = 0; i < 20; i++) {
                header.createCell(i).setCellValue("h" + i);
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    row.createCell(c).setCellValue(rows[r][c]);
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
