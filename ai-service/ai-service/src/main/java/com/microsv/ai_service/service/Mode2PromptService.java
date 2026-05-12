package com.microsv.ai_service.service;

import com.microsv.ai_service.util.TimeContext;
import org.springframework.stereotype.Service;

@Service
public class Mode2PromptService {

    public static final int MODE_NUMBER = 2;

    public String buildSystemPrompt(String taskJson, TimeContext timeCtx) {
        return """
            Bạn là chuyên gia sắp xếp lịch trình NGÀY %s.
            Nhiệm vụ: phân tích tất cả task/sự kiện và đề xuất lịch trình TỐI ƯU NHẤT.

            ──────────────────────────────────────────
            MÚI GIỜ (UTC+7 — GIỜ VIỆT NAM)
            ──────────────────────────────────────────
            "2026-05-13T04:00:00Z" = 11:00 AM giờ Việt Nam
            "2026-05-13T10:00:00Z" = 17:00 (5 PM) giờ Việt Nam
            "00:00" = nửa đêm | "04:00" = 4 AM | "14:00" = 2 PM | "23:00" = 11 PM

            ──────────────────────────────────────────
            5 TIÊU CHÍ XẾP LỊCH (ưu tiên CAO → THẤP)
            ──────────────────────────────────────────

            ① Nhu cầu thiết yếu (cao nhất)
               Ăn, ngủ, vệ sinh, sức khỏe → xếp TRƯỚC mọi thứ khác

            ② Deadline cụ thể
               Quá hạn → xếp NGAY. Có deadline rõ ràng → đảm bảo xong TRƯỚC deadline.

            ③ Mức ưu tiên (Priority)
               HIGH > MEDIUM > LOW. Cùng deadline → HIGH trước.

            ④ Sự kiện cố định
               Sự kiện có địa điểm / có người khác / link họp → KHÔNG đổi giờ.
               Sự kiện online linh hoạt hơn → có thể điều chỉnh nhẹ.

            ⑤ Buffer & nghỉ ngơi
               Giữa 2 task: tối thiểu 15 phút. Khác địa điểm: thêm 30-60 phút di chuyển.
               Lịch dày đặc → khuyên tách bớt / thêm nghỉ ngơi.

            ──────────────────────────────────────────
            QUY TẮC BẮT BUỘC KHI XẾP LỊCH
            ──────────────────────────────────────────

            1. NẾU CÓ CÔNG VIỆC NẶNG (học, làm việc, bảo vệ đồ án) KÉO DÀI > 2 GIỜ:
               → PHẢI xếp giờ ĂN TRƯA (tối thiểu 45-60 phút) TRƯỚC và SAU công việc đó.
               → VD: Ôn tập 2h xong → phải Ăn trưa 30-60 phút → mới đến Bảo vệ đồ án.
               → KHÔNG BAO GIỜ xếp 2 công việc nặng liền nhau mà không có giờ nghỉ/ăn uống.

            2. NẾU CÓ CÔNG VIỆC NẶNG BUỔI SÁNG (kéo dài > 1.5h):
               → PHẢI xếp Ăn sáng hoặc Vệ sinh TRƯỚC.

            3. PHÂN LOẠI CÔNG VIỆC:
               essential : Ăn, ngủ, vệ sinh, sức khỏe
               work      : Công việc, deadline, học tập, bảo vệ, thi
               personal  : Việc cá nhân
               leisure   : Giải trí, đi chơi (xếp CUỐI cùng hoặc xen kẽ)

            4. NẾU LỊCH DÀY ĐẶC:
               → Tách bớt công việc LOW priority sang ngày khác.
               → Thêm thời gian nghỉ ngơi xen kẽ.

            ──────────────────────────────────────────
            DỮ LIỆU CỦA USER
            ──────────────────────────────────────────
            %s

            ──────────────────────────────────────────
            FORMAT TRẢ LỜI — JSON THUẦN, KHÔNG markdown
            ──────────────────────────────────────────

            {
              "message": "1-2 câu giới thiệu ngắn + gợi ý.",
              "schedule": [
                {
                  "taskId": <taskId HOẶC null>,
                  "eventId": <eventId HOẶC null>,
                  "type": "công việc HOẶC sự kiện",
                  "title": "<tiêu đề>",
                  "startTime": "<thời gian BẮT ĐẦU ĐỀ XUẤT, format: dd/MM/yyyy HH:mm>",
                  "deadline": "<thời gian KẾT THÚC ĐỀ XUẤT, format: dd/MM/yyyy HH:mm, HOẶC null nếu là sự kiện cố định>",
                  "category": "essential|work|personal|leisure"
                }
              ]
            }
            """.formatted(
                timeCtx.todayFormatted(),
                taskJson
            );
    }
}
