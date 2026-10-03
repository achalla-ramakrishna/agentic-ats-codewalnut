package com.codewalnut.ats.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.codewalnut.ats.client.AssessmentDrafter;
import com.codewalnut.ats.client.ClaudeAssessmentDrafter;
import com.codewalnut.ats.client.ClaudeResumeAnalyzer;
import com.codewalnut.ats.client.DisabledAssessmentDrafter;
import com.codewalnut.ats.client.SampleAssessmentDrafter;
import com.codewalnut.ats.client.DisabledResumeAnalyzer;
import com.codewalnut.ats.client.KeywordResumeAnalyzer;
import com.codewalnut.ats.client.ResumeAnalyzer;
import java.util.Optional;
import com.codewalnut.ats.client.AssistantClient;
import com.codewalnut.ats.client.CalendarClient;
import com.codewalnut.ats.client.ClaudeAssistantClient;
import com.codewalnut.ats.client.DisabledAssistantClient;
import com.codewalnut.ats.client.RuleBasedAssistantClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import com.codewalnut.ats.client.FakeCalendarClient;
import com.codewalnut.ats.client.FakeMailClient;
import com.codewalnut.ats.client.GmailClient;
import com.codewalnut.ats.client.GoogleAccess;
import com.codewalnut.ats.client.GoogleCalendarClient;
import com.codewalnut.ats.client.MailClient;
import com.codewalnut.ats.client.FakeWhatsAppClient;
import com.codewalnut.ats.client.WhatsAppClient;
import com.codewalnut.ats.client.WhatsAppCloudClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.web.client.RestClient;

/**
 * Real Google Calendar, Gmail, WhatsApp and Claude in production; fakes in dev and demo so nothing is
 * ever sent from them.
 */
@Configuration
public class IntegrationsConfig {

    @Bean
    @Profile({"dev", "demo"})
    public FakeCalendarClient fakeCalendarClient() {
        return new FakeCalendarClient();
    }

    @Bean
    @Profile({"dev", "demo"})
    public FakeMailClient fakeMailClient() {
        return new FakeMailClient();
    }

    @Bean
    @Profile("!dev & !demo")
    public GoogleAccess googleAccess(ObjectProvider<ClientRegistrationRepository> registrations,
            OAuth2AuthorizedClientRepository authorizedClients) {
        return new GoogleAccess(registrations.getIfAvailable(), authorizedClients);
    }

    @Bean
    @Profile("!dev & !demo")
    public CalendarClient googleCalendarClient(GoogleAccess google, RestClient.Builder restClientBuilder) {
        return new GoogleCalendarClient(google, restClientBuilder.clone().baseUrl(GoogleCalendarClient.BASE_URL).build());
    }

    @Bean
    @Profile("!dev & !demo")
    public MailClient gmailClient(GoogleAccess google, RestClient.Builder restClientBuilder) {
        return new GmailClient(google, restClientBuilder.clone().baseUrl(GmailClient.BASE_URL).build());
    }

    @Bean
    @Profile({"dev", "demo"})
    public FakeWhatsAppClient fakeWhatsAppClient() {
        return new FakeWhatsAppClient();
    }

    @Bean
    @Profile("!dev & !demo")
    public WhatsAppClient whatsAppClient(WhatsAppProperties properties, RestClient.Builder restClientBuilder) {
        return new WhatsAppCloudClient(properties, restClientBuilder.clone().baseUrl(WhatsAppCloudClient.BASE_URL).build());
    }

    @Bean
    @Profile({"dev", "demo"})
    public AssistantClient ruleBasedAssistantClient() {
        return new RuleBasedAssistantClient();
    }

    @Bean
    @Profile({"dev", "demo"})
    public AssessmentDrafter sampleAssessmentDrafter() {
        return new SampleAssessmentDrafter();
    }

    /** Claude drafts test questions when ANTHROPIC_API_KEY is set; otherwise they're written by hand. */
    @Bean
    @Profile("!dev & !demo")
    public AssessmentDrafter assessmentDrafter(@Value("${ANTHROPIC_API_KEY:}") String apiKey,
            @Value("${ats.assistant.model:claude-opus-5-5}") String model) {
        return anthropic(apiKey).<AssessmentDrafter>map(c -> new ClaudeAssessmentDrafter(c, model))
                .orElseGet(DisabledAssessmentDrafter::new);
    }

    @Bean
    @Profile({"dev", "demo"})
    public ResumeAnalyzer keywordResumeAnalyzer() {
        return new KeywordResumeAnalyzer();
    }

    /** Claude when ANTHROPIC_API_KEY is set; otherwise the assistant is off. */
    @Bean
    @Profile("!dev & !demo")
    public AssistantClient assistantClient(@Value("${ANTHROPIC_API_KEY:}") String apiKey,
            @Value("${ats.assistant.model:claude-opus-5-5}") String model) {
        return anthropic(apiKey).<AssistantClient>map(c -> new ClaudeAssistantClient(c, model))
                .orElseGet(DisabledAssistantClient::new);
    }

    /** Claude reads résumés when ANTHROPIC_API_KEY is set; otherwise résumé reading is off. */
    @Bean
    @Profile("!dev & !demo")
    public ResumeAnalyzer resumeAnalyzer(@Value("${ANTHROPIC_API_KEY:}") String apiKey,
            @Value("${ats.assistant.model:claude-opus-5-5}") String model) {
        return anthropic(apiKey).<ResumeAnalyzer>map(c -> new ClaudeResumeAnalyzer(c, model))
                .orElseGet(DisabledResumeAnalyzer::new);
    }

    private static Optional<AnthropicClient> anthropic(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(AnthropicOkHttpClient.builder()
                .apiKey(apiKey.trim())
                .timeout(Duration.ofSeconds(120))
                .maxRetries(2)
                .build());
    }
}
