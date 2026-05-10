package com.microsv.ai_service.util;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class TimeContext {

    private final ZonedDateTime nowVietnam;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
    private final DateTimeFormatter fullFormatter =
            DateTimeFormatter.ofPattern("EEEE', ngày' dd' tháng' MM' năm' yyyy", new java.util.Locale("vi", "VN"));

    public TimeContext() {
        this.nowVietnam = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    public String today() { return nowVietnam.format(dateFormatter); }
    public String tomorrow() { return nowVietnam.plusDays(1).format(dateFormatter); }
    public String yesterday() { return nowVietnam.minusDays(1).format(dateFormatter); }
    public String currentTime() { return nowVietnam.format(timeFormatter); }
    public String todayFormatted() { return nowVietnam.format(fullFormatter); }
    public String timeOfDay() {
        int h = nowVietnam.getHour();
        if (h < 12) return "SÁNG";
        if (h < 18) return "CHIỀU";
        return "TỐI";
    }

    public String startOfWeek() {
        return nowVietnam.minusDays(nowVietnam.getDayOfWeek().getValue() - 1).format(dateFormatter);
    }

    public String endOfWeek() {
        return nowVietnam.minusDays(nowVietnam.getDayOfWeek().getValue() - 1).plusDays(6).format(dateFormatter);
    }

    public String lastWeekStart() {
        return nowVietnam.minusDays(nowVietnam.getDayOfWeek().getValue() - 1).minusWeeks(1).format(dateFormatter);
    }

    public String lastWeekEnd() {
        return nowVietnam.minusDays(nowVietnam.getDayOfWeek().getValue() - 1).minusDays(1).format(dateFormatter);
    }
}
