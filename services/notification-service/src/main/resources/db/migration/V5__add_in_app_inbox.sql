CREATE TABLE in_app_deliveries (
    notification_id UUID PRIMARY KEY,
    recipient       VARCHAR(320) NOT NULL,
    subject         VARCHAR(300) NOT NULL,
    body            TEXT NOT NULL,
    delivered_at    TIMESTAMP NOT NULL,
    read_at         TIMESTAMP,
    CONSTRAINT fk_in_app_delivery_notification
        FOREIGN KEY (notification_id) REFERENCES notifications (id)
);

CREATE INDEX ix_in_app_deliveries_recipient_delivered
    ON in_app_deliveries (recipient, delivered_at DESC);
