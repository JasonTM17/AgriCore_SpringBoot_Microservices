package com.agricore.notification.infrastructure.delivery;

import com.agricore.notification.application.port.NotificationDeliveryRequest;
import com.agricore.notification.application.port.NotificationDeliveryResult;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpNotificationDeliveryAdapterTest {

    private JavaMailSender mailSender;
    private SmtpNotificationDeliveryAdapter adapter;
    private MimeMessage message;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        adapter = new SmtpNotificationDeliveryAdapter(mailSender, "no-reply@agricore.local");
        message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
    }

    @Test
    void sendsEmailWithoutExposingRecipientOrBodyInResult() {
        NotificationDeliveryResult result = adapter.deliver(new NotificationDeliveryRequest(
                "EMAIL", "manager@agricore.local", "Harvest ready", "Batch HB-1 is ready"
        ));

        assertThat(result.delivered()).isTrue();
        assertThat(result.errorCode()).isNull();
        assertThat(result.errorMessage()).isNull();
        verify(mailSender).send(message);
    }

    @Test
    void rejectsUnsupportedChannelBeforeCallingSmtp() {
        NotificationDeliveryResult result = adapter.deliver(new NotificationDeliveryRequest(
                "SMS", "+84123456789", "Harvest ready", "Batch HB-1 is ready"
        ));

        assertThat(result.delivered()).isFalse();
        assertThat(result.errorCode()).isEqualTo("UNSUPPORTED_CHANNEL");
        assertThat(result.errorMessage()).doesNotContain("+84123456789");
        verify(mailSender, org.mockito.Mockito.never()).send(message);
    }

    @Test
    void convertsSmtpFailureToBoundedRetryableResult() {
        doThrow(new MailSendException("recipient=manager@agricore.local")).when(mailSender).send(message);

        NotificationDeliveryResult result = adapter.deliver(new NotificationDeliveryRequest(
                "EMAIL", "manager@agricore.local", "Harvest ready", "Batch HB-1 is ready"
        ));

        assertThat(result.delivered()).isFalse();
        assertThat(result.errorCode()).isEqualTo("SMTP_DELIVERY_FAILED");
        assertThat(result.errorMessage()).isEqualTo("SMTP delivery failed");
        assertThat(result.retryable()).isTrue();
        assertThat(result.errorMessage()).doesNotContain("manager@agricore.local");
    }

    @Test
    void rejectsInvalidConfiguredSenderAtStartup() {
        assertThatThrownBy(() -> new SmtpNotificationDeliveryAdapter(mailSender, "invalid sender"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SMTP from address is invalid");
    }
}
