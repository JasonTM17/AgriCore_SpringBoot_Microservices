package com.agricore.notification.infrastructure.delivery;

import com.agricore.notification.application.port.NotificationDeliveryPort;
import com.agricore.notification.application.port.NotificationDeliveryRequest;
import com.agricore.notification.application.port.NotificationDeliveryResult;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "agricore.notification.delivery.provider",
        havingValue = "smtp",
        matchIfMissing = true
)
public class SmtpNotificationDeliveryAdapter implements NotificationDeliveryPort {

    private final JavaMailSender mailSender;
    private final InternetAddress fromAddress;

    public SmtpNotificationDeliveryAdapter(
            JavaMailSender mailSender,
            @Value("${agricore.notification.delivery.smtp.from-address:no-reply@agricore.local}") String fromAddress
    ) {
        this.mailSender = mailSender;
        this.fromAddress = validatedFromAddress(fromAddress);
    }

    @Override
    public NotificationDeliveryResult deliver(NotificationDeliveryRequest request) {
        if (!"EMAIL".equalsIgnoreCase(request.channel())) {
            return NotificationDeliveryResult.failed(
                    "UNSUPPORTED_CHANNEL",
                    "SMTP adapter supports EMAIL only",
                    false
            );
        }
        try {
            InternetAddress recipient = new InternetAddress(request.recipient());
            recipient.validate();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipient);
            helper.setSubject(request.subject());
            helper.setText(request.body(), false);
            mailSender.send(message);
            return NotificationDeliveryResult.sent();
        } catch (AddressException exception) {
            return NotificationDeliveryResult.failed(
                    "INVALID_RECIPIENT",
                    "Recipient address is invalid",
                    false
            );
        } catch (MessagingException exception) {
            return NotificationDeliveryResult.failed(
                    "MESSAGE_PREPARATION_FAILED",
                    "Notification message preparation failed",
                    false
            );
        } catch (MailAuthenticationException | MailParseException exception) {
            return NotificationDeliveryResult.failed(
                    "SMTP_CONFIGURATION_ERROR",
                    "SMTP delivery configuration is invalid",
                    false
            );
        } catch (MailException exception) {
            return NotificationDeliveryResult.failed(
                    "SMTP_DELIVERY_FAILED",
                    "SMTP delivery failed",
                    true
            );
        }
    }

    private static InternetAddress validatedFromAddress(String fromAddress) {
        try {
            InternetAddress address = new InternetAddress(fromAddress);
            address.validate();
            return address;
        } catch (AddressException exception) {
            throw new IllegalArgumentException("SMTP from address is invalid");
        }
    }
}
