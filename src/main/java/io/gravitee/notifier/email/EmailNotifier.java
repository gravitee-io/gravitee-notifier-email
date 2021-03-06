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

import freemarker.cache.FileTemplateLoader;
import freemarker.core.TemplateClassResolver;
import freemarker.template.Configuration;
import io.gravitee.common.utils.UUID;
import io.gravitee.notifier.api.AbstractConfigurableNotifier;
import io.gravitee.notifier.api.Notification;
import io.gravitee.notifier.email.configuration.EmailNotifierConfiguration;
import io.vertx.core.Vertx;
import io.vertx.ext.mail.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;

import javax.activation.MimetypesFileTypeMap;
import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.vertx.core.buffer.Buffer.buffer;
import static io.vertx.ext.mail.MailClient.createShared;
import static java.lang.String.valueOf;
import static java.nio.file.Files.readAllBytes;
import static java.util.stream.Collectors.toList;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Azize ELAMRANI (azize.elamrani at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EmailNotifier extends AbstractConfigurableNotifier<EmailNotifierConfiguration> implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailNotifier.class);

    private static final String TYPE = "email-notifier";

    @Value("${notifiers.email.templates.path:${gravitee.home}/templates}")
    private String templatesPath;

    private Configuration config = new Configuration(Configuration.VERSION_2_3_28);

    public EmailNotifier(EmailNotifierConfiguration configuration) {
        super(TYPE, configuration);
    }

    public void afterPropertiesSet() throws IOException {
        config.setNewBuiltinClassResolver(TemplateClassResolver.SAFER_RESOLVER);
        config.setTemplateLoader(new FileTemplateLoader(new File(URLDecoder.decode(templatesPath, "UTF-8"))));
    }

    @Override
    public CompletableFuture<Void> doSend(final Notification notification, final Map<String, Object> parameters) {
        final CompletableFuture<Void> completeFuture = new CompletableFuture<>();
        try {
            final MailMessage mailMessage = new MailMessage()
                    .setFrom(templatize(configuration.getFrom(), parameters))
                    .setTo(Arrays.stream(configuration.getTo().split(",|;|\\s")).map(to -> {
                        try {
                            return templatize(to, parameters);
                        } catch (Exception ex) {
                            completeFuture.completeExceptionally(ex);
                            throw new IllegalArgumentException("Error while sending email notification", ex);
                        }
                    }).collect(toList()));

            mailMessage.setSubject(templatize(configuration.getSubject(), parameters));
            addContentInMessage(mailMessage, templatize(configuration.getBody(), parameters));

            final MailConfig mailConfig = new MailConfig()
                    .setHostname(configuration.getHost())
                    .setPort(configuration.getPort())
                    .setTrustAll(configuration.isSslTrustAll());

            if (configuration.getUsername() != null && ! configuration.getUsername().isEmpty() &&
                    configuration.getPassword() != null && ! configuration.getPassword().isEmpty()) {
                mailConfig.setUsername(configuration.getUsername());
                mailConfig.setPassword(configuration.getPassword());
            } else {
                mailConfig.setLogin(LoginOption.DISABLED);
            }

            if (configuration.getSslKeyStore() != null) {
                mailConfig.setKeyStore(configuration.getSslKeyStore());
            }
            if (configuration.getSslKeyStorePassword() != null) {
                mailConfig.setKeyStorePassword(configuration.getSslKeyStorePassword());
            }
            if (configuration.isStartTLSEnabled()) {
                mailConfig.setStarttls(StartTLSOptions.REQUIRED);
            } else {
                mailConfig.setStarttls(StartTLSOptions.DISABLED);
            }

            createShared(Vertx.currentContext().owner(), mailConfig, valueOf(mailConfig.hashCode()))
                    .sendMail(mailMessage, e -> {
                        if (e.succeeded()) {
                            LOGGER.info("Email sent! " + e.result());
                            completeFuture.complete(null);
                        } else {
                            LOGGER.error("Email failed!", e.cause());
                            completeFuture.completeExceptionally(e.cause());
                        }
                    });
        } catch (final Exception ex) {
            LOGGER.error("Error while sending email notification", ex);
            completeFuture.completeExceptionally(ex);
        }
        return completeFuture;
    }

    private void addContentInMessage(final MailMessage mailMessage, final String htmlText) throws Exception {
        final Document document = Jsoup.parse(htmlText);
        final Elements imageElements = document.getElementsByTag("img");

        final List<Element> resources = imageElements.stream()
                .filter(imageElement -> imageElement.hasAttr("src") && !imageElement.attr("src").startsWith("http"))
                .collect(toList());

        if (!resources.isEmpty()) {
            final List<MailAttachment> mailAttachments = new ArrayList<>(resources.size());
            for (final Element res : resources) {
                final MailAttachment attachment = new MailAttachment();

                String source = res.attr("src").trim();
                if (source.startsWith("data:image/")) {
                    final String value = source.replaceFirst("^data:image/[^;]*;base64,?", "");
                    byte[] bytes = Base64.getDecoder().decode(value.getBytes(StandardCharsets.UTF_8));
                    attachment.setContentType(extractMimeType(source));
                    attachment.setData(buffer(bytes));
                } else {
                    attachment.setContentType(getContentTypeByFileName(source));
                    attachment.setData(buffer(readAllBytes(new File(templatesPath, source).toPath())));
                }

                String contentId = UUID.random().toString();
                res.attr("src", "cid:" + contentId);
                attachment.setContentId('<' + contentId + '>');
                attachment.setDisposition("inline");

                mailAttachments.add(attachment);
            }

            // Attach images
            mailMessage.setInlineAttachment(mailAttachments);
        }

        // Set HTML content
        mailMessage.setHtml(document.html());
    }

    private String getContentTypeByFileName(final String fileName) {
        if (fileName == null) {
            return "";
        } else if (fileName.endsWith(".png")) {
            return "image/png";
        }
        return MimetypesFileTypeMap.getDefaultFileTypeMap().getContentType(fileName);
    }

    /**
     * Extract the MIME type from a base64 string
     * @param encoded Base64 string
     * @return MIME type string
     */
    private static String extractMimeType(final String encoded) {
        final Pattern mime = Pattern.compile("^data:([a-zA-Z0-9]+/[a-zA-Z0-9]+).*,.*");
        final Matcher matcher = mime.matcher(encoded);
        if (!matcher.find())
            return "";
        return matcher.group(1).toLowerCase();
    }
}
