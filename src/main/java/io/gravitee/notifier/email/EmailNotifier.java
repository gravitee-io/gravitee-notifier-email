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

import static io.vertx.core.buffer.Buffer.buffer;
import static io.vertx.ext.mail.MailClient.createShared;
import static java.lang.String.valueOf;
import static java.nio.file.Files.readAllBytes;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import freemarker.cache.FileTemplateLoader;
import freemarker.core.TemplateClassResolver;
import freemarker.template.Configuration;
import io.gravitee.common.utils.UUID;
import io.gravitee.notifier.api.AbstractConfigurableNotifier;
import io.gravitee.notifier.api.Notification;
import io.gravitee.notifier.email.configuration.EmailNotifierConfiguration;
import io.vertx.core.Vertx;
import io.vertx.ext.mail.*;
import io.vertx.ext.mail.impl.MailAttachmentImpl;
import jakarta.activation.MimetypesFileTypeMap;
import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Azize ELAMRANI (azize.elamrani at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EmailNotifier extends AbstractConfigurableNotifier<EmailNotifierConfiguration> implements InitializingBean {

    private static final String RECIPIENTS_SPLIT_REGEX = ",|;|\\s";

    static final String TYPE = "email-notifier";

    @Value("${notifiers.email.templates.path:${gravitee.home}/templates}")
    private String templatesPath;

    private final Configuration config = new Configuration(Configuration.VERSION_2_3_32);

    public EmailNotifier(EmailNotifierConfiguration configuration) {
        super(TYPE, configuration);
    }

    public void afterPropertiesSet() throws IOException {
        config.setNewBuiltinClassResolver(TemplateClassResolver.SAFER_RESOLVER);
        config.setTemplateLoader(new FileTemplateLoader(new File(URLDecoder.decode(templatesPath, StandardCharsets.UTF_8))));
    }

    @Override
    public CompletableFuture<Void> doSend(final Notification notification, final Map<String, Object> parameters) {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            final MailMessage mailMessage = prepareMailMessage(parameters);
            final MailConfig mailConfig = prepareMailConfig();
            createShared(Vertx.currentContext().owner(), mailConfig, valueOf(mailConfig.getHostname().hashCode()))
                .sendMail(
                    mailMessage,
                    e -> {
                        if (e.succeeded()) {
                            logger.debug("Email {) has been send successfully! " + e.result().getMessageID());
                            future.complete(null);
                        } else {
                            logger.error("An error occurs while sending email", e.cause());
                            future.completeExceptionally(e.cause());
                        }
                    }
                );
        } catch (final Exception ex) {
            logger.error("Error while sending email notification", ex);
            future.completeExceptionally(ex);
        }
        return future;
    }

    MailMessage prepareMailMessage(final Map<String, Object> parameters) throws Exception {
        String recipients = configuration.getTo();

        try {
            recipients = templatize(recipients, parameters);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid email recipient(s)", ex);
        }

        if (recipients == null || recipients.isEmpty()) {
            throw new IllegalArgumentException("Invalid email recipient(s)");
        }

        final MailMessage mailMessage = new MailMessage()
            .setFrom(templatize(configuration.getFrom(), parameters))
            .setTo(Arrays.stream(recipients.split(RECIPIENTS_SPLIT_REGEX)).collect(toList()));

        mailMessage.setSubject(templatize(configuration.getSubject(), parameters));
        String body = configuration
            .getBody()
            // Replace `\n` with <br> tags
            .replace("\n", "<br>");

        addContentInMessage(mailMessage, templatize(body, parameters));

        return mailMessage;
    }

    MailConfig prepareMailConfig() {
        final MailConfig mailConfig = new MailConfig()
            .setHostname(configuration.getHost())
            .setPort(configuration.getPort())
            .setTrustAll(configuration.isSslTrustAll());

        if (hasCredentials()) {
            mailConfig.setUsername(configuration.getUsername());
            mailConfig.setPassword(configuration.getPassword());
        } else {
            mailConfig.setLogin(LoginOption.DISABLED);
        }

        if (nonNull(configuration.getSslKeyStore())) {
            mailConfig.setKeyStore(configuration.getSslKeyStore());
        }
        if (nonNull(configuration.getSslKeyStorePassword())) {
            mailConfig.setKeyStorePassword(configuration.getSslKeyStorePassword());
        }

        if (configuration.isStartTLSEnabled()) {
            mailConfig.setStarttls(StartTLSOptions.REQUIRED);
        } else {
            mailConfig.setStarttls(StartTLSOptions.DISABLED);
        }

        if (hasAuthMethods()) {
            var authMethods = configuration.getAuthMethods().stream().map(String::toUpperCase).collect(joining(" "));
            mailConfig.setAuthMethods(authMethods);
        }

        return mailConfig;
    }

    private boolean hasAuthMethods() {
        return nonNull(configuration.getAuthMethods()) && !configuration.getAuthMethods().isEmpty();
    }

    private boolean hasCredentials() {
        return (
            configuration.getUsername() != null &&
            !configuration.getUsername().isEmpty() &&
            configuration.getPassword() != null &&
            !configuration.getPassword().isEmpty()
        );
    }

    private void addContentInMessage(final MailMessage mailMessage, final String htmlText) throws Exception {
        final Document document = Jsoup.parse(htmlText);
        final Elements imageElements = document.getElementsByTag("img");

        final List<Element> resources = imageElements
            .stream()
            .filter(imageElement -> imageElement.hasAttr("src") && !imageElement.attr("src").startsWith("http"))
            .collect(toList());

        if (!resources.isEmpty()) {
            final List<MailAttachment> mailAttachments = new ArrayList<>(resources.size());
            for (final Element res : resources) {
                final MailAttachment attachment = new MailAttachmentImpl();

                String source = res.attr("src").trim();
                boolean addAttachment = true;
                if (source.startsWith("data:image/")) {
                    final String value = source.replaceFirst("^data:image/[^;]*;base64,?", "");
                    byte[] bytes = Base64.getDecoder().decode(value.getBytes(StandardCharsets.UTF_8));
                    attachment.setContentType(extractMimeType(source));
                    attachment.setData(buffer(bytes));
                } else {
                    File file = new File(templatesPath, source);
                    if (file.getCanonicalPath().startsWith(templatesPath)) {
                        attachment.setContentType(getContentTypeByFileName(source));
                        attachment.setData(buffer(readAllBytes(file.toPath())));
                    } else {
                        logger.warn("Resource path invalid : {}", file.getPath());
                        addAttachment = false;
                    }
                }

                String contentId = UUID.random().toString();
                res.attr("src", "cid:" + contentId);
                attachment.setContentId('<' + contentId + '>');
                attachment.setDisposition("inline");

                if (addAttachment) {
                    mailAttachments.add(attachment);
                }
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
        if (!matcher.find()) return "";
        return matcher.group(1).toLowerCase();
    }

    public String getTemplatesPath() {
        return templatesPath;
    }

    public void setTemplatesPath(String templatesPath) {
        this.templatesPath = templatesPath;
    }
}
