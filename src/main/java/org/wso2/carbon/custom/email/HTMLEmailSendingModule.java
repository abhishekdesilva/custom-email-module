/*
 * Copyright (c) 2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.custom.email;

import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.description.TransportOutDescription;
import org.apache.axis2.transport.mail.MailUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.core.CarbonConfigurationContextFactory;
import org.wso2.carbon.identity.mgt.mail.AbstractEmailSendingModule;
import org.wso2.carbon.identity.mgt.mail.EmailConfig;
import org.wso2.carbon.identity.mgt.mail.Notification;

import javax.mail.*;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;


/**
 * Default email sending implementation
 */
public class HTMLEmailSendingModule extends AbstractEmailSendingModule {

    public static final String CONF_STRING = "confirmation";
    private static final String SEND_MAIL_PROPERTY = "mailto:";
    private static Log log = LogFactory.getLog(HTMLEmailSendingModule.class);
    private BlockingQueue<Notification> notificationQueue = new LinkedBlockingDeque<Notification>();

    /**
     * Replace the {user-parameters} in the config file with the respective
     * values
     *
     * @param text           the initial text
     * @param userParameters mapping of the key and its value
     * @return the final text to be sent in the email
     */
    public static String replacePlaceHolders(String text, Map<String, String> userParameters) {
        if (userParameters != null) {
            for (Map.Entry<String, String> entry : userParameters.entrySet()) {
                String key = entry.getKey();
                if (key != null && entry.getValue() != null) {
                    text = text.replaceAll("\\{" + key + "\\}", entry.getValue());
                }
            }
        }
        return text;
    }

    @Override
    public void sendEmail() {
        try {
            Notification notification = notificationQueue.take();
            ConfigurationContext configContext = CarbonConfigurationContextFactory
                    .getConfigurationContext();
            TransportOutDescription transportOutDescription = null;
            if (configContext != null) {
                transportOutDescription = configContext.getAxisConfiguration().getTransportOut(EmailConstants.MAIL_TO);
            } else {

            }

            String smtpHost = transportOutDescription.getParameter(EmailConstants.MAIL_SMTP_HOST).getValue().toString();
            String smtpPort = transportOutDescription.getParameter(EmailConstants.MAIL_SMTP_PORT).getValue().toString();
            String smtpStarttls = transportOutDescription.getParameter(EmailConstants.MAIL_SMTP_STARTTLS).getValue().toString();
            String smtpAuth = transportOutDescription.getParameter(EmailConstants.MAIL_SMTP_AUTH).getValue().toString();
            final String smtpUser = transportOutDescription.getParameter(EmailConstants.MAIL_SMTP_USER).getValue().toString();
            final String smtpPassword = transportOutDescription.getParameter(EmailConstants.MAIL_SMTP_PASSWORD).getValue().toString();
            String smtpFrom = transportOutDescription.getParameter(EmailConstants.MAIL_SMTP_FROM).getValue().toString();
            String contentType;
            if (transportOutDescription.getParameter(EmailConstants.MAIL_CONTENT_TYPE) != null) {
                contentType = transportOutDescription.getParameter(EmailConstants.MAIL_CONTENT_TYPE).getValue().toString();
            } else {
                contentType = "text/plain";
            }

            Properties properties = new Properties();
            properties.put(EmailConstants.MAIL_SMTP_HOST, smtpHost);
            properties.put(EmailConstants.MAIL_SMTP_PORT, smtpPort);
            properties.put(EmailConstants.MAIL_SMTP_STARTTLS, smtpStarttls);
            properties.put(EmailConstants.MAIL_SMTP_AUTH, smtpAuth);

            Session session = Session.getInstance(properties,
                    new Authenticator() {
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(smtpUser, smtpPassword);
                        }
                    });

            Message message = new MimeMessage(session);
            Address[] addresses = InternetAddress.parse(notification.getSendTo());
            message.setFrom(new InternetAddress(smtpFrom));
            message.setRecipients(Message.RecipientType.TO, addresses);
            message.setSubject(notification.getSubject());
            StringBuilder contents = new StringBuilder();
            contents.append(notification.getBody())
                    .append(System.getProperty("line.separator"))
                    .append(System.getProperty("line.separator"))
                    .append(notification.getFooter());
            message.setContent(contents.toString(), contentType);
            Thread.currentThread().setContextClassLoader(MailUtils.class.getClassLoader());
            Transport.send(message);

            if (log.isDebugEnabled()) {
                log.debug("Email content : " + notification.getBody());
            }

            log.info("Email notification has been sent to " + notification.getSendTo());
        } catch (InterruptedException e) {
            log.error("Interrupted while waiting until an element becomes available in the notification queue.", e);
        } catch (AddressException e) {
            e.printStackTrace();
        } catch (MessagingException e) {
            e.printStackTrace();
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }

    }

    public String getRequestMessage(EmailConfig emailConfig) {

        StringBuilder msg;
        String targetEpr = emailConfig.getTargetEpr();
        if (emailConfig.getEmailBody().length() == 0) {
            msg = new StringBuilder(EmailConfig.DEFAULT_VALUE_MESSAGE);
            msg.append("\n");
            if (notificationData.getNotificationCode() != null) {

                msg.append(targetEpr).append("?").append(CONF_STRING).append(notificationData
                        .getNotificationCode()).append("\n");
            }
        } else {
            msg = new StringBuilder(emailConfig.getEmailBody());
            msg.append("\n");
        }
        if (emailConfig.getEmailFooter() != null) {
            msg.append("\n").append(emailConfig.getEmailFooter());
        }
        return msg.toString();
    }

    @Override
    public Notification getNotification() {
        return notificationQueue.peek();
    }

    @Override
    public void setNotification(Notification notification) {
        notificationQueue.add(notification);
    }

}
