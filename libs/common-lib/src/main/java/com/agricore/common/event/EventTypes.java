package com.agricore.common.event;

/**
 * Well-known event type names (version suffix in type string for clarity).
 */
public final class EventTypes {

    public static final String FARM_CREATED = "FarmCreated.v1";
    public static final String PLOT_CREATED = "PlotCreated.v1";
    public static final String PLOT_STATUS_CHANGED = "PlotStatusChanged.v1";

    public static final String CROP_CREATED = "CropCreated.v1";

    public static final String CROP_CYCLE_CREATED = "CropCycleCreated.v1";
    public static final String CROP_CYCLE_STAGE_CHANGED = "CropCycleStageChanged.v1";
    public static final String CROP_CYCLE_COMPLETED = "CropCycleCompleted.v1";
    public static final String CROP_CYCLE_CANCELLED = "CropCycleCancelled.v1";

    public static final String WORK_TASK_CREATED = "WorkTaskCreated.v1";
    public static final String WORK_TASK_ASSIGNED = "WorkTaskAssigned.v1";
    public static final String WORK_TASK_COMPLETED = "WorkTaskCompleted.v1";

    public static final String MATERIAL_CONSUMED = "MaterialConsumed.v1";
    public static final String INVENTORY_RESERVED = "InventoryReserved.v1";
    public static final String INVENTORY_RESERVATION_FAILED = "InventoryReservationFailed.v1";
    public static final String INVENTORY_RELEASED = "InventoryReleased.v1";
    public static final String STOCK_ADDED = "StockAdded.v1";
    public static final String STOCK_DEDUCTED = "StockDeducted.v1";

    public static final String HARVEST_STARTED = "HarvestStarted.v1";
    public static final String HARVEST_COMPLETED = "HarvestCompleted.v1";
    public static final String HARVEST_BATCH_CREATED = "HarvestBatchCreated.v1";

    public static final String SENSOR_READING_RECEIVED = "SensorReadingReceived.v1";
    public static final String SENSOR_THRESHOLD_EXCEEDED = "SensorThresholdExceeded.v1";
    public static final String DEVICE_OFFLINE_DETECTED = "DeviceOfflineDetected.v1";

    public static final String TRACEABILITY_BATCH_CREATED = "TraceabilityBatchCreated.v1";
    public static final String TRACEABILITY_CODE_GENERATED = "TraceabilityCodeGenerated.v1";

    public static final String SALES_ORDER_CREATED = "SalesOrderCreated.v1";
    public static final String SALES_ORDER_CONFIRMED = "SalesOrderConfirmed.v1";
    public static final String SALES_ORDER_CANCELLED = "SalesOrderCancelled.v1";

    public static final String NOTIFICATION_REQUESTED = "NotificationRequested.v2";
    public static final String NOTIFICATION_SENT = "NotificationSent.v2";
    public static final String NOTIFICATION_FAILED = "NotificationFailed.v2";

    public static final String USER_REGISTERED = "UserRegistered.v1";

    private EventTypes() {
    }
}
