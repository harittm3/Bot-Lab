package com.harit.botlab;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.flagsmith.FlagsmithClient;

@Configuration
public class FlagsmithConfig {

	private static final Logger log = LoggerFactory.getLogger(FlagsmithConfig.class);

	@Bean
	public FlagsmithClient flagsmithClient(@Value("${flagsmith.server-key:}") String serverKey) {
		if (serverKey.isBlank()) {
			// Warn early at startup without failing boot when running locally without a key
			log.warn("FLAGSMITH_SERVER_KEY is not set; bot_check will fall back to 'image_click'");
		}
		// Keep one shared client bean so OkHttp connection pools and config are reused across requests
		return FlagsmithClient.newBuilder()
				.setApiKey(serverKey)
				.build();
	}

}
