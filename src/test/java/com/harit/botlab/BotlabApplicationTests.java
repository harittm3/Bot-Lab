package com.harit.botlab;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagsmith.FlagsmithClient;
import com.flagsmith.models.Flags;

import jakarta.servlet.http.Cookie;

@SpringBootTest
class BotlabApplicationTests {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private FlagsmithClient flagsmithClient;

	@Test
	void contextLoadsAndRoutesWorkWithFallback() throws Exception {
		assertThat(flagsmithClient).isNotNull();
		MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

		mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Bot-Defense Lab")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("/check")));

		MvcResult checkResult = mockMvc.perform(get("/check"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.allOf(
						org.hamcrest.Matchers.containsString("visitor_id="),
						org.hamcrest.Matchers.containsString("Path=/"),
						org.hamcrest.Matchers.containsString("Max-Age=31536000"),
						org.hamcrest.Matchers.containsString("HttpOnly"),
						org.hamcrest.Matchers.containsString("SameSite=Lax")
				)))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Check type: <strong>image_click</strong>")))
				.andReturn();

		String setCookie = checkResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
		String visitorId = setCookie.substring("visitor_id=".length(), setCookie.indexOf(';'));
		assertThat(checkResult.getResponse().getContentAsString()).contains(visitorId);

		mockMvc.perform(get("/check").cookie(new Cookie("visitor_id", visitorId)))
				.andExpect(status().isOk())
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
				.andExpect(content().string(org.hamcrest.Matchers.containsString(visitorId)));

		mockMvc.perform(get("/thanks")).andExpect(status().isOk());
		mockMvc.perform(get("/blocked")).andExpect(status().isOk());
	}

	@Test
	void flagsmithJackson2ParsingAndVariantSelectionWork() throws Exception {
		String apiJson = """
				[
				  {
				    "feature": { "id": 1, "name": "bot_check", "type": "MULTIVARIATE" },
				    "enabled": true,
				    "feature_state_value": "cursor_motion"
				  }
				]
				""";
		JsonNode node = new ObjectMapper().readTree(apiJson);
		Flags flags = Flags.fromApiFlags(node, null, null);

		FlagsmithClient mockClient = mock(FlagsmithClient.class);
		when(mockClient.getIdentityFlags("abc-123")).thenReturn(flags);

		PageController controller = new PageController(mockClient, "ser.dummy-for-test");
		ConcurrentModel model = new ConcurrentModel();
		MockHttpServletResponse response = new MockHttpServletResponse();

		String view = controller.check("abc-123", response, model);
		assertThat(view).isEqualTo("check");
		assertThat(model.getAttribute("visitorId")).isEqualTo("abc-123");
		assertThat(model.getAttribute("checkType")).isEqualTo("cursor_motion");
	}

}
