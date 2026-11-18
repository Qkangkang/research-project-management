package cn.edu.rpm.funding.domain.service;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.funding.domain.model.PaymentImportRow;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 支付导入文件解析器（T-FUND-033；技术文档 05 §3.4）。
 *
 * <p>支持 CSV（UTF-8）与 XLSX（第一个工作表）。解析器只做表头映射、字段格式与
 * 文件内重复单号校验，输出 {@link PaymentImportRow}，不写任何业务表。
 * 表头缺失抛 {@code RPM-COMMON-422}；字段错误写入行级 {@code errorCode}。</p>
 */
@Component
public class PaymentImportFileParser {

    /** 错误码：必填字段缺失。 */
    public static final String ERROR_FIELD_MISSING = "IMPORT_FIELD_MISSING";

    /** 错误码：金额非法。 */
    public static final String ERROR_AMOUNT_INVALID = "IMPORT_AMOUNT_INVALID";

    /** 错误码：币种非法。 */
    public static final String ERROR_CURRENCY_INVALID = "IMPORT_CURRENCY_INVALID";

    /** 错误码：支付时间非法。 */
    public static final String ERROR_DATE_INVALID = "IMPORT_DATE_INVALID";

    /** 错误码：凭证号缺失。 */
    public static final String ERROR_VOUCHER_MISSING = "IMPORT_VOUCHER_MISSING";

    /** 错误码：文件内外部支付单号重复。 */
    public static final String ERROR_DUPLICATE_PAYMENT_NO = "IMPORT_DUPLICATE_PAYMENT_NO";

    private static final String COL_PAYMENT_NO = "external_payment_no";
    private static final String COL_EXPENSE_NO = "external_expense_no";
    private static final String COL_PROJECT_NO = "project_no";
    private static final String COL_AMOUNT = "amount";
    private static final String COL_CURRENCY = "currency";
    private static final String COL_PAID_AT = "paid_at";
    private static final String COL_VOUCHER = "voucher_no";

    private static final Set<String> REQUIRED_HEADERS = Set.of(
            COL_PAYMENT_NO, COL_EXPENSE_NO, COL_PROJECT_NO, COL_AMOUNT,
            COL_CURRENCY, COL_PAID_AT, COL_VOUCHER);

    private static final ZoneOffset CHINA_OFFSET = ZoneOffset.ofHours(8);

    private final ObjectMapper objectMapper;

    /**
     * 构造解析器。
     *
     * @param objectMapper JSON 序列化器（保存原始行）
     */
    public PaymentImportFileParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析导入文件。
     *
     * @param content    文件内容
     * @param sourceType 来源类型 {@code CSV}/{@code EXCEL}
     * @return 行解析结果（含行级错误）
     * @throws BizException 文件为空、表头缺失或格式无法解析 {@code RPM-COMMON-422}
     */
    public List<PaymentImportRow> parse(byte[] content, String sourceType) {
        if (content == null || content.length == 0) {
            throw new BizException(ErrorCode.COMMON_422, "导入文件不能为空");
        }
        List<List<String>> matrix = parseMatrix(content, sourceType);
        if (matrix.isEmpty()) {
            throw new BizException(ErrorCode.COMMON_422, "导入文件缺少表头");
        }
        Map<String, Integer> headerIndex = headerIndex(matrix.get(0));
        if (!headerIndex.keySet().containsAll(REQUIRED_HEADERS)) {
            throw new BizException(ErrorCode.COMMON_422, "导入文件表头缺失必填列");
        }
        List<PaymentImportRow> rows = new ArrayList<>();
        Set<String> seenPaymentNos = new HashSet<>();
        int rowNo = 0;
        for (int i = 1; i < matrix.size(); i++) {
            List<String> cells = matrix.get(i);
            if (allBlank(cells)) {
                continue;
            }
            rowNo++;
            rows.add(toRow(rowNo, cells, headerIndex, seenPaymentNos));
        }
        return rows;
    }

