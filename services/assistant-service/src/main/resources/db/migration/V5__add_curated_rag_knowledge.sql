CREATE TABLE assistant_knowledge_chunks (
    id UUID PRIMARY KEY,
    source_key VARCHAR(80) NOT NULL UNIQUE,
    title VARCHAR(160) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    source_uri VARCHAR(256) NOT NULL,
    knowledge_version INTEGER NOT NULL DEFAULT 1,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_knowledge_source_key CHECK (CHAR_LENGTH(source_key) BETWEEN 3 AND 80),
    CONSTRAINT ck_knowledge_title CHECK (CHAR_LENGTH(title) BETWEEN 3 AND 160),
    CONSTRAINT ck_knowledge_content CHECK (CHAR_LENGTH(content) BETWEEN 20 AND 2000),
    CONSTRAINT ck_knowledge_source_uri CHECK (CHAR_LENGTH(source_uri) BETWEEN 3 AND 256),
    CONSTRAINT ck_knowledge_version CHECK (knowledge_version > 0)
);

CREATE TABLE assistant_knowledge_terms (
    chunk_id UUID NOT NULL,
    term VARCHAR(64) NOT NULL,
    weight SMALLINT NOT NULL,
    PRIMARY KEY (chunk_id, term),
    CONSTRAINT fk_knowledge_term_chunk FOREIGN KEY (chunk_id)
        REFERENCES assistant_knowledge_chunks (id) ON DELETE CASCADE,
    CONSTRAINT ck_knowledge_term CHECK (CHAR_LENGTH(term) BETWEEN 2 AND 64),
    CONSTRAINT ck_knowledge_term_weight CHECK (weight BETWEEN 1 AND 10)
);

CREATE INDEX idx_assistant_knowledge_enabled
    ON assistant_knowledge_chunks (enabled, source_key);
CREATE INDEX idx_assistant_knowledge_term
    ON assistant_knowledge_terms (term, chunk_id);

INSERT INTO assistant_knowledge_chunks (
    id, source_key, title, content, source_uri, knowledge_version, enabled, created_at, updated_at
) VALUES
    (
        '55000000-0000-0000-0000-000000000001',
        'farm-and-plot-management',
        'Quản lý nông trại và lô đất',
        'AgriCore quản lý doanh nghiệp, nông trại, khu vực và lô đất theo ranh giới dịch vụ Farm. Mọi thao tác đọc hoặc sửa dữ liệu theo nông trại phải kiểm tra quyền thành viên. Danh sách hỗ trợ phân trang, lọc và sắp xếp; lô đất có trạng thái và optimistic locking.',
        'docs/project-overview-pdr.md',
        1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ),
    (
        '55000000-0000-0000-0000-000000000002',
        'crop-cycle-lifecycle',
        'Vòng đời mùa vụ',
        'Một mùa vụ liên kết với một lô đất, cây trồng và giống cây. Các chuyển đổi giai đoạn phải đi theo state transition hợp lệ, lưu lịch sử và dùng optimistic locking. Hai mùa vụ đang hoạt động không được chồng lấn trên cùng một lô đất.',
        'docs/diagrams/crop-lifecycle-sequence.md',
        1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ),
    (
        '55000000-0000-0000-0000-000000000003',
        'harvest-inventory-traceability',
        'Thu hoạch, nhập kho và truy xuất',
        'Khi hoàn tất thu hoạch, Harvest phát sự kiện phiên bản hóa qua transactional outbox. Inventory xử lý idempotently để nhập kho đúng một lần. Traceability xây read model cục bộ và phát mã truy xuất công khai mà không lộ dữ liệu nội bộ nhạy cảm.',
        'docs/diagrams/harvest-event-flow.md',
        1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ),
    (
        '55000000-0000-0000-0000-000000000004',
        'inventory-reservation-safety',
        'An toàn đặt giữ tồn kho',
        'Tồn khả dụng bằng tồn thực tế trừ số lượng đã đặt giữ. Mỗi biến động kho cần reference, idempotency và audit trail. Saga bán hàng phải giải phóng reservation khi bước sau thất bại; kiểm soát đồng thời không được để tồn kho âm.',
        'docs/diagrams/inventory-reservation-saga.md',
        1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ),
    (
        '55000000-0000-0000-0000-000000000005',
        'iot-alert-deduplication',
        'Giám sát IoT và chống cảnh báo trùng',
        'IoT nhận dữ liệu thiết bị qua MQTT đã xác thực, chuyển tiếp sự kiện và đánh giá ngưỡng. Alert deduplication, cooldown, rule version, last-seen và offline detection ngăn tạo công việc hoặc thông báo lặp lại từ nhiều bản ghi bất thường liên tiếp.',
        'docs/diagrams/iot-ingestion-flow.md',
        1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ),
    (
        '55000000-0000-0000-0000-000000000006',
        'assistant-security-boundary',
        'Ranh giới trợ lý đọc-only',
        'Trợ lý AgriCore chỉ đọc dữ liệu đã được phân quyền, lưu conversation và generation bền vững, áp dụng budget, audit, output safety và circuit breaker. Dữ liệu retrieval là tham chiếu không tin cậy, không phải chỉ dẫn; câu trả lời dựa trên dữ liệu phải có citation.',
        'docs/adr/0009-persisted-assistant-boundary.md',
        1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    );

