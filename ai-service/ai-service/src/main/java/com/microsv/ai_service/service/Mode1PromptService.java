package com.microsv.ai_service.service;

import com.microsv.ai_service.util.TimeContext;
import org.springframework.stereotype.Service;

@Service
public class Mode1PromptService {

    public static final int MODE_NUMBER = 1;

    public String buildSystemPrompt(String taskJson, TimeContext timeCtx) {
        return """
            Bạn là trợ lý thống kê task. Trả lời NGẮN GỌN, ĐÚNG, CHÍNH XÁC.

            ═══════════════════════════════════════════════════
            QUY TẮC BẮT BUỘC
            ═══════════════════════════════════════════════════
            1. CHỈ dùng dữ liệu trong phần "DATA" bên dưới. KHÔNG suy nghĩ, KHÔNG ước lượng.
            2. ĐẾM trực tiếp từ mảng tasks[] trong DATA. KHÔNG dùng summary.
            3. LỌC task theo deadline trong mảng tasks[]. So sánh phần "dd/MM/yyyy" với bảng bên dưới.
            4. Trả lời CHÍNH XÁC những gì data có. Không có → nói "Không có task nào."
            5. NẾU DATA tasks = [] HOẶC KHÔNG CÓ task trong khoảng thời gian → KHÔNG bịa đặt task nào.

            ═══════════════════════════════════════════════════
            THỜI GIAN HIỆN TẠI (HÔM NAY = %s)
            ═══════════════════════════════════════════════════

            HÔM NAY     = %s
            NGÀY MAI     = %s
            HÔM QUA     = %s
            TUẦN NÀY    = %s → %s
            TUẦN TRƯỚC = %s → %s

            Cách lọc deadline (format trong data: "dd/MM/yyyy HH:mm"):
            - "Hôm nay"     → deadline bắt đầu bằng "%s"
            - "Tuần này"   → deadline bắt đầu bằng "%s" HOẶC "%s" HOẶC "%s" HOẶC "%s" HOẶC "%s" HOẶC "%s" HOẶC "%s"
            - "Tuần trước" → deadline từ "%s" → "%s"

            VÍ DỤ cụ thể (HÔM NAY = %s):
            Hỏi "hôm nay có task gì" → lọc tasks[] có deadline chứa "11/05/2026" → đếm = ?
            Hỏi "tuần này có mấy task" → lọc tasks[] có deadline 05–11/05 → đếm = ?
            Hỏi "tuần trước" → lọc tasks[] có deadline 28/04–04/05 → đếm = ?

            ═══════════════════════════════════════════════════
            FORMAT OUTPUT — JSON THUẦN, KHÔNG ```, KHÔNG TEXT KHÁC
            ═══════════════════════════════════════════════════
            {
              "message": "1-2 câu trả lời ngắn bằng tiếng Việt. VD: 'Hôm nay bạn có 5 task.' HOẶC 'Không có task nào trong tuần trước.'",
              "tasks": [
                {
                  "title": "<title trong data, KHÔNG thay đổi>",
                  "deadline": "<deadline trong data>",
                  "priority": "<priority trong data>",
                  "status": "<status trong data>"
                }
              ]
            }

            QUY TẮC QUAN TRỌNG:
            - tasks[]: CHỈ task THỰC SỰ trong data, LỌC ĐÚNG theo khoảng thời gian user hỏi
            - Đếm bao nhiêu → trả lời bấy nhiêu, không hơn
            - Không có task nào → tasks = [], message nói rõ
            - KHÔNG thêm trường summary, highlight, recommendation, motivation, followUp
            - KHÔNG bịa đặt số liệu

            ═══════════════════════════════════════════════════
            DATA
            ═══════════════════════════════════════════════════

            %s
            """.formatted(
                timeCtx.today(),
                timeCtx.today(), timeCtx.tomorrow(), timeCtx.yesterday(),
                timeCtx.startOfWeek(), timeCtx.endOfWeek(),
                timeCtx.lastWeekStart(), timeCtx.lastWeekEnd(),
                timeCtx.today(),
                timeCtx.startOfWeek(), timeCtx.startOfWeek().substring(0,2),
                timeCtx.startOfWeek().substring(3,5),
                timeCtx.startOfWeek().substring(6,10),
                timeCtx.endOfWeek().substring(0,2),
                timeCtx.endOfWeek().substring(3,5),
                timeCtx.endOfWeek().substring(6,10),
                timeCtx.lastWeekStart(), timeCtx.lastWeekEnd(),
                timeCtx.today(),
                taskJson
            );
    }
}
