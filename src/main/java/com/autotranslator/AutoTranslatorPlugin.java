package com.autotranslator;

import com.deepl.api.DeepLClient;
import com.deepl.api.DeepLException;
import com.google.inject.Provides;

import javax.inject.Inject;

import com.optimaize.langdetect.DetectedLanguage;
import com.optimaize.langdetect.LanguageDetector;
import com.optimaize.langdetect.LanguageDetectorBuilder;
import com.optimaize.langdetect.ngram.NgramExtractors;
import com.optimaize.langdetect.profiles.LanguageProfile;
import com.optimaize.langdetect.profiles.LanguageProfileReader;
import com.optimaize.langdetect.text.CommonTextObjectFactories;
import com.optimaize.langdetect.text.TextObjectFactory;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
@PluginDescriptor(
        name = "AutoTranslator",
        description = "A Runelite Build-in Translator using DeepL Translation service and optimaize for language detection"
)
public class AutoTranslatorPlugin extends Plugin {

    private static final String TRANSLATOR_HEADER = "AutoTranslator: ";
    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private AutoTranslatorPluginConfig config;
    @Inject
    private ScheduledExecutorService scheduledExecutorService;
    private DeepLClient deepLClient;
    private LanguageDetector languageDetector;
    private TextObjectFactory factory;
    private List<LanguageProfile> languageProfiles;

    @Override
    protected void startUp() throws Exception {
        if (!config.apiKey().isEmpty()) {
            this.deepLClient = new DeepLClient(config.apiKey());
        }
        //load all languages build-in
        languageProfiles = new LanguageProfileReader().readAllBuiltIn();
        //build language detector:
        this.languageDetector = LanguageDetectorBuilder.create(NgramExtractors.standard())
                .withProfiles(languageProfiles)
                .probabilityThreshold(config.minimumProbability())
                .build();
        //Build the TextObjectFactory
        this.factory = CommonTextObjectFactories.forDetectingShortCleanText();
    }

    @Override
    protected void shutDown() throws Exception {
        this.deepLClient = null;
        this.languageDetector = null;
        this.factory = null;
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        if (this.deepLClient == null) {
            return;
        }
        if (chatMessage.getName().startsWith(TRANSLATOR_HEADER)) {
            return;
        }
        switch (chatMessage.getType()) {
            case PUBLICCHAT:
            case MODCHAT:
            case FRIENDSCHAT:
            case PRIVATECHAT:
            case MODPRIVATECHAT:
            case PRIVATECHATOUT:
            case CLAN_CHAT:
            case CLAN_GUEST_CHAT:
            case CLAN_GIM_CHAT:
                break;
            default:
                return;
        }
        String trimmed = chatMessage.getMessage().trim();
        if (trimmed.matches("\\b[A-Za-z]+[^A-Za-z]+[A-Za-z]+.*")) {
            List<DetectedLanguage> detectedLanguageOutPut = languageDetector.getProbabilities(factory.forText(trimmed));
            if (!detectedLanguageOutPut.isEmpty()) {
                String[] languages = config.languagesToTranslate().split("\\s*,\\s*");
                for (String language : languages) {
                    String detectedLanguage = detectedLanguageOutPut.get(0).getLocale().getLanguage();
                    if (detectedLanguage.equals(language)) {
                        scheduledExecutorService.execute(() -> translateAndPrintMessage(chatMessage, detectedLanguage));
                    }
                }
            }

        }
    }


    private void translateAndPrintMessage(ChatMessage chatMessage, String language) {
        try {
            String translated = deepLClient.translateText(chatMessage.getMessage(), language, config.targetLanguage()).getText();
            clientThread.invoke(() -> client.addChatMessage(chatMessage.getType(), TRANSLATOR_HEADER + chatMessage.getName(), translated, chatMessage.getSender()));
        } catch (DeepLException | InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    @Subscribe
    public void onConfigChanged(ConfigChanged configChanged) {
        if (configChanged.getGroup().equals("AutoTranslator")) {
            if (configChanged.getKey().equals("apiKey")) {
                this.deepLClient = null;
                if (!config.apiKey().isEmpty()) {
                    this.deepLClient = new DeepLClient(config.apiKey());
                }
            }
            if(configChanged.getKey().equals("probability")){
                this.languageDetector = LanguageDetectorBuilder.create(NgramExtractors.standard())
                        .withProfiles(languageProfiles)
                        .probabilityThreshold(config.minimumProbability())
                        .build();
            }
        }
    }

    @Provides
    AutoTranslatorPluginConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoTranslatorPluginConfig.class);
    }
}
