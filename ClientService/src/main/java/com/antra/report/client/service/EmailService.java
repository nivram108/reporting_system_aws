package com.antra.report.client.service;

import com.antra.report.client.pojo.EmailType;

/**
 * EmailService send the constant email to the address when the report is generated
 */
public interface EmailService {
    void sendEmail(String to, EmailType success, String submitter);
}
