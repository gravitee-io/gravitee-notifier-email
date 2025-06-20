/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.notifier.email;

import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import io.gravitee.notifier.api.Notification;
import io.gravitee.notifier.email.configuration.EmailNotifierConfiguration;
import io.vertx.core.Vertx;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class EmailNotifierTest extends AbstractEmailNotifierTest {

    private EmailNotifier emailNotifier;

    private final Notification notification = new Notification();

    private final EmailNotifierConfiguration emailNotifierConfiguration = new EmailNotifierConfiguration();

    private final Map<String, Object> parameters = new HashMap<>();

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
        .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication())
        .withPerMethodLifecycle(true);

    @BeforeEach
    public void setUp() throws IOException {
        super.setUp();

        notification.setType(EmailNotifier.TYPE);

        emailNotifierConfiguration.setFrom("from@mail.com");
        emailNotifierConfiguration.setTo("to@mail.com");
        emailNotifierConfiguration.setSubject("subject of email");
        emailNotifierConfiguration.setBody("template_sample.html");
        emailNotifierConfiguration.setHost(ServerSetupTest.SMTP.getBindAddress());
        emailNotifierConfiguration.setPort(ServerSetupTest.SMTP.getPort());
        emailNotifierConfiguration.setUsername("user");
        emailNotifierConfiguration.setPassword("password");

        emailNotifier = new EmailNotifier(emailNotifierConfiguration);
        emailNotifier.setTemplatesPath(this.getClass().getResource("/io/gravitee/notifier/email/templates").getPath());
        emailNotifier.afterPropertiesSet();
    }

    @Test
    void shouldSendEmailToSingleRecipient() {
        Vertx
            .vertx()
            .runOnContext(event ->
                emailNotifier
                    .send(notification, parameters)
                    .whenComplete((unused, throwable) -> {
                        assertThat(greenMail.getReceivedMessages()).hasSize(1);

                        MimeMessage receivedMessage = greenMail.getReceivedMessages()[0];

                        assertThat(GreenMailUtil.getBody(receivedMessage))
                            .isEqualTo("<html>\r\n <head></head>\r\n <body>template_sample.html</body>\r\n</html>");

                        try {
                            assertThat(receivedMessage.getAllRecipients()).hasSize(1);
                            assertThat(receivedMessage.getAllRecipients()[0]).hasToString("to@mail.com");
                            //TODO: check why the sender is always null
                            // assertThat(receivedMessage.getSender()).hasToString("from@mail.com");
                            assertThat(receivedMessage.getSubject()).isEqualTo("subject of email");
                        } catch (MessagingException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .whenComplete(completeOrFailNow())
            );

        awaitCompletionAndCheckFailure();
    }

    @Test
    void shouldSendEmailToMultipleRecipients() {
        emailNotifierConfiguration.setTo("to@mail.com,to2@mail.com");

        Vertx
            .vertx()
            .runOnContext(event ->
                emailNotifier
                    .send(notification, parameters)
                    .whenComplete((unused, throwable) -> {
                        assertThat(greenMail.getReceivedMessages()).hasSize(2);
                        MimeMessage receivedMessage = greenMail.getReceivedMessages()[1];

                        assertThat(GreenMailUtil.getBody(receivedMessage))
                            .isEqualTo("<html>\r\n <head></head>\r\n <body>template_sample.html</body>\r\n</html>");
                        try {
                            assertThat(receivedMessage.getAllRecipients()).hasSize(2);
                            assertThat(receivedMessage.getAllRecipients()[0]).hasToString("to@mail.com");
                            assertThat(receivedMessage.getAllRecipients()[1]).hasToString("to2@mail.com");
                            //TODO: check why the sender is always null
                            //    assertEquals("from@mail.com", receivedMessage.getSender().toString());
                            assertThat(receivedMessage.getSubject()).isEqualTo("subject of email");
                        } catch (MessagingException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .whenComplete(completeOrFailNow())
            );

        awaitCompletionAndCheckFailure();
    }

    @Test
    void shouldSendEmailWithImage() {
        emailNotifierConfiguration.setBody("<img src=\"images/email.svg\" />\n<div>test</div>");

        Vertx
            .vertx()
            .runOnContext(event ->
                emailNotifier
                    .send(notification, parameters)
                    .whenComplete((unused, throwable) -> {
                        assertThat(greenMail.getReceivedMessages()).hasSize(1);
                        MimeMessage receivedMessage = greenMail.getReceivedMessages()[0];

                        try {
                            assertThat(GreenMailUtil.getBody(receivedMessage)).contains("Content-ID:");
                            assertThat(receivedMessage.getAllRecipients()).hasSize(1);
                            assertThat(receivedMessage.getAllRecipients()[0]).hasToString("to@mail.com");
                            assertThat(receivedMessage.getSubject()).isEqualTo("subject of email");
                        } catch (MessagingException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .whenComplete(completeOrFailNow())
            );

        awaitCompletionAndCheckFailure();
    }

    @Test
    void shouldSendEmailWithInvalidImage() {
        emailNotifierConfiguration.setBody("<img src=\"../../../../../images/email.svg\" />\n<div>test</div>");

        Vertx
            .vertx()
            .runOnContext(event ->
                emailNotifier
                    .send(notification, parameters)
                    .whenComplete((unused, throwable) -> {
                        assertThat(greenMail.getReceivedMessages()).hasSize(1);
                        MimeMessage receivedMessage = greenMail.getReceivedMessages()[0];

                        try {
                            assertThat(GreenMailUtil.getBody(receivedMessage)).doesNotContain("Content-ID:");
                            assertThat(receivedMessage.getAllRecipients()).hasSize(1);

                            assertThat(receivedMessage.getAllRecipients()[0]).hasToString("to@mail.com");
                            assertThat(receivedMessage.getSubject()).isEqualTo("subject of email");
                        } catch (MessagingException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .whenComplete(completeOrFailNow())
            );

        awaitCompletionAndCheckFailure();
    }

    @Test
    @DisplayName("Should send email with new line identifier converted to <br>")
    void shouldSendEmailWithNewLine() {
        emailNotifierConfiguration.setBody("A test \n with \n new \n line\n");

        Vertx
            .vertx()
            .runOnContext(event ->
                emailNotifier
                    .send(notification, parameters)
                    .whenComplete((unused, throwable) -> {
                        assertThat(greenMail.getReceivedMessages()).hasSize(1);
                        MimeMessage receivedMessage = greenMail.getReceivedMessages()[0];

                        try {
                            assertThat(GreenMailUtil.getBody(receivedMessage))
                                .isEqualTo(
                                    """
                                            <html>\r
                                             <head></head>\r
                                             <body>\r
                                              A test\r
                                              <br>\r
                                              with\r
                                              <br>\r
                                              new\r
                                              <br>\r
                                              line\r
                                              <br>\r
                                             </body>\r
                                            </html>"""
                                );
                            assertThat(receivedMessage.getAllRecipients()).hasSize(1);
                            assertThat(receivedMessage.getAllRecipients()[0]).hasToString("to@mail.com");
                            assertThat(receivedMessage.getSubject()).isEqualTo("subject of email");
                        } catch (MessagingException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .whenComplete(completeOrFailNow())
            );

        awaitCompletionAndCheckFailure();
    }
}
