package cn.edu.rpm.funding.domain.service;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.funding.domain.model.PaymentImportRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link PaymentImportFileParser} 纯单元测试（T-FUND-033；TC-FUND-053～055）。
 */
class PaymentImportFileParserTest {

    private static final String HEADER = "external_payment_no,external_expense_no,project_no,"
            + "amount,currency,paid_at,voucher_no";

    private PaymentImportFileParser parser;

    @BeforeEach
    void setUp() {
        parser = new PaymentImportFileParser(new ObjectMapper());
    }

    @Test
    void parsesHundredValidCsvRows() {
        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        for (int i = 1; i <= 100; i++) {
            csv.append(String.format("PAY-%03d,EXP-2026-%03d,KY-2026-001,1000.00,CNY,"
                    + "2026-11-17T09:30:00+08:00,PZ-001%n", i, i));
        }

        List<PaymentImportRow> rows = parser.parse(
                csv.toString().getBytes(StandardCharsets.UTF_8), "CSV");

        assertThat(rows).hasSize(100);
        assertThat(rows).allMatch(PaymentImportRow::valid);
        assertThat(rows.get(0).externalPaymentNo()).isEqualTo("PAY-001");
        assertThat(rows.get(0).amount()).isEqualByComparingTo("1000.00");
        assertThat(rows.get(99).rowNo()).isEqualTo(100);
        assertThat(rows.get(0).rawData()).contains("\"external_payment_no\":\"PAY-001\"");
    }

    @Test
    void marksBlankAmountAndDuplicatePaymentNoWithSpecificCodes() {
        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        for (int i = 1; i <= 100; i++) {
            String paymentNo = i == 33 ? "PAY-001" : String.format("PAY-%03d", i);
            String amount = i == 17 ? "" : "500.00";
            csv.append(paymentNo).append(",EXP-").append(i).append(",KY-001,")
                    .append(amount).append(",CNY,2026-11-17,PZ-001\n");
        }

        List<PaymentImportRow> rows = parser.parse(
                csv.toString().getBytes(StandardCharsets.UTF_8), "CSV");

        assertThat(rows).hasSize(100);
        assertThat(rows.get(16).errorCode())
                .isEqualTo(PaymentImportFileParser.ERROR_AMOUNT_INVALID);
        assertThat(rows.get(32).errorCode())
                .isEqualTo(PaymentImportFileParser.ERROR_DUPLICATE_PAYMENT_NO);
        assertThat(rows.stream().filter(PaymentImportRow::valid).count()).isEqualTo(98);
    }

    @Test
    void illegalCurrencyMarkedInvalid() {
        String csv = HEADER + "\nPAY-001,EXP-001,KY-001,500.00,USD,2026-11-17,PZ-001\n";

        List<PaymentImportRow> rows = parser.parse(
                csv.getBytes(StandardCharsets.UTF_8), "CSV");

        assertThat(rows).singleElement()
                .satisfies(row -> assertThat(row.errorCode())
                        .isEqualTo(PaymentImportFileParser.ERROR_CURRENCY_INVALID));
    }

    @Test
    void missingHeaderRejectedAsWholeFile() {
        String csv = "external_payment_no,amount\nPAY-001,500.00\n";

        assertThatThrownBy(() -> parser.parse(csv.getBytes(StandardCharsets.UTF_8), "CSV"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_422);
    }

    @Test
    void parsesXlsxFirstSheet() throws Exception {
        byte[] xlsx = xlsx(new String[]{HEADER,
                "PAY-101,EXP-101,KY-001,1500.00,CNY,2026-11-17T09:30:00+08:00,PZ-101"});

        List<PaymentImportRow> rows = parser.parse(xlsx, "EXCEL");

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.valid()).isTrue();
            assertThat(row.externalPaymentNo()).isEqualTo("PAY-101");
            assertThat(row.amount()).isEqualByComparingTo("1500.00");
        });
    }

    private static byte[] xlsx(String[] lines) throws Exception {
        StringBuilder shared = new StringBuilder(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        StringBuilder sheet = new StringBuilder(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        int index = 0;
        for (int r = 0; r < lines.length; r++) {
            sheet.append("<row r=\"").append(r + 1).append("\">");
            String[] cells = lines[r].split(",", -1);
            for (int c = 0; c < cells.length; c++) {
                sheet.append("<c r=\"").append((char) ('A' + c)).append(r + 1)
                        .append("\" t=\"s\"><v>").append(index).append("</v></c>");
                shared.append("<si><t>").append(cells[c]).append("</t></si>");
                index++;
            }
            sheet.append("</row>");
        }
        shared.append("</sst>");
        sheet.append("</sheetData></worksheet>");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
            zip.write(shared.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            zip.write(sheet.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
