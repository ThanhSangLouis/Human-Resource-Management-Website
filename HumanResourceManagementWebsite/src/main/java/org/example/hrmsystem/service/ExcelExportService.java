package org.example.hrmsystem.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.hrmsystem.dto.AttendanceHistoryRow;
import org.example.hrmsystem.dto.EmployeeResponse;
import org.example.hrmsystem.model.AttendanceStatus;
import org.example.hrmsystem.model.Employee;
import org.example.hrmsystem.model.SalaryHistory;
import org.example.hrmsystem.repository.EmployeeRepository;
import org.example.hrmsystem.repository.SalaryHistoryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ExcelExportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final EmployeeService employeeService;
    private final AttendanceService attendanceService;
    private final SalaryHistoryRepository salaryHistoryRepository;
    private final EmployeeRepository employeeRepository;

    public ExcelExportService(
            EmployeeService employeeService,
            AttendanceService attendanceService,
            SalaryHistoryRepository salaryHistoryRepository,
            EmployeeRepository employeeRepository
    ) {
        this.employeeService = employeeService;
        this.attendanceService = attendanceService;
        this.salaryHistoryRepository = salaryHistoryRepository;
        this.employeeRepository = employeeRepository;
    }

    /**
     * Xuất danh sách nhân viên theo filter ra file Excel (.xlsx).
     * Hỗ trợ tiếng Việt nhờ font mặc định của POI (Calibri) và UTF-8 nội tại OOXML.
     */
    public byte[] exportEmployees(String keyword, String status, Long departmentId) throws IOException {
        // Lấy toàn bộ dữ liệu (size=10000 để bao phủ hết, sort fullName asc)
        Pageable all = PageRequest.of(0, 10_000, Sort.by(Sort.Direction.ASC, "fullName"));
        List<EmployeeResponse> employees = employeeService.findAll(keyword, status, departmentId, all)
                .getContent();

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Danh sách nhân viên");

            // ── Column widths ────────────────────────────────────────────────
            sheet.setColumnWidth(0, 5  * 256);   // STT
            sheet.setColumnWidth(1, 14 * 256);   // Mã NV
            sheet.setColumnWidth(2, 28 * 256);   // Họ tên
            sheet.setColumnWidth(3, 30 * 256);   // Email
            sheet.setColumnWidth(4, 16 * 256);   // Điện thoại
            sheet.setColumnWidth(5, 22 * 256);   // Chức danh
            sheet.setColumnWidth(6, 22 * 256);   // Phòng ban
            sheet.setColumnWidth(7, 14 * 256);   // Trạng thái
            sheet.setColumnWidth(8, 16 * 256);   // Ngày vào làm
            sheet.setColumnWidth(9, 18 * 256);   // Lương cơ bản

            // ── Title row ────────────────────────────────────────────────────
            CellStyle titleStyle = createTitleStyle(wb);
            Row titleRow = sheet.createRow(0);
            titleRow.setHeightInPoints(24);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("DANH SÁCH NHÂN VIÊN – HRM SYSTEM");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 9));

            // ── Sub-title: export date + filter ──────────────────────────────
            CellStyle subStyle = createSubTitleStyle(wb);
            Row subRow = sheet.createRow(1);
            String filterNote = buildFilterNote(keyword, status, departmentId);
            Cell subCell = subRow.createCell(0);
            subCell.setCellValue("Xuất ngày: " + LocalDate.now().format(DATE_FMT) + filterNote);
            subCell.setCellStyle(subStyle);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 9));

            // ── Header row ───────────────────────────────────────────────────
            CellStyle headerStyle = createHeaderStyle(wb);
            Row headerRow = sheet.createRow(3);
            headerRow.setHeightInPoints(18);
            String[] headers = {
                "STT", "Mã NV", "Họ và tên", "Email", "Điện thoại",
                "Chức danh", "Phòng ban", "Trạng thái", "Ngày vào làm", "Lương cơ bản (VNĐ)"
            };
            for (int i = 0; i < headers.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            // ── Data rows ────────────────────────────────────────────────────
            CellStyle dataStyle   = createDataStyle(wb, false);
            CellStyle dataAlt     = createDataStyle(wb, true);
            CellStyle numStyle    = createNumberStyle(wb);
            CellStyle badgeActive = createBadgeStyle(wb, new byte[]{(byte)39,(byte)103,(byte)73});
            CellStyle badgeInactive = createBadgeStyle(wb, new byte[]{(byte)180,(byte)83,(byte)9});
            CellStyle badgeResigned = createBadgeStyle(wb, new byte[]{(byte)153,(byte)27,(byte)27});

            int rowIdx = 4;
            for (int i = 0; i < employees.size(); i++) {
                EmployeeResponse e = employees.get(i);
                Row row = sheet.createRow(rowIdx++);
                row.setHeightInPoints(16);

                CellStyle base = (i % 2 == 0) ? dataStyle : dataAlt;

                createCell(row, 0, String.valueOf(i + 1), base);
                createCell(row, 1, nullSafe(e.getEmployeeCode()), base);
                createCell(row, 2, nullSafe(e.getFullName()), base);
                createCell(row, 3, nullSafe(e.getEmail()), base);
                createCell(row, 4, nullSafe(e.getPhone()), base);
                createCell(row, 5, nullSafe(e.getPosition()), base);
                createCell(row, 6, nullSafe(e.getDepartmentName()), base);

                // Status badge
                String st = nullSafe(e.getStatus());
                CellStyle stStyle = switch (st) {
                    case "ACTIVE"   -> badgeActive;
                    case "INACTIVE" -> badgeInactive;
                    case "RESIGNED" -> badgeResigned;
                    default         -> base;
                };
                String stLabel = switch (st) {
                    case "ACTIVE"   -> "Đang làm";
                    case "INACTIVE" -> "Tạm nghỉ";
                    case "RESIGNED" -> "Đã nghỉ";
                    default         -> st;
                };
                createCell(row, 7, stLabel, stStyle);

                createCell(row, 8,
                        e.getHireDate() != null ? e.getHireDate().format(DATE_FMT) : "", base);

                // Lương cơ bản – số
                Cell salCell = row.createCell(9);
                if (e.getSalaryBase() != null) {
                    salCell.setCellValue(e.getSalaryBase().doubleValue());
                    salCell.setCellStyle(numStyle);
                } else {
                    salCell.setCellValue("—");
                    salCell.setCellStyle(base);
                }
            }

            // ── Tổng cộng ────────────────────────────────────────────────────
            CellStyle totalStyle = createTotalStyle(wb);
            Row totalRow = sheet.createRow(rowIdx);
            Cell totalLabel = totalRow.createCell(0);
            totalLabel.setCellValue("Tổng: " + employees.size() + " nhân viên");
            totalLabel.setCellStyle(totalStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, 0, 9));

            // ── Xuất ra byte[] ────────────────────────────────────────────────
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    /** US18 — Chấm công theo tháng (filter tùy chọn). */
    public byte[] exportAttendanceMonth(String month, Long departmentId, String status) throws java.io.IOException {
        AttendanceStatus st = null;
        if (status != null && !status.isBlank()) {
            st = AttendanceStatus.valueOf(status.trim().toUpperCase());
        }
        List<AttendanceHistoryRow> rows = attendanceService.listForExport(month, departmentId, st);
        YearMonth ym = YearMonth.parse(month.trim());

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Chấm công");
            sheet.setColumnWidth(0, 6 * 256);
            sheet.setColumnWidth(1, 10 * 256);
            sheet.setColumnWidth(2, 24 * 256);
            sheet.setColumnWidth(3, 12 * 256);
            sheet.setColumnWidth(4, 18 * 256);
            sheet.setColumnWidth(5, 18 * 256);
            sheet.setColumnWidth(6, 10 * 256);
            sheet.setColumnWidth(7, 10 * 256);
            sheet.setColumnWidth(8, 14 * 256);
            sheet.setColumnWidth(9, 36 * 256);

            CellStyle titleStyle = createTitleStyle(wb);
            Row titleRow = sheet.createRow(0);
            Cell tc = titleRow.createCell(0);
            tc.setCellValue("BÁO CÁO CHẤM CÔNG – " + ym);
            tc.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 9));

            CellStyle subStyle = createSubTitleStyle(wb);
            Row sub = sheet.createRow(1);
            String f = "Tháng: " + ym;
            if (departmentId != null) f += " | Phòng ban ID: " + departmentId;
            if (st != null) f += " | Trạng thái: " + st;
            Cell sc = sub.createCell(0);
            sc.setCellValue(f);
            sc.setCellStyle(subStyle);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 9));

            CellStyle headerStyle = createHeaderStyle(wb);
            Row hr = sheet.createRow(3);
            String[] headers = {"STT", "Mã NV", "Họ tên", "Ngày", "Vào", "Ra", "Giờ LV", "Tăng ca", "Trạng thái", "Ghi chú"};
            for (int i = 0; i < headers.length; i++) {
                Cell c = hr.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            CellStyle dataStyle = createDataStyle(wb, false);
            CellStyle dataAlt = createDataStyle(wb, true);
            CellStyle numStyle = createNumberStyle(wb);
            int r = 4;
            for (int i = 0; i < rows.size(); i++) {
                AttendanceHistoryRow row = rows.get(i);
                Row excelRow = sheet.createRow(r++);
                CellStyle base = (i % 2 == 0) ? dataStyle : dataAlt;
                createCell(excelRow, 0, String.valueOf(i + 1), base);
                createCell(excelRow, 1, String.valueOf(row.getEmployeeId()), base);
                createCell(excelRow, 2, nullSafe(row.getEmployeeName()), base);
                createCell(excelRow, 3, row.getAttendanceDate() != null ? row.getAttendanceDate().format(DATE_FMT) : "", base);
                createCell(excelRow, 4, row.getCheckIn() != null ? row.getCheckIn().format(DT_FMT) : "", base);
                createCell(excelRow, 5, row.getCheckOut() != null ? row.getCheckOut().format(DT_FMT) : "", base);
                Cell wh = excelRow.createCell(6);
                if (row.getWorkHours() != null) {
                    wh.setCellValue(row.getWorkHours().doubleValue());
                    wh.setCellStyle(numStyle);
                } else {
                    wh.setCellValue("");
                    wh.setCellStyle(base);
                }
                Cell ot = excelRow.createCell(7);
                if (row.getOvertimeHours() != null) {
                    ot.setCellValue(row.getOvertimeHours().doubleValue());
                    ot.setCellStyle(numStyle);
                } else {
                    ot.setCellValue("");
                    ot.setCellStyle(base);
                }
                createCell(excelRow, 8, row.getStatus() != null ? row.getStatus().name() : "", base);
                createCell(excelRow, 9, nullSafe(row.getNote()), base);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    /** US18 — Bảng lương theo tháng. */
    public byte[] exportPayrollMonth(String month) throws java.io.IOException {
        YearMonth ym = YearMonth.parse(month.trim());
        LocalDate monthStart = ym.atDay(1);
        List<SalaryHistory> list = salaryHistoryRepository.findBySalaryMonthOrderByEmployeeIdAsc(monthStart);
        Map<Long, Employee> em = employeeRepository.findAllById(
                list.stream().map(SalaryHistory::getEmployeeId).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(Employee::getId, e -> e));

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Bảng lương");
            for (int c = 0; c < 12; c++) {
                sheet.setColumnWidth(c, 14 * 256);
            }
            sheet.setColumnWidth(2, 22 * 256);

            CellStyle titleStyle = createTitleStyle(wb);
            Row titleRow = sheet.createRow(0);
            Cell t = titleRow.createCell(0);
            t.setCellValue("BẢNG LƯƠNG – " + ym);
            t.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 11));

            CellStyle headerStyle = createHeaderStyle(wb);
            Row hr = sheet.createRow(2);
            String[] headers = {
                    "STT", "Mã NV", "Họ tên", "Lương CB", "Thưởng", "Thuế", "BHXH", "Khác", "Thực nhận",
                    "Tháng", "TT thanh toán", "Ghi chú"
            };
            for (int i = 0; i < headers.length; i++) {
                Cell c = hr.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            CellStyle dataStyle = createDataStyle(wb, false);
            CellStyle dataAlt = createDataStyle(wb, true);
            CellStyle numStyle = createNumberStyle(wb);
            int r = 3;
            for (int i = 0; i < list.size(); i++) {
                SalaryHistory sh = list.get(i);
                Employee e = em.get(sh.getEmployeeId());
                Row excelRow = sheet.createRow(r++);
                CellStyle base = (i % 2 == 0) ? dataStyle : dataAlt;
                createCell(excelRow, 0, String.valueOf(i + 1), base);
                createCell(excelRow, 1, e != null ? nullSafe(e.getEmployeeCode()) : "", base);
                createCell(excelRow, 2, e != null ? nullSafe(e.getFullName()) : "—", base);
                putMoney(excelRow, 3, sh.getSalaryBase(), numStyle, base);
                putMoney(excelRow, 4, sh.getBonus(), numStyle, base);
                putMoney(excelRow, 5, sh.getTax(), numStyle, base);
                putMoney(excelRow, 6, sh.getInsurance(), numStyle, base);
                putMoney(excelRow, 7, sh.getOtherDeduction(), numStyle, base);
                putMoney(excelRow, 8, sh.getFinalSalary(), numStyle, base);
                createCell(excelRow, 9, sh.getSalaryMonth() != null ? sh.getSalaryMonth().format(DATE_FMT) : "", base);
                createCell(excelRow, 10, sh.getPaymentStatus() != null ? sh.getPaymentStatus().name() : "", base);
                createCell(excelRow, 11, nullSafe(sh.getNote()), base);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private void putMoney(Row row, int col, java.math.BigDecimal v, CellStyle numStyle, CellStyle base) {
        Cell c = row.createCell(col);
        if (v != null) {
            c.setCellValue(v.doubleValue());
            c.setCellStyle(numStyle);
        } else {
            c.setCellValue("");
            c.setCellStyle(base);
        }
    }

    // ── Style helpers ────────────────────────────────────────────────────────

    private CellStyle createTitleStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 14);
        f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        return s;
    }

    private CellStyle createSubTitleStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setItalic(true);
        f.setFontHeightInPoints((short) 10);
        f.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        return s;
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 11);
        f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorderAll(s, BorderStyle.THIN, IndexedColors.WHITE.getIndex());
        s.setWrapText(true);
        return s;
    }

    private CellStyle createDataStyle(Workbook wb, boolean alt) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setFontHeightInPoints((short) 10);
        s.setFont(f);
        if (alt) {
            s.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        setBorderAll(s, BorderStyle.THIN, IndexedColors.GREY_25_PERCENT.getIndex());
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        return s;
    }

    private CellStyle createNumberStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setFontHeightInPoints((short) 10);
        s.setFont(f);
        DataFormat fmt = wb.createDataFormat();
        s.setDataFormat(fmt.getFormat("#,##0"));
        s.setAlignment(HorizontalAlignment.RIGHT);
        setBorderAll(s, BorderStyle.THIN, IndexedColors.GREY_25_PERCENT.getIndex());
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        return s;
    }

    private CellStyle createBadgeStyle(Workbook wb, byte[] rgb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 10);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        setBorderAll(s, BorderStyle.THIN, IndexedColors.GREY_25_PERCENT.getIndex());
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        return s;
    }

    private CellStyle createTotalStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 10);
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorderAll(s, BorderStyle.THIN, IndexedColors.GREY_25_PERCENT.getIndex());
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        return s;
    }

    private void setBorderAll(CellStyle s, BorderStyle bs, short color) {
        s.setBorderTop(bs);    s.setTopBorderColor(color);
        s.setBorderBottom(bs); s.setBottomBorderColor(color);
        s.setBorderLeft(bs);   s.setLeftBorderColor(color);
        s.setBorderRight(bs);  s.setRightBorderColor(color);
    }

    private void createCell(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private String nullSafe(String v) {
        return v != null ? v : "";
    }

    private String buildFilterNote(String keyword, String status, Long departmentId) {
        StringBuilder sb = new StringBuilder();
        if (keyword != null && !keyword.isBlank())
            sb.append("  |  Tìm kiếm: ").append(keyword.trim());
        if (status != null && !status.isBlank())
            sb.append("  |  Trạng thái: ").append(status.trim());
        if (departmentId != null)
            sb.append("  |  Phòng ban ID: ").append(departmentId);
        return sb.toString();
    }
}
