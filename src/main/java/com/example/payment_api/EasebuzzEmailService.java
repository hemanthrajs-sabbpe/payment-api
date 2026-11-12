package com.example.payment_api;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Dedicated service for building and dispatching branded Easebuzz checkout emails.
 * Separated from PaymentService to eliminate circular dependency between PaymentService and PaymentSplitService.
 */
@Service
public class EasebuzzEmailService {

    private static final Logger log = LoggerFactory.getLogger(EasebuzzEmailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${otp.mail.from:}")
    private String mailFrom;

    public EasebuzzEmailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    public boolean sendEasebuzzCheckoutEmailSafely(
            String recipientEmail,
            String customerName,
            BigDecimal amount,
            String transactionId,
            String paymentUrl,
            String trackingUrl) {

        try {
            JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
            if (mailSender == null || recipientEmail == null || recipientEmail.isBlank() || paymentUrl == null || paymentUrl.isBlank()) {
                return false;
            }

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");

            if (mailFrom != null && !mailFrom.isBlank()) {
                helper.setFrom(mailFrom.trim(), "Easebuzz Payment Platform");
            }
            helper.setTo(recipientEmail.trim());
            helper.setSubject("Payment Checkout Request: " + transactionId);

            String htmlContent = buildEasebuzzEmailHtml(customerName, amount, transactionId, paymentUrl, trackingUrl);
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
            log.info("Payment link HTML email accepted by SMTP relay for recipient {} (Transaction: {}). Notice: SMTP acceptance acknowledges relay queueing, not confirmed recipient inbox delivery.",
                    safeEmailMask(recipientEmail), transactionId);
            return true;
        } catch (Exception ex) {
            log.warn("Payment link email relay failed for transaction {}: {}", transactionId, ex.getMessage());
            return false;
        }
    }

    private String safeEmailMask(String email) {
        if (email == null || email.isBlank()) return "<empty>";
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) return "***" + (atIndex >= 0 ? email.substring(atIndex) : "");
        return email.substring(0, 1) + "***" + email.substring(atIndex);
    }

    private String buildEasebuzzEmailHtml(
            String customerName,
            BigDecimal amount,
            String transactionId,
            String paymentUrl,
            String trackingUrl) {

        String safeCustomer = escapeHtml(customerName != null ? customerName : "Customer");
        String safeTxnId = escapeHtml(transactionId != null ? transactionId : "—");
        String formattedAmount = String.format(Locale.US, "%.2f", amount != null ? amount : BigDecimal.ZERO);

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">");
        sb.append("<title>Payment Request</title></head>");
        sb.append("<body style=\"margin:0;padding:0;background-color:#0b0f19;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;color:#f1f5f9;\">");
        sb.append("<table role=\"presentation\" width=\"100%\" style=\"background-color:#0b0f19;padding:40px 15px;\"><tr><td align=\"center\">");
        sb.append("<table role=\"presentation\" width=\"100%\" style=\"max-width:540px;background-color:#141c2e;border:1px solid #1e293b;border-radius:16px;overflow:hidden;\">");
        sb.append("<tr><td style=\"height:4px;background:linear-gradient(90deg,#3b82f6,#8b5cf6);\"></td></tr>");
        sb.append("<tr><td style=\"padding:32px;text-align:center;\">");
        sb.append("<h1 style=\"margin:0 0 6px 0;font-size:24px;color:#ffffff;\">Easebuzz Checkout Request</h1>");
        sb.append("<p style=\"margin:0;font-size:14px;color:#94a3b8;\">Issued for <strong style=\"color:#e2e8f0;\">").append(safeCustomer).append("</strong></p>");
        sb.append("</td></tr>");
        sb.append("<tr><td style=\"padding:0 32px 24px 32px;\">");
        sb.append("<table role=\"presentation\" width=\"100%\" style=\"background:#18243c;border:1px solid #243552;border-radius:12px;padding:22px;text-align:center;\">");
        sb.append("<tr><td><span style=\"font-size:12px;color:#94a3b8;font-weight:600;\">AMOUNT PAYABLE</span>");
        sb.append("<div style=\"font-size:36px;font-weight:800;color:#ffffff;margin:8px 0;\">₹").append(formattedAmount).append("</div>");
        sb.append("</td></tr></table></td></tr>");
        sb.append("<tr><td style=\"padding:0 32px 28px 32px;text-align:center;\">");
        sb.append("<a href=\"").append(escapeHtml(paymentUrl)).append("\" target=\"_blank\" style=\"display:block;background:#2563eb;color:#ffffff;text-decoration:none;font-weight:700;font-size:16px;padding:16px 24px;border-radius:10px;\">Pay Now &rarr;</a>");
        sb.append("<p style=\"margin:10px 0 0 0;font-size:12px;color:#64748b;\">Instant checkout via UPI, Cards, Net Banking</p>");
        sb.append("</td></tr>");
        sb.append("<tr><td style=\"padding:0 32px 28px 32px;\">");
        sb.append("<table role=\"presentation\" width=\"100%\" style=\"background-color:#0f172a;border:1px solid #1e293b;border-radius:10px;padding:12px;\">");
        sb.append("<tr><td style=\"padding:8px;font-size:13px;color:#94a3b8;\">Transaction ID</td><td style=\"padding:8px;font-size:13px;font-family:monospace;color:#38bdf8;text-align:right;\">").append(safeTxnId).append("</td></tr>");
        sb.append("</table></td></tr>");
        sb.append("<tr><td style=\"padding:0 32px 24px 32px;text-align:center;\">");
        sb.append("<a href=\"").append(escapeHtml(trackingUrl)).append("\" target=\"_blank\" style=\"font-size:13px;color:#60a5fa;text-decoration:none;\">Track payment status &rarr;</a>");
        sb.append("</td></tr>");
        sb.append("<tr><td style=\"padding:20px 32px;border-top:1px solid #1e293b;text-align:center;background-color:#0d1322;\">");
        sb.append("<p style=\"margin:0;font-size:11px;color:#475569;\">Powered by Easebuzz Sandbox Payment Platform.</p>");
        sb.append("</td></tr></table></td></tr></table></body></html>");
        return sb.toString();
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
