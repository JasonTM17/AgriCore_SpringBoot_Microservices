package com.agricore.notification.infrastructure.delivery;

import com.agricore.notification.application.port.NotificationDeliveryPort;
import com.agricore.notification.application.port.NotificationDeliveryRequest;
import com.agricore.notification.application.port.NotificationDeliveryResult;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
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
            message.setHeader("X-AgriCore-Notification-Id", request.notificationId().toString());
            message.setHeader("X-AgriCore-Delivery-Claim", request.deliveryClaimId().toString());
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
                    isRetryable(exception)
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

    private static boolean isRetryable(MailException exception) {
        Boolean classification = classify(exception);
        if (classification != null) {
            return classification;
        }
        if (exception instanceof MailSendException sendException) {
            for (Exception messageException : sendException.getMessageExceptions()) {
                classification = classify(messageException);
                if (classification != null) {
                    return classification;
                }
            }
        }
        return true;
    }

    private static Boolean classify(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof SMTPAddressFailedException addressFailure) {
                return retryableStatus(addressFailure.getReturnCode());
            }
            if (current instanceof SMTPSendFailedException sendFailure) {
                return retryableStatus(sendFailure.getReturnCode());
            }
            if (current instanceof SendFailedException) {
                return false;
            }
            if (current instanceof MessagingException messagingException
                    && messagingException.getNextException() != null
                    && messagingException.getNextException() != current) {
                Boolean nested = classify(messagingException.getNextException());
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static boolean retryableStatus(int statusCode) {
        return statusCode >= 400 && statusCode < 500;
    }
}
