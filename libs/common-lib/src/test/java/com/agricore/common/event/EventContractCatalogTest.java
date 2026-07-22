package com.agricore.common.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class EventContractCatalogTest {

    private static final String JSON_SCHEMA_DIALECT = "https://json-schema.org/draft/2020-12/schema";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final List<Contract> CONTRACTS = List.of(
            contract(EventTypes.FARM_CREATED, "farm-service", "agricore.farm.events",
                    set("farmId", "code", "name", "province", "status")),
            contract(EventTypes.PLOT_CREATED, "farm-service", "agricore.farm.events",
                    set("plotId", "farmId", "code", "name", "status", "areaInHectares")),
            contract(EventTypes.PLOT_STATUS_CHANGED, "farm-service", "agricore.farm.events",
                    set("plotId", "farmId", "code", "name", "status", "areaInHectares", "previousStatus")),
            contract(EventTypes.CROP_CYCLE_CREATED, "crop-cycle-service", "agricore.crop-cycle.events",
                    set("cropCycleId", "code", "farmId", "plotId", "cropId", "stage", "status")),
            contract(EventTypes.CROP_CYCLE_STAGE_CHANGED, "crop-cycle-service", "agricore.crop-cycle.events",
                    set("cropCycleId", "code", "farmId", "plotId", "cropId", "stage", "status", "previousStage")),
            contract(EventTypes.CROP_CYCLE_COMPLETED, "crop-cycle-service", "agricore.crop-cycle.events",
                    set("cropCycleId", "code", "farmId", "plotId", "cropId", "stage", "status", "previousStage")),
            contract(EventTypes.CROP_CYCLE_CANCELLED, "crop-cycle-service", "agricore.crop-cycle.events",
                    set("cropCycleId", "code", "farmId", "plotId", "cropId", "stage", "status", "previousStage")),
            contract(EventTypes.WORK_TASK_CREATED, "work-service", "agricore.work.events",
                    set("taskId", "code", "cropCycleId", "plotId", "taskType", "status")),
            contract(EventTypes.WORK_TASK_ASSIGNED, "work-service", "agricore.work.events",
                    set("taskId", "code", "cropCycleId", "plotId", "taskType", "status", "assignedEmployeeId")),
            new Contract(EventTypes.WORK_TASK_COMPLETED, "work-service", "agricore.work.events",
                    set("taskId", "code", "cropCycleId", "plotId", "taskType", "status"), set("assignedEmployeeId")),
            contract(EventTypes.MATERIAL_CONSUMED, "work-service", "agricore.work.events",
                    set("materialUsageId", "taskId", "cropCycleId", "plotId", "inventoryItemId", "quantity",
                            "unit", "referenceType", "referenceId", "consumedAt")),
            contract(EventTypes.INVENTORY_RESERVED, "inventory-service", "agricore.inventory.events",
                    set("inventoryItemId", "warehouseId", "sku", "itemType", "unit", "reservationId", "quantity",
                            "referenceType", "referenceId", "reservedQuantity", "availableQuantity")),
            contract(EventTypes.INVENTORY_RESERVATION_FAILED, "inventory-service", "agricore.inventory.events",
                    set("inventoryItemId", "warehouseId", "sku", "itemType", "unit", "requestedQuantity",
                            "availableQuantity", "referenceType", "referenceId", "reasonCode")),
            contract(EventTypes.INVENTORY_RELEASED, "inventory-service", "agricore.inventory.events",
                    set("inventoryItemId", "warehouseId", "sku", "itemType", "unit", "reservationId", "quantity",
                            "referenceType", "referenceId", "reservedQuantity", "availableQuantity")),
            contract(EventTypes.STOCK_ADDED, "inventory-service", "agricore.inventory.events",
                    set("inventoryItemId", "warehouseId", "sku", "itemType", "unit", "movementId", "quantity",
                            "referenceType", "referenceId", "onHandQuantity", "availableQuantity")),
            new Contract(EventTypes.STOCK_DEDUCTED, "inventory-service", "agricore.inventory.events",
                    set("inventoryItemId", "warehouseId", "sku", "itemType", "unit", "movementId", "quantity",
                            "referenceType", "referenceId", "onHandQuantity", "availableQuantity"),
                    set("reservationId")),
            contract(EventTypes.HARVEST_BATCH_CREATED, "harvest-service", "agricore.harvest.events",
                    set("harvestId", "harvestBatchId", "cropCycleId", "plotId", "warehouseId", "productCode",
                            "code", "status", "startedAt")),
            contract(EventTypes.HARVEST_STARTED, "harvest-service", "agricore.harvest.events",
                    set("harvestId", "harvestBatchId", "cropCycleId", "plotId", "warehouseId", "productCode",
                            "code", "status", "startedAt")),
            new Contract(EventTypes.HARVEST_COMPLETED, "harvest-service", "agricore.harvest.events",
                    set("harvestId", "harvestBatchId", "cropCycleId", "plotId", "warehouseId", "productCode",
                            "grossWeightKg", "netWeightKg", "qualityGrade", "harvestDate", "productName"),
                    set("farmName", "plotCode", "careSummary")),
            contract(EventTypes.SENSOR_READING_RECEIVED, "iot-service", "agricore.iot.events",
                    set("readingId", "deviceId", "deviceCode", "plotId", "metricType", "metricValue", "unit",
                            "recordedAt")),
            new Contract(EventTypes.SENSOR_THRESHOLD_EXCEEDED, "iot-service", "agricore.iot.events",
                    set("readingId", "deviceId", "deviceCode", "plotId", "metricType", "metricValue", "unit",
                            "recordedAt", "alertId", "severity", "ruleVersion", "message", "detectedAt"),
                    set("minValue", "maxValue")),
            contract(EventTypes.DEVICE_OFFLINE_DETECTED, "iot-service", "agricore.iot.events",
                    set("deviceId", "deviceCode", "plotId", "deviceName", "lastActivityAt", "detectedAt",
                            "offlineAfterSeconds")),
            new Contract(EventTypes.TRACEABILITY_BATCH_CREATED, "traceability-service",
                    "agricore.traceability.events",
                    set("traceabilityBatchId", "traceabilityCode", "harvestBatchId", "productName", "harvestDate",
                            "publicUrl", "batchLabel", "createdAt"),
                    set("cropCycleId", "plotId", "farmName", "plotCode", "varietyName", "plantingDate",
                            "qualityGrade", "netWeightKg", "careSummary")),
            contract(EventTypes.TRACEABILITY_CODE_GENERATED, "traceability-service",
                    "agricore.traceability.events",
                    set("traceabilityBatchId", "traceabilityCode", "publicUrl", "qrImageUrl", "batchLabel",
                            "generatedAt")),
            contract(EventTypes.SALES_ORDER_CREATED, "sales-service", "agricore.sales.events",
                    set("salesOrderId", "orderNumber", "customerId", "inventoryItemId", "quantity", "status", "createdAt")),
            contract(EventTypes.SALES_ORDER_CONFIRMED, "sales-service", "agricore.sales.events",
                    set("salesOrderId", "orderNumber", "customerId", "inventoryItemId", "quantity", "status", "reservationId", "confirmedAt")),
            new Contract(EventTypes.SALES_ORDER_CANCELLED, "sales-service", "agricore.sales.events",
                    set("salesOrderId", "orderNumber", "customerId", "inventoryItemId", "quantity", "status", "finalStatus", "reasonCode", "reason", "cancelledAt"),
                    set("reservationId"))
    );

    @Test
    void schemasExactlyDescribeTheEmittedEventCatalog() throws IOException {
        Path schemaDirectory = projectRoot().resolve("contracts/event-schemas");
        Set<String> expectedFiles = new HashSet<>(Set.of("DomainEventEnvelope.v1.json"));
        CONTRACTS.forEach(contract -> expectedFiles.add(contract.schemaFile()));

        try (var files = Files.list(schemaDirectory)) {
            assertThat(files.filter(path -> path.toString().endsWith(".json"))
                    .map(path -> path.getFileName().toString()).toList())
                    .containsExactlyInAnyOrderElementsOf(expectedFiles);
        }

        JsonNode envelope = MAPPER.readTree(schemaDirectory.resolve("DomainEventEnvelope.v1.json").toFile());
        assertThat(envelope.path("$schema").asText()).isEqualTo(JSON_SCHEMA_DIALECT);
        assertThat(envelope.path("additionalProperties").asBoolean()).isFalse();
        Set<String> envelopeProperties = fieldNames(envelope.path("properties"));

        for (Contract contract : CONTRACTS) {
            JsonNode schema = MAPPER.readTree(schemaDirectory.resolve(contract.schemaFile()).toFile());
            JsonNode specialization = schema.path("allOf").path(1).path("properties");
            JsonNode payload = specialization.path("payload");

            assertThat(schema.path("$schema").asText()).isEqualTo(JSON_SCHEMA_DIALECT);
            assertThat(schema.path("$id").asText()).endsWith("/" + contract.schemaFile());
            assertThat(schema.path("title").asText()).isEqualTo(contract.eventType());
            assertThat(schema.path("allOf").path(0).path("$ref").asText())
                    .isEqualTo("DomainEventEnvelope.v1.json");
            assertThat(fieldNames(specialization)).isSubsetOf(envelopeProperties);
            assertThat(specialization.path("eventType").path("const").asText()).isEqualTo(contract.eventType());
            assertThat(specialization.path("eventVersion").path("const").asInt()).isEqualTo(1);
            assertThat(specialization.path("producer").path("const").asText()).isEqualTo(contract.producer());
            assertThat(textValues(payload.path("required"))).containsExactlyInAnyOrderElementsOf(contract.required());
            assertThat(fieldNames(payload.path("properties")))
                    .containsExactlyInAnyOrderElementsOf(contract.payloadFields());
            assertThat(payload.path("additionalProperties").asBoolean()).isFalse();
            if (contract.eventType().equals(EventTypes.PLOT_STATUS_CHANGED)) {
                assertThat(payload.path("properties").path("name").has("minLength")).isFalse();
            }
        }
    }

    @Test
    void asyncApiParsesAndReferencesOnlyImplementedEvents() throws IOException {
        Path asyncApi = projectRoot().resolve("contracts/asyncapi/agricore-events.yaml");
        JsonNode document = YAML_MAPPER.readTree(asyncApi.toFile());
        JsonNode channels = document.path("channels");
        JsonNode operations = document.path("operations");

        assertThat(document.path("asyncapi").asText()).isEqualTo("3.0.0");
        assertThat(channels.isObject()).isTrue();
        assertThat(operations.isObject()).isTrue();
        for (String ref : references(document)) {
            if (ref.startsWith("#/")) {
                assertThat(document.at(ref.substring(1)).isMissingNode())
                        .as("resolved internal AsyncAPI reference %s", ref)
                        .isFalse();
            } else {
                assertThat(asyncApi.getParent().resolve(ref).normalize()).exists();
            }
        }

        for (Contract contract : CONTRACTS) {
            JsonNode message = channels.path(contract.topic()).path("messages").path(contract.messageKey());
            assertThat(message.isObject()).as("message %s", contract.eventType()).isTrue();
            assertThat(message.path("name").asText()).isEqualTo(contract.eventType());
            assertThat(message.path("payload").path("$ref").asText())
                    .isEqualTo("../event-schemas/" + contract.schemaFile());
        }
        assertThat(versionedMessageNames(channels))
                .containsExactlyInAnyOrderElementsOf(CONTRACTS.stream().map(Contract::eventType).toList());

        assertThat(actionCount(operations, "send")).isEqualTo(10);
        assertThat(actionCount(operations, "receive")).isEqualTo(2);
        assertThat(fieldNames(operations)).contains(
                "inventoryServiceReceivesHarvestCompleted",
                "traceabilityServiceReceivesHarvestCompleted",
                "inventoryServiceSendsHarvestDeadLetters",
                "traceabilityServiceSendsHarvestDeadLetters"
        );
        JsonNode deadLetterChannel = channels.path("agricore.harvest.events.DLT");
        assertThat(deadLetterChannel.path("description").asText()).contains("No Kafka retry topics are implemented");
        assertThat(deadLetterChannel.path("messages").path("HarvestCompletedDeadLetter")
                .path("contentType").asText()).isEqualTo("text/plain");
        assertThat(deadLetterChannel.path("messages").path("HarvestCompletedDeadLetter")
                .path("payload").path("type").asText()).isEqualTo("string");
    }

    private static List<String> references(JsonNode root) {
        List<String> refs = new ArrayList<>();
        collectReferences(root, refs);
        return refs;
    }

    private static void collectReferences(JsonNode node, List<String> refs) {
        if (node.isObject()) {
            node.properties().forEach(field -> {
                if (field.getKey().equals("$ref") && field.getValue().isTextual()) {
                    refs.add(field.getValue().asText());
                } else {
                    collectReferences(field.getValue(), refs);
                }
            });
        } else if (node.isArray()) {
            node.forEach(child -> collectReferences(child, refs));
        }
    }

    private static List<String> versionedMessageNames(JsonNode channels) {
        List<String> names = new ArrayList<>();
        channels.forEach(channel -> channel.path("messages").forEach(message -> {
            String name = message.path("name").asText();
            if (name.matches("[A-Za-z0-9]+\\.v\\d+")) {
                names.add(name);
            }
        }));
        return names;
    }

    private static long actionCount(JsonNode operations, String action) {
        return StreamSupport.stream(operations.spliterator(), false)
                .filter(operation -> action.equals(operation.path("action").asText()))
                .count();
    }

    private static Set<String> fieldNames(JsonNode object) {
        Set<String> names = new HashSet<>();
        object.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static List<String> textValues(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).toList();
    }

    private static Path projectRoot() {
        for (Path current = Path.of("").toAbsolutePath(); current != null; current = current.getParent()) {
            if (Files.isRegularFile(current.resolve("contracts/event-schemas/DomainEventEnvelope.v1.json"))) {
                return current;
            }
        }
        return fail("Could not locate project root from %s", Path.of("").toAbsolutePath());
    }

    private static Contract contract(String eventType, String producer, String topic, Set<String> required) {
        return new Contract(eventType, producer, topic, required, Set.of());
    }

    private static Set<String> set(String... values) {
        return Set.of(values);
    }

    private record Contract(String eventType, String producer, String topic,
                            Set<String> required, Set<String> optional) {
        String schemaFile() {
            return eventType + ".json";
        }

        String messageKey() {
            return eventType.substring(0, eventType.indexOf('.'));
        }

        Set<String> payloadFields() {
            Set<String> fields = new HashSet<>(required);
            fields.addAll(optional);
            return fields;
        }
    }
}