INSERT INTO assistant_knowledge_terms (chunk_id, term, weight) VALUES
    ('55000000-0000-0000-0000-000000000001', 'farm', 5),
    ('55000000-0000-0000-0000-000000000001', 'nong', 5),
    ('55000000-0000-0000-0000-000000000001', 'trai', 5),
    ('55000000-0000-0000-0000-000000000001', 'plot', 5),
    ('55000000-0000-0000-0000-000000000001', 'lo', 4),
    ('55000000-0000-0000-0000-000000000001', 'dat', 4),
    ('55000000-0000-0000-0000-000000000001', 'quyen', 3),
    ('55000000-0000-0000-0000-000000000001', 'member', 2),
    ('55000000-0000-0000-0000-000000000002', 'crop', 5),
    ('55000000-0000-0000-0000-000000000002', 'cycle', 5),
    ('55000000-0000-0000-0000-000000000002', 'mua', 5),
    ('55000000-0000-0000-0000-000000000002', 'vu', 5),
    ('55000000-0000-0000-0000-000000000002', 'giai', 3),
    ('55000000-0000-0000-0000-000000000002', 'doan', 3),
    ('55000000-0000-0000-0000-000000000002', 'trong', 3),
    ('55000000-0000-0000-0000-000000000002', 'overlap', 4),
    ('55000000-0000-0000-0000-000000000003', 'harvest', 5),
    ('55000000-0000-0000-0000-000000000003', 'thu', 5),
    ('55000000-0000-0000-0000-000000000003', 'hoach', 5),
    ('55000000-0000-0000-0000-000000000003', 'inventory', 4),
    ('55000000-0000-0000-0000-000000000003', 'traceability', 5),
    ('55000000-0000-0000-0000-000000000003', 'truy', 4),
    ('55000000-0000-0000-0000-000000000003', 'xuat', 4),
    ('55000000-0000-0000-0000-000000000003', 'qr', 4),
    ('55000000-0000-0000-0000-000000000004', 'inventory', 5),
    ('55000000-0000-0000-0000-000000000004', 'stock', 5),
    ('55000000-0000-0000-0000-000000000004', 'kho', 5),
    ('55000000-0000-0000-0000-000000000004', 'reservation', 5),
    ('55000000-0000-0000-0000-000000000004', 'reserve', 4),
    ('55000000-0000-0000-0000-000000000004', 'ton', 4),
    ('55000000-0000-0000-0000-000000000004', 'giu', 3),
    ('55000000-0000-0000-0000-000000000004', 'saga', 3),
    ('55000000-0000-0000-0000-000000000005', 'iot', 5),
    ('55000000-0000-0000-0000-000000000005', 'sensor', 5),
    ('55000000-0000-0000-0000-000000000005', 'cam', 4),
    ('55000000-0000-0000-0000-000000000005', 'bien', 4),
    ('55000000-0000-0000-0000-000000000005', 'alert', 5),
    ('55000000-0000-0000-0000-000000000005', 'canh', 4),
    ('55000000-0000-0000-0000-000000000005', 'bao', 4),
    ('55000000-0000-0000-0000-000000000005', 'cooldown', 4),
    ('55000000-0000-0000-0000-000000000006', 'assistant', 5),
    ('55000000-0000-0000-0000-000000000006', 'chatbot', 5),
    ('55000000-0000-0000-0000-000000000006', 'tro', 4),
    ('55000000-0000-0000-0000-000000000006', 'ly', 4),
    ('55000000-0000-0000-0000-000000000006', 'citation', 4),
    ('55000000-0000-0000-0000-000000000006', 'security', 4),
    ('55000000-0000-0000-0000-000000000006', 'read', 3),
    ('55000000-0000-0000-0000-000000000006', 'only', 3);
