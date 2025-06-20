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

import io.gravitee.notifier.email.configuration.EmailNotifierConfiguration;
import io.vertx.ext.mail.MailMessage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class EmailNotifierParametersTest {

    private EmailNotifier emailNotifier;

    private final EmailNotifierConfiguration emailNotifierConfiguration = new EmailNotifierConfiguration();

    @BeforeEach
    public void init() throws IOException {
        emailNotifier = new EmailNotifier(emailNotifierConfiguration);
        emailNotifier.setTemplatesPath(this.getClass().getResource("/io/gravitee/notifier/email/templates").getPath());
        emailNotifier.afterPropertiesSet();
    }

    @Test
    void shouldSendEmailToSingleRecipient() throws Exception {
        emailNotifierConfiguration.setFrom("from@mail.com");
        emailNotifierConfiguration.setTo("${(entity.metadata['emails'])}");
        emailNotifierConfiguration.setSubject("subject of email");
        emailNotifierConfiguration.setBody("template_sample.html");

        Entity entity = new Entity();
        entity.getMetadata().put("emails", "john.doe@gmail.com,jane.doe@gmail.com");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("entity", entity);

        MailMessage mailMessage = emailNotifier.prepareMailMessage(parameters);

        assertThat(mailMessage.getTo()).hasSize(2);
    }

    public static class Entity {

        private final Map<String, Object> metadata = new HashMap<>();

        public Map<String, Object> getMetadata() {
            return metadata;
        }
    }
}