    private PaymentImportRow toRow(int rowNo, List<String> cells,
                                   Map<String, Integer> headerIndex, Set<String> seenPaymentNos) {
        String paymentNo = cell(cells, headerIndex, COL_PAYMENT_NO);
        String expenseNo = cell(cells, headerIndex, COL_EXPENSE_NO);
        String projectNo = cell(cells, headerIndex, COL_PROJECT_NO);
        String amountText = cell(cells, headerIndex, COL_AMOUNT);
        String currency = cell(cells, headerIndex, COL_CURRENCY);
        String paidAtText = cell(cells, headerIndex, COL_PAID_AT);
        String voucherNo = cell(cells, headerIndex, COL_VOUCHER);

        BigDecimal amount = parseAmount(amountText);
        OffsetDateTime paidAt = parsePaidAt(paidAtText);
        String errorCode = null;
        String errorMessage = null;
        if (isBlank(paymentNo) || isBlank(expenseNo) || isBlank(projectNo)) {
            errorCode = ERROR_FIELD_MISSING;
            errorMessage = "外部支付单号、支出单号、项目编号不能为空";
        } else if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            errorCode = ERROR_AMOUNT_INVALID;
            errorMessage = "金额必须为正数且最多两位小数";
        } else if (!"CNY".equalsIgnoreCase(currency)) {
            errorCode = ERROR_CURRENCY_INVALID;
            errorMessage = "一期仅支持 CNY";
        } else if (paidAt == null) {
            errorCode = ERROR_DATE_INVALID;
            errorMessage = "支付时间格式非法";
        } else if (isBlank(voucherNo)) {
            errorCode = ERROR_VOUCHER_MISSING;
            errorMessage = "凭证号不能为空";
        } else if (!seenPaymentNos.add(paymentNo)) {
            errorCode = ERROR_DUPLICATE_PAYMENT_NO;
            errorMessage = "文件内外部支付单号重复：" + paymentNo;
        }

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put(COL_PAYMENT_NO, paymentNo);
        raw.put(COL_EXPENSE_NO, expenseNo);
        raw.put(COL_PROJECT_NO, projectNo);
        raw.put(COL_AMOUNT, amountText);
        raw.put(COL_CURRENCY, currency);
        raw.put(COL_PAID_AT, paidAtText);
        raw.put(COL_VOUCHER, voucherNo);
        return new PaymentImportRow(rowNo, paymentNo, expenseNo, projectNo, amount,
                currency, paidAt, voucherNo, toJson(raw), errorCode, errorMessage);
    }

    private Map<String, Integer> headerIndex(List<String> header) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String name = normalizeHeader(header.get(i));
            if (!name.isEmpty()) {
                index.putIfAbsent(name, i);
            }
        }
        return index;
    }

    private static String normalizeHeader(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static String cell(List<String> cells, Map<String, Integer> index, String column) {
        Integer position = index.get(column);
        if (position == null || position >= cells.size()) {
            return null;
        }
        return cells.get(position);
    }

    private static BigDecimal parseAmount(String text) {
        if (isBlank(text)) {
            return null;
        }
        try {
            BigDecimal amount = new BigDecimal(text.trim().replace(",", ""));
            return amount.scale() > 2 ? null : amount;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static OffsetDateTime parsePaidAt(String text) {
        if (isBlank(text)) {
            return null;
        }
        String value = text.trim();
        try {
            return OffsetDateTime.parse(value);
        } catch (RuntimeException ignored) {
            try {
                return LocalDate.parse(value).atStartOfDay().atOffset(CHINA_OFFSET);
            } catch (RuntimeException ex) {
                return null;
            }
        }
    }

    private String toJson(Map<String, Object> raw) {
        try {
            return objectMapper.writeValueAsString(raw);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.COMMON_422, "导入行序列化失败");
        }
    }

    private static boolean allBlank(List<String> cells) {
        return cells.stream().allMatch(PaymentImportFileParser::isBlank);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static List<List<String>> parseMatrix(byte[] content, String sourceType) {
        String type = sourceType == null ? "CSV" : sourceType.trim().toUpperCase(Locale.ROOT);
        boolean xlsx = "EXCEL".equals(type) || "XLSX".equals(type) || isZip(content);
        return xlsx ? parseXlsx(content) : parseCsv(content);
    }

    private static boolean isZip(byte[] content) {
        return content.length >= 4 && content[0] == 'P' && content[1] == 'K';
    }

    private static List<List<String>> parseCsv(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        List<List<String>> rows = new ArrayList<>();
        for (String line : text.split("\\r?\\n", -1)) {
            if (!line.isBlank()) {
                rows.add(splitCsvLine(line));
            }
        }
        return rows;
    }

    private static List<String> splitCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                cells.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        cells.add(current.toString().trim());
        return cells;
    }

    private static List<List<String>> parseXlsx(byte[] content) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            byte[] sharedStrings = null;
            byte[] sheet = null;
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if ("xl/sharedStrings.xml".equals(name)) {
                    sharedStrings = readAll(zip);
                } else if ("xl/worksheets/sheet1.xml".equals(name)) {
                    sheet = readAll(zip);
                }
            }
            if (sheet == null) {
                throw new BizException(ErrorCode.COMMON_422, "Excel 缺少第一个工作表");
            }
            List<String> strings = sharedStrings == null
                    ? List.of() : parseSharedStrings(sharedStrings);
            return parseSheet(sheet, strings);
        } catch (IOException ex) {
            throw new BizException(ErrorCode.COMMON_422, "Excel 文件无法解析");
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        input.transferTo(output);
        return output.toByteArray();
    }

    private static List<String> parseSharedStrings(byte[] xml) {
        Document doc = parseXml(xml);
        List<String> values = new ArrayList<>();
        NodeList items = doc.getElementsByTagName("si");
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            NodeList texts = item.getElementsByTagName("t");
            StringBuilder value = new StringBuilder();
            for (int j = 0; j < texts.getLength(); j++) {
                value.append(texts.item(j).getTextContent());
            }
            values.add(value.toString());
        }
        return values;
    }

    private static List<List<String>> parseSheet(byte[] xml, List<String> sharedStrings) {
        Document doc = parseXml(xml);
        NodeList rowNodes = doc.getElementsByTagName("row");
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < rowNodes.getLength(); i++) {
            Element row = (Element) rowNodes.item(i);
            NodeList cells = row.getElementsByTagName("c");
            List<String> values = new ArrayList<>();
            for (int j = 0; j < cells.getLength(); j++) {
                Element cell = (Element) cells.item(j);
                int column = columnIndex(cell.getAttribute("r"));
                while (values.size() <= column) {
                    values.add("");
                }
                values.set(column, cellValue(cell, sharedStrings));
            }
            rows.add(values);
        }
        return rows;
    }

    private static String cellValue(Element cell, List<String> sharedStrings) {
        String type = cell.getAttribute("t");
        if ("inlineStr".equals(type)) {
            NodeList texts = cell.getElementsByTagName("t");
            return texts.getLength() == 0 ? "" : texts.item(0).getTextContent();
        }
        NodeList values = cell.getElementsByTagName("v");
        if (values.getLength() == 0) {
            return "";
        }
        String value = values.item(0).getTextContent();
        if ("s".equals(type)) {
            try {
                int index = Integer.parseInt(value);
                return index >= 0 && index < sharedStrings.size() ? sharedStrings.get(index) : "";
            } catch (NumberFormatException ex) {
                return "";
            }
        }
        return value;
    }

    private static int columnIndex(String reference) {
        int index = 0;
        for (int i = 0; i < reference.length(); i++) {
            char ch = reference.charAt(i);
            if (ch < 'A' || ch > 'Z') {
                break;
            }
            index = index * 26 + (ch - 'A' + 1);
        }
        return Math.max(0, index - 1);
    }

    private static Document parseXml(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (Exception ex) {
            throw new BizException(ErrorCode.COMMON_422, "Excel 内容无法解析");
        }
    }
}
