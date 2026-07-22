package com.agricore.iot.infrastructure.messaging;

import com.agricore.iot.application.service.IotMetrics;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "agricore.mqtt.enabled", havingValue = "true")
public class MqttTelemetryListener implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(MqttTelemetryListener.class);
    private final IotMetrics metrics;
    private final MqttTelemetryMessageProcessor messageProcessor;
    private final String broker;
    private final String brokerScheme;
    private final String configuredClientId;
    private final String topic;
    private final int connectionTimeoutSeconds;
    private final int reconnectDelaySeconds;
    private final String username;
    private final String password;
    private final Object connectionLock = new Object();
    private final ScheduledExecutorService connector = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "agricore-mqtt-connector");
        thread.setDaemon(true);
        return thread;
    });
    private final ThreadPoolExecutor processor = new ThreadPoolExecutor(
            2, 4, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(64), r -> {
        Thread thread = new Thread(r, "agricore-mqtt-processor");
        thread.setDaemon(true);
        return thread;
    });

    private volatile MqttAsyncClient client;
    private volatile boolean running;
    private volatile boolean subscribed;

    public MqttTelemetryListener(
            IotMetrics metrics,
            MqttTelemetryMessageProcessor messageProcessor,
            @Value("${agricore.mqtt.broker:tcp://localhost:1883}") String broker,
            @Value("${agricore.mqtt.allow-insecure:false}") boolean allowInsecure,
            @Value("${agricore.mqtt.client-id:agricore-iot-service}") String clientId,
            @Value("${agricore.mqtt.topic:agricore/telemetry/+/reading}") String topic,
            @Value("${agricore.mqtt.qos:1}") int qos,
            @Value("${agricore.mqtt.connection-timeout-seconds:5}") int connectionTimeoutSeconds,
            @Value("${agricore.mqtt.reconnect-delay-seconds:10}") int reconnectDelaySeconds,
            @Value("${agricore.mqtt.username:}") String username,
            @Value("${agricore.mqtt.password:}") String password
    ) {
        if (qos != 1) {
            throw new IllegalArgumentException("MQTT telemetry contract requires QoS 1");
        }
        if (clientId.isBlank() || clientId.length() > 128 || username.isBlank() || password.isBlank()) {
            throw new IllegalArgumentException("MQTT client ID and credentials must not be blank");
        }
        if (!"agricore/telemetry/+/reading".equals(topic)) {
            throw new IllegalArgumentException("MQTT topic must match the telemetry contract");
        }
        if (connectionTimeoutSeconds < 1 || connectionTimeoutSeconds > 60
                || reconnectDelaySeconds < 1 || reconnectDelaySeconds > 300) {
            throw new IllegalArgumentException("MQTT timeout configuration is outside safe bounds");
        }
        URI brokerUri = URI.create(broker);
        String scheme = brokerUri.getScheme();
        if (scheme == null || brokerUri.getHost() == null || brokerUri.getUserInfo() != null
                || (!allowInsecure && !"ssl".equalsIgnoreCase(scheme)
                && !"wss".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("MQTT broker must use TLS unless insecure transport is explicitly allowed");
        }
        this.metrics = metrics;
        this.messageProcessor = messageProcessor;
        this.broker = broker;
        this.brokerScheme = scheme;
        this.configuredClientId = clientId;
        this.topic = topic;
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
        this.reconnectDelaySeconds = reconnectDelaySeconds;
        this.username = username;
        this.password = password;
    }

    @Override
    public void start() {
        running = true;
        connector.scheduleWithFixedDelay(this::connectIfNeeded, 0, reconnectDelaySeconds, TimeUnit.SECONDS);
    }

    private void connectIfNeeded() {
        synchronized (connectionLock) {
            if (!running) {
                return;
            }
            try {
                if (client == null) {
                    client = new MqttAsyncClient(broker, configuredClientId, new MemoryPersistence());
                    client.setManualAcks(true);
                    client.setCallback(new Callback());
                }
                if (!client.isConnected()) {
                    subscribed = false;
                    client.connect(connectOptions()).waitForCompletion(connectionTimeoutSeconds * 1000L);
                }
                if (!subscribed) {
                    client.subscribe(topic, 1, this::dispatchMessage)
                            .waitForCompletion(connectionTimeoutSeconds * 1000L);
                    subscribed = true;
                    log.info("mqtt_telemetry_connected brokerScheme={} topic={} qos=1", brokerScheme, topic);
                }
            } catch (Exception exception) {
                subscribed = false;
                metrics.recordMqttOutcome("connection_failed");
                log.warn("mqtt_telemetry_connect_failed brokerScheme={} errorType={}", brokerScheme,
                        exception.getClass().getSimpleName());
                disconnectForRetry();
            }
        }
    }

    private MqttConnectOptions connectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(false);
        options.setConnectionTimeout(connectionTimeoutSeconds);
        options.setUserName(username);
        options.setPassword(password.toCharArray());
        return options;
    }

    private void dispatchMessage(String topicName, MqttMessage message) {
        try {
            processor.execute(() -> onMessage(topicName, message));
        } catch (RejectedExecutionException exception) {
            metrics.recordMqttOutcome("processing_failed");
            log.warn("mqtt_telemetry_backpressure queueCapacity=64");
            disconnectForRetry();
        }
    }

    void onMessage(String topicName, MqttMessage message) {
        MqttTelemetryMessageProcessor.Disposition disposition = messageProcessor.process(topicName, message);
        if (disposition == MqttTelemetryMessageProcessor.Disposition.ACKNOWLEDGE) {
            acknowledge(message);
        } else {
            disconnectForRetry();
        }
    }

    private void acknowledge(MqttMessage message) {
        MqttAsyncClient currentClient = client;
        if (currentClient == null || message.getId() <= 0) {
            return;
        }
        try {
            currentClient.messageArrivedComplete(message.getId(), message.getQos());
        } catch (MqttException exception) {
            metrics.recordMqttOutcome("processing_failed");
            log.warn("mqtt_telemetry_ack_failed errorType={}", exception.getClass().getSimpleName());
            disconnectForRetry();
        }
    }

    private void disconnectForRetry() {
        MqttAsyncClient currentClient = client;
        subscribed = false;
        if (currentClient == null) {
            return;
        }
        try {
            currentClient.disconnectForcibly(1_000L, 1_000L, false);
        } catch (MqttException exception) {
            log.debug("mqtt_telemetry_disconnect_failed errorType={}", exception.getClass().getSimpleName());
        }
    }

    @Override
    public void stop(Runnable callback) {
        running = false;
        subscribed = false;
        connector.shutdownNow();
        processor.shutdownNow();
        try {
            if (client != null && client.isConnected()) {
                client.disconnect().waitForCompletion(2_000L);
            }
            if (client != null) {
                client.close();
            }
        } catch (MqttException exception) {
            log.debug("mqtt_telemetry_shutdown_failed errorType={}", exception.getClass().getSimpleName());
        } finally {
            callback.run();
        }
    }

    @Override
    public void stop() {
        stop(() -> { });
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    public boolean isReady() {
        MqttAsyncClient currentClient = client;
        return currentClient != null && currentClient.isConnected() && subscribed;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private final class Callback implements MqttCallbackExtended {
        @Override
        public void connectComplete(boolean reconnect, String serverUri) {
            subscribed = false;
            if (running) {
                connector.execute(MqttTelemetryListener.this::connectIfNeeded);
            }
        }

        @Override
        public void connectionLost(Throwable cause) {
            subscribed = false;
            log.warn("mqtt_telemetry_connection_lost brokerScheme={} reasonType={}", brokerScheme,
                    cause == null ? "unknown" : cause.getClass().getSimpleName());
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            dispatchMessage(topic, message);
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            // This client only subscribes; publisher acknowledgements are not used.
        }
    }
}
