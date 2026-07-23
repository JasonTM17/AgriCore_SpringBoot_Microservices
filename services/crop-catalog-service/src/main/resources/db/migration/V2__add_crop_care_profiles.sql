CREATE TABLE crop_growth_requirements (
    crop_id                          UUID PRIMARY KEY REFERENCES crops(id),
    irrigation_interval_days_min     INT NOT NULL CHECK (irrigation_interval_days_min > 0),
    irrigation_interval_days_max     INT NOT NULL CHECK (irrigation_interval_days_max >= irrigation_interval_days_min),
    fertilization_interval_days_min  INT NOT NULL CHECK (fertilization_interval_days_min > 0),
    fertilization_interval_days_max  INT NOT NULL CHECK (fertilization_interval_days_max >= fertilization_interval_days_min),
    water_requirement_mm_per_week    NUMERIC(8,2) NOT NULL CHECK (water_requirement_mm_per_week >= 0),
    notes                            TEXT,
    updated_at                       TIMESTAMP NOT NULL
);

CREATE TABLE common_diseases (
    id          UUID PRIMARY KEY,
    crop_id     UUID NOT NULL REFERENCES crops(id),
    code        VARCHAR(64) NOT NULL,
    name        VARCHAR(200) NOT NULL,
    symptoms    TEXT NOT NULL,
    prevention  TEXT NOT NULL,
    treatment   TEXT NOT NULL,
    created_at  TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_common_diseases_crop_code
    ON common_diseases (crop_id, code);

CREATE INDEX idx_common_diseases_crop_name
    ON common_diseases (crop_id, name, id);

CREATE TABLE care_recommendations (
    id            UUID PRIMARY KEY,
    crop_id       UUID NOT NULL REFERENCES crops(id),
    category      VARCHAR(32) NOT NULL CHECK (
        category IN ('IRRIGATION', 'FERTILIZATION', 'PEST_MANAGEMENT', 'PRUNING', 'HARVEST', 'SOIL')
    ),
    title         VARCHAR(200) NOT NULL,
    description   TEXT NOT NULL,
    growth_stage  VARCHAR(64),
    sort_order    INT NOT NULL CHECK (sort_order >= 0),
    created_at    TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_care_recommendations_crop_title
    ON care_recommendations (crop_id, title);

CREATE INDEX idx_care_recommendations_crop_order
    ON care_recommendations (crop_id, sort_order, id);

INSERT INTO crop_growth_requirements (
    crop_id, irrigation_interval_days_min, irrigation_interval_days_max,
    fertilization_interval_days_min, fertilization_interval_days_max,
    water_requirement_mm_per_week, notes, updated_at
) VALUES
('22222222-2222-2222-2222-222222222001', 3, 5, 30, 45, 32.00, 'Giữ ẩm ổn định, tránh úng vùng rễ cà phê.', NOW()),
('22222222-2222-2222-2222-222222222002', 2, 4, 30, 45, 45.00, 'Tăng tưới khi ra hoa và nuôi trái, bắt buộc thoát nước tốt.', NOW()),
('22222222-2222-2222-2222-222222222003', 3, 5, 20, 30, 28.00, 'Giảm tưới trước thu hoạch để tăng độ ngọt của quả.', NOW()),
('22222222-2222-2222-2222-222222222004', 2, 3, 15, 25, 50.00, 'Duy trì mực nước phù hợp theo giai đoạn sinh trưởng của lúa.', NOW()),
('22222222-2222-2222-2222-222222222005', 1, 2, 10, 14, 24.00, 'Tưới nhẹ vào sáng sớm, không để nước đọng trên lá.', NOW()),
('22222222-2222-2222-2222-222222222006', 2, 3, 10, 15, 30.00, 'Tưới tại gốc và giữ độ ẩm đồng đều để hạn chế nứt quả.', NOW()),
('22222222-2222-2222-2222-222222222007', 3, 5, 30, 45, 35.00, 'Phủ gốc trong mùa khô và tránh úng cho hệ rễ hồ tiêu.', NOW());

INSERT INTO common_diseases (
    id, crop_id, code, name, symptoms, prevention, treatment, created_at
) VALUES
('44444444-4444-4444-4444-444444444001', '22222222-2222-2222-2222-222222222001', 'COFFEE_LEAF_RUST', 'Gỉ sắt lá cà phê', 'Đốm vàng cam dạng bột ở mặt dưới lá, lá rụng sớm.', 'Tạo tán thông thoáng, bón cân đối và kiểm tra lá định kỳ.', 'Cách ly vùng bệnh và dùng thuốc được phép theo khuyến cáo chuyên môn.', NOW()),
('44444444-4444-4444-4444-444444444002', '22222222-2222-2222-2222-222222222002', 'DURIAN_PHYTOPHTHORA', 'Thối rễ xì mủ', 'Vỏ thân chảy nhựa nâu, rễ thối và tán cây suy yếu.', 'Làm rãnh thoát nước, vệ sinh vườn và tránh gây vết thương thân.', 'Loại bỏ mô bệnh, cải thiện thoát nước và xử lý theo hướng dẫn bảo vệ thực vật.', NOW()),
('44444444-4444-4444-4444-444444444003', '22222222-2222-2222-2222-222222222003', 'DRAGON_FRUIT_BROWN_SPOT', 'Đốm nâu thanh long', 'Đốm lõm màu nâu lan rộng trên cành và quả.', 'Giữ giàn thông thoáng, thu gom cành bệnh và hạn chế ẩm kéo dài.', 'Cắt bỏ phần nhiễm và áp dụng chế phẩm được đăng ký cho thanh long.', NOW()),
('44444444-4444-4444-4444-444444444004', '22222222-2222-2222-2222-222222222004', 'RICE_BLAST', 'Đạo ôn lúa', 'Vết bệnh hình thoi trên lá, cổ bông có thể bị thắt và gãy.', 'Dùng giống sạch bệnh, gieo mật độ hợp lý và không bón thừa đạm.', 'Khoanh vùng ruộng bệnh và xử lý theo dự báo, khuyến cáo địa phương.', NOW()),
('44444444-4444-4444-4444-444444444005', '22222222-2222-2222-2222-222222222005', 'LETTUCE_DOWNY_MILDEW', 'Sương mai xà lách', 'Vết vàng góc cạnh trên lá, mặt dưới có lớp mốc trắng.', 'Thông gió tốt, tưới buổi sáng và luân canh cây trồng.', 'Loại bỏ lá bệnh, giảm ẩm và sử dụng biện pháp được phép.', NOW()),
('44444444-4444-4444-4444-444444444006', '22222222-2222-2222-2222-222222222006', 'TOMATO_LATE_BLIGHT', 'Mốc sương cà chua', 'Vết úng nâu trên lá và quả, lan nhanh khi thời tiết ẩm mát.', 'Dùng cây giống khỏe, làm giàn thoáng và tránh tưới lên tán.', 'Loại bỏ cây bệnh nặng và xử lý sớm theo khuyến cáo chuyên môn.', NOW()),
('44444444-4444-4444-4444-444444444007', '22222222-2222-2222-2222-222222222007', 'PEPPER_QUICK_WILT', 'Chết nhanh hồ tiêu', 'Lá héo đột ngột, cổ rễ thâm đen và cây chết nhanh.', 'Thoát nước tốt, xử lý đất và không để nguồn bệnh lan theo nước.', 'Cách ly trụ bệnh, tiêu hủy tàn dư và phục hồi đất theo hướng dẫn.', NOW());

INSERT INTO care_recommendations (
    id, crop_id, category, title, description, growth_stage, sort_order, created_at
) VALUES
('55555555-5555-5555-5555-555555555001', '22222222-2222-2222-2222-222222222001', 'PRUNING', 'Tạo tán sau thu hoạch', 'Loại bỏ cành sâu bệnh và cành vô hiệu để tán nhận ánh sáng đồng đều.', 'POST_HARVEST', 10, NOW()),
('55555555-5555-5555-5555-555555555002', '22222222-2222-2222-2222-222222222001', 'FERTILIZATION', 'Bón phân theo năng suất', 'Chia nhỏ lượng phân, điều chỉnh theo phân tích đất và sản lượng mục tiêu.', 'GROWING', 20, NOW()),
('55555555-5555-5555-5555-555555555003', '22222222-2222-2222-2222-222222222002', 'IRRIGATION', 'Giữ ẩm khi ra hoa', 'Theo dõi ẩm đất và tưới đều, không để cây khô hạn rồi tưới đột ngột.', 'FLOWERING', 10, NOW()),
('55555555-5555-5555-5555-555555555004', '22222222-2222-2222-2222-222222222002', 'HARVEST', 'Thu hoạch đúng độ chín', 'Thu quả đủ ngày tuổi, tránh va đập và ghi nhận lô thu hoạch.', 'READY_FOR_HARVEST', 20, NOW()),
('55555555-5555-5555-5555-555555555005', '22222222-2222-2222-2222-222222222003', 'PRUNING', 'Tỉa cành sau lứa quả', 'Giữ cành khỏe, loại bỏ cành già và vệ sinh dụng cụ giữa các trụ.', 'POST_HARVEST', 10, NOW()),
('55555555-5555-5555-5555-555555555006', '22222222-2222-2222-2222-222222222003', 'IRRIGATION', 'Điều tiết nước trước thu hoạch', 'Giảm lượng nước có kiểm soát và không để cây héo kéo dài.', 'FRUITING', 20, NOW()),
('55555555-5555-5555-5555-555555555007', '22222222-2222-2222-2222-222222222004', 'FERTILIZATION', 'Quản lý đạm cân đối', 'Bón theo bảng so màu lá và điều kiện ruộng, tránh bón thừa đạm.', 'GROWING', 10, NOW()),
('55555555-5555-5555-5555-555555555008', '22222222-2222-2222-2222-222222222004', 'PEST_MANAGEMENT', 'Thăm đồng định kỳ', 'Kiểm tra theo tuyến cố định, ghi nhận mật độ sâu bệnh trước khi xử lý.', 'GROWING', 20, NOW()),
('55555555-5555-5555-5555-555555555009', '22222222-2222-2222-2222-222222222005', 'IRRIGATION', 'Tưới nhẹ buổi sáng', 'Giữ ẩm vùng rễ và để bề mặt lá khô nhanh sau tưới.', 'GROWING', 10, NOW()),
('55555555-5555-5555-5555-555555555010', '22222222-2222-2222-2222-222222222005', 'HARVEST', 'Thu lá đạt kích thước', 'Thu vào thời điểm mát và làm lạnh sớm để giữ độ giòn.', 'READY_FOR_HARVEST', 20, NOW()),
('55555555-5555-5555-5555-555555555011', '22222222-2222-2222-2222-222222222006', 'PRUNING', 'Tỉa chồi và lá gốc', 'Loại chồi vô hiệu, giữ tán thông thoáng và buộc thân chắc chắn.', 'GROWING', 10, NOW()),
('55555555-5555-5555-5555-555555555012', '22222222-2222-2222-2222-222222222006', 'FERTILIZATION', 'Bổ sung dinh dưỡng nuôi quả', 'Chia nhỏ dinh dưỡng kali và canxi theo phân tích đất, nước.', 'FRUITING', 20, NOW()),
('55555555-5555-5555-5555-555555555013', '22222222-2222-2222-2222-222222222007', 'SOIL', 'Duy trì lớp phủ gốc', 'Phủ hữu cơ cách cổ rễ, bổ sung vật liệu khi lớp phủ phân hủy.', 'GROWING', 10, NOW()),
('55555555-5555-5555-5555-555555555014', '22222222-2222-2222-2222-222222222007', 'PEST_MANAGEMENT', 'Kiểm tra gốc sau mưa', 'Phát hiện sớm úng, thối rễ và xử lý nguồn nước chảy từ trụ bệnh.', 'GROWING', 20, NOW());
