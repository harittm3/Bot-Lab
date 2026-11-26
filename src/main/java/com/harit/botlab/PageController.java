package com.harit.botlab;

import java.time.Duration;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.flagsmith.FlagsmithClient;
import com.flagsmith.models.Flags;

import jakarta.servlet.http.HttpServletResponse;

@Controller
public class PageController {

	private static final Logger log = LoggerFactory.getLogger(PageController.class);
	private static final String VISITOR_COOKIE = "visitor_id";
	private static final String BOT_CHECK_FLAG = "bot_check";
	private static final String DEFAULT_CHECK = "image_click";

	private final FlagsmithClient flagsmithClient;
	private final String serverKey;

	public PageController(FlagsmithClient flagsmithClient, @Value("${flagsmith.server-key:}") String serverKey) {
		this.flagsmithClient = flagsmithClient;
		this.serverKey = serverKey;
	}

	@GetMapping("/")
	public String home() {
		return "home";
	}

	@GetMapping("/check")
	public String check(
			@CookieValue(name = VISITOR_COOKIE, required = false) String visitorId,
			HttpServletResponse response,
			Model model) {
		if (visitorId == null || visitorId.isBlank()) {
			// Persist a stable anonymous identity so Flagsmith assigns the same split across refreshes
			visitorId = UUID.randomUUID().toString();
			ResponseCookie cookie = ResponseCookie.from(VISITOR_COOKIE, visitorId)
					.httpOnly(true)
					.sameSite("Lax")
					.path("/")
					.maxAge(Duration.ofDays(365))
					.build();
			response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
		}

		model.addAttribute("visitorId", visitorId);
		model.addAttribute("checkType", resolveBotCheck(visitorId));
		return "check";
	}

	@GetMapping("/thanks")
	public String thanks() {
		return "thanks";
	}

	@GetMapping("/blocked")
	public String blocked() {
		return "blocked";
	}

	private String resolveBotCheck(String visitorId) {
		if (serverKey.isBlank()) {
			// Avoid an unnecessary failing HTTP call when FLAGSMITH_SERVER_KEY is unset
			log.warn("FLAGSMITH_SERVER_KEY is not set; falling back to '{}'", DEFAULT_CHECK);
			return DEFAULT_CHECK;
		}
		try {
			Flags flags = flagsmithClient.getIdentityFlags(visitorId);
			if (!flags.isFeatureEnabled(BOT_CHECK_FLAG)) {
				log.warn("Flagsmith feature '{}' is disabled; falling back to '{}'", BOT_CHECK_FLAG, DEFAULT_CHECK);
				return DEFAULT_CHECK;
			}
			Object rawValue = flags.getFeatureValue(BOT_CHECK_FLAG);
			// Remote Flagsmith API flags store feature_state_value as a Jackson JsonNode (TextNode quotes on toString())
			String value = (rawValue instanceof JsonNode jsonNode)
					? (jsonNode.isNull() ? "" : jsonNode.asText("").trim())
					: (rawValue != null ? rawValue.toString().trim() : "");
			if ("image_click".equals(value) || "cursor_motion".equals(value)) {
				return value;
			}
			log.warn("Unexpected '{}' value '{}'; falling back to '{}'", BOT_CHECK_FLAG, value, DEFAULT_CHECK);
			return DEFAULT_CHECK;
		} catch (Exception e) {
			// Catch network, SDK, and missing-flag errors so visitors are never blocked by Flagsmith outages
			log.warn("Flagsmith lookup failed for visitor '{}'; falling back to '{}'", visitorId, DEFAULT_CHECK, e);
			return DEFAULT_CHECK;
		}
	}

}
