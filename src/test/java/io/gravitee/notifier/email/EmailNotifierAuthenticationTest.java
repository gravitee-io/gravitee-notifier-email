/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.notifier.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.util.ReflectionTestUtils.setField;

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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class EmailNotifierAuthenticationTest extends AbstractEmailNotifierTest {

    private EmailNotifier emailNotifier;

    private final Notification notification = new Notification();

    private final EmailNotifierConfiguration emailNotifierConfiguration = new EmailNotifierConfiguration();

    private final Map<String, Object> parameters = new HashMap<>();

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
        .withConfiguration(GreenMailConfiguration.aConfig().withUser("user", "password"))
        .withPerMethodLifecycle(true);

    @BeforeEach
    public void setUp() throws IOException {
        super.setUp();

        emailNotifier = new EmailNotifier(emailNotifierConfiguration);

        emailNotifierConfiguration.setFrom("from@mail.com");
        emailNotifierConfiguration.setTo("to@mail.com");
        emailNotifierConfiguration.setSubject("subject of email");
        emailNotifierConfiguration.setBody("template_sample.html");
        emailNotifierConfiguration.setHost(ServerSetupTest.SMTP.getBindAddress());
        emailNotifierConfiguration.setPort(ServerSetupTest.SMTP.getPort());
        emailNotifierConfiguration.setUsername("user");
        emailNotifierConfiguration.setPassword("password");

        notification.setType(EmailNotifier.TYPE);
        setField(emailNotifier, "templatesPath", this.getClass().getResource("/io/gravitee/notifier/email/templates").getPath());
        emailNotifier.afterPropertiesSet();
    }

    @Test
    void shouldSendEmailWithCredentials() throws Exception {
        Vertx
            .vertx()
            .runOnContext(event -> {
                emailNotifier
                    .send(notification, parameters)
                    .whenComplete((unused, throwable) -> {
                        assertThat(greenMail.getReceivedMessages()).hasSize(1);
                        MimeMessage receivedMessage = greenMail.getReceivedMessages()[0];

                        try {
                            assertThat(GreenMailUtil.getBody(receivedMessage))
                                .isEqualTo("<html>\r\n <head></head>\r\n <body>\r\n  template_sample.html\r\n </body>\r\n</html>");
                            assertThat(receivedMessage.getAllRecipients()).hasSize(1);
                            assertThat(receivedMessage.getAllRecipients()[0]).hasToString("to@mail.com");
                            assertThat(receivedMessage.getSubject()).hasToString("subject of email");
                            assertEquals("subject of email", receivedMessage.getSubject());
                        } catch (MessagingException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .whenComplete(completeOrFailNow());
            });

        awaitCompletionAndCheckFailure();
    }

    @Test
    void shouldNotSendEmailWithInvalidCredentials() {
        emailNotifierConfiguration.setUsername("user");
        emailNotifierConfiguration.setPassword("bad-password");

        Vertx
            .vertx()
            .runOnContext(event -> {
                emailNotifier
                    .send(notification, parameters)
                    .whenComplete((unused, throwable) -> {
                        assertThat(throwable).isNotNull();
                        assertThat(greenMail.getReceivedMessages()).isEmpty();
                        testContext.completeNow();
                    })
                    .whenComplete(completeOrFailNow());
            });

        awaitCompletionAndCheckFailure();
    }

    @Test
    @Disabled("No exception when no credentials are passed to mail server")
    /*
     * From GreenMail documentation:
     * By default GreenMail accepts all incoming emails. If there is no corresponding existing email account,
     * one is automatically created with login and password being the same as the to-address.
     */
    void shouldNotSendEmailWithoutCredentials() throws Exception {
        emailNotifierConfiguration.setUsername(null);
        emailNotifierConfiguration.setPassword(null);

        Vertx
            .vertx()
            .runOnContext(event -> {
                emailNotifier
                    .send(notification, parameters)
                    .whenComplete((unused, throwable) -> {
                        assertNotNull(throwable);
                        assertEquals(0, greenMail.getReceivedMessages().length);
                        testContext.completeNow();
                    })
                    .whenComplete(completeOrFailNow());
            });

        awaitCompletionAndCheckFailure();
    }
}
