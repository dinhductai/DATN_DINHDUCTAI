-- ============================================================
-- Script xóa toàn bộ dữ liệu trong bảng push_subscriptions
-- KHÔNG đụng gì vào các bảng khác
-- ============================================================
-- Chạy script này TRƯỚC KHI restart app (trước khi bảng được rename thành notifications)

DELETE FROM push_subscriptions;

-- Verify
SELECT COUNT(*) AS remaining_rows FROM push_subscriptions;
