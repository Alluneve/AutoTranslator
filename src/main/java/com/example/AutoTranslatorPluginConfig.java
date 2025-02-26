package com.example;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("AutoTranslator")
public interface AutoTranslatorPluginConfig extends Config
{
	@ConfigItem(
		keyName = "apiKey",
		name = "DeepL Api key",
		description = "the Api Key from DeepL"
	)
	default String apiKey()
	{
		return "";
	}

	@ConfigItem(keyName = "targetLanguage",
			name = "Target Language",
			description = "Language to be translated to")
	default String targetLanguage(){ return "en-GB";}

	@ConfigItem(keyName = "sourceLanguage",
	name = "Languages to be Translated",
	description = "ISO 639-1 Language Codes, Comma Separated")
	default String languagesToTranslate(){
		return "fr,de,es,it";
	}

	@ConfigItem(keyName = "probability",
			name = "the minimum probability",
			description = "number between 0 and 0.999999")
	default double minimumProbability(){
		return 0.90;
	}
}
