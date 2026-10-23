package com.group5.MyMemories;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group5.MyMemories.entity.Memory;
import com.group5.MyMemories.entity.User;
import com.group5.MyMemories.repositories.MemoryRepository;
import com.group5.MyMemories.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MyMemoriesApplicationTests {
	private static final String TEST_JWT_SECRET = createTestJwtSecret();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private MemoryRepository memoryRepository;

	@DynamicPropertySource
	static void testProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () -> "jdbc:h2:mem:mymemories;DB_CLOSE_DELAY=-1");
		registry.add("spring.datasource.username", () -> "sa");
		registry.add("spring.datasource.password", () -> "");
		registry.add("jwt.secret", () -> TEST_JWT_SECRET);
		registry.add("jwt.expiration", () -> "86400000");
	}

	private static String createTestJwtSecret() {
		byte[] secret = new byte[32];
		new SecureRandom().nextBytes(secret);
		return Base64.getEncoder().encodeToString(secret);
	}

	@Test
	void contextLoads() {
	}

	@Test
	void rootEndpointIsPublicAndReportsServiceStatus() throws Exception {
		mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("MyMemories API is running"));
	}

	@Test
	void protectedRequestsDoNotCreateHttpSession() throws Exception {
		var result = mockMvc.perform(get("/api/private-test"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Unauthorized"))
			.andExpect(jsonPath("$.fieldErrors").isMap())
			.andExpect(jsonPath("$.status").doesNotExist())
			.andReturn();

		assertNull(result.getRequest().getSession(false));
	}

	@Test
	void registerAndLoginReturnJwtWithoutPassword() throws Exception {
		String suffix = UUID.randomUUID().toString();
		String username = "test-" + suffix;
		String email = suffix + "@example.test";
		String password = createTestJwtSecret();

		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username":"%s","email":"%s","password":"%s"}
						""".formatted(username, email, password)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.token").isNotEmpty())
			.andExpect(jsonPath("$.password").doesNotExist());

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s"}
						""".formatted(email, password)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.token").isNotEmpty());
	}

	@Test
	void registrationValidationReturnsFieldErrors() throws Exception {
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username":" ","email":"not-an-email","password":"123"}
						"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Validation failed"))
			.andExpect(jsonPath("$.status").doesNotExist())
			.andExpect(jsonPath("$.fieldErrors.username").value("Username is required"))
			.andExpect(jsonPath("$.fieldErrors.email").value("Email must be valid"))
			.andExpect(jsonPath("$.fieldErrors.password").value("Password must be at least 6 characters"));
	}

	@Test
	void duplicateUsernameReturnsFieldError() throws Exception {
		String username = "duplicate-" + UUID.randomUUID();
		String firstEmail = UUID.randomUUID() + "@example.test";
		String secondEmail = UUID.randomUUID() + "@example.test";
		String password = createTestJwtSecret();

		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username":"%s","email":"%s","password":"%s"}
						""".formatted(username, firstEmail, password)))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username":"%s","email":"%s","password":"%s"}
						""".formatted(username, secondEmail, password)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").doesNotExist())
			.andExpect(jsonPath("$.fieldErrors.username").value("Username already exists"));
	}

	@Test
	void nullAndMalformedLoginBodiesReturnBadRequest() throws Exception {
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("null"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").doesNotExist())
			.andExpect(jsonPath("$.message").value("Request body is required"));

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").doesNotExist())
			.andExpect(jsonPath("$.message").value("Malformed or missing request body"));
	}

	@Test
	void invalidLoginCredentialsUseStandardErrorShape() throws Exception {
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"unknown@example.test","password":"incorrect"}
						"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.status").doesNotExist())
			.andExpect(jsonPath("$.message").value("Invalid email or password"))
			.andExpect(jsonPath("$.fieldErrors.email").value("Invalid email or password"));
	}

	@Test
	void memorySearchMatchesTitleAndContentCaseInsensitivelyForCurrentUser() throws Exception {
		TestAccount account = createTestAccount();
		TestAccount otherAccount = createTestAccount();

		saveMemory(account.user(), "Bright ideas", null);
		saveMemory(account.user(), "Untitled", "A VERY bright day");
		saveMemory(account.user(), "Quiet afternoon", "A peaceful walk");
		saveMemory(otherAccount.user(), "Bright memory", "Not this user's memory");

		mockMvc.perform(get("/api/memories")
				.param("q", "bRiGhT")
				.header("Authorization", "Bearer " + account.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.content.length()").value(2))
			.andExpect(jsonPath("$.content[0].title").value("Untitled"))
			.andExpect(jsonPath("$.content[1].title").value("Bright ideas"))
			.andExpect(jsonPath("$.content[0].user").doesNotExist());
	}

	@Test
	void memoryFiltersCombineTextInclusiveDatesAndAnyOwnerTag() throws Exception {
		TestAccount account = createTestAccount();
		TestAccount otherAccount = createTestAccount();

		createMemory(account, "Summer picnic", "By the lake", "2022-06-15", "family");
		createMemory(account, "Summer trip", "Kyoto", "2023-06-15", "japan");
		createMemory(account, "Quiet picnic", "A peaceful memory", "2022-06-16", "japan");
		createMemory(otherAccount, "Summer picnic", "Other user's record", "2022-06-15", "family");

		mockMvc.perform(get("/api/memories")
				.param("q", "SUMMER")
				.param("from", "2022-06-15")
				.param("to", "2023-06-15")
				.param("tag", " FAMILY ")
				.param("tag", "JAPAN")
				.header("Authorization", "Bearer " + account.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.content[0].title").value("Summer trip"))
			.andExpect(jsonPath("$.content[1].title").value("Summer picnic"));

		mockMvc.perform(get("/api/memories")
				.param("q", "summer")
				.param("from", "2023-06-15")
				.param("to", "2023-06-15")
				.param("tag", "family", "japan")
				.header("Authorization", "Bearer " + account.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].title").value("Summer trip"));
	}

	@Test
	void memoryListCapsPageSizeAtFifty() throws Exception {
		TestAccount account = createTestAccount();
		saveMemory(account.user(), "First", null);
		saveMemory(account.user(), "Second", null);

		mockMvc.perform(get("/api/memories")
				.param("size", "100")
				.header("Authorization", "Bearer " + account.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.size").value(50))
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.content.length()").value(2));
	}

	@Test
	void onThisDayGroupsOwnedMemoriesByYearAndIncludesLeapDayOnFebruaryTwentyEighth() throws Exception {
		TestAccount account = createTestAccount();
		TestAccount otherAccount = createTestAccount();

		createMemory(account, "Feb 28 memory", "2018-02-28");
		createMemory(account, "Leap day memory", "2020-02-29");
		createMemory(account, "Recent Feb 28", "2023-02-28");
		createMemory(account, "Recent leap day", "2024-02-29");
		createMemory(account, "Same year", "2025-02-28");
		createMemory(account, "Different day", "2022-03-01");
		createMemory(otherAccount, "Other user's memory", "2024-02-28");

		mockMvc.perform(get("/api/memories/on-this-day")
				.param("date", "2025-02-28")
				.header("Authorization", "Bearer " + account.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(4))
			.andExpect(jsonPath("$[0].year").value(2024))
			.andExpect(jsonPath("$[0].memories[0].title").value("Recent leap day"))
			.andExpect(jsonPath("$[1].year").value(2023))
			.andExpect(jsonPath("$[1].memories[0].title").value("Recent Feb 28"))
			.andExpect(jsonPath("$[2].year").value(2020))
			.andExpect(jsonPath("$[2].memories[0].title").value("Leap day memory"))
			.andExpect(jsonPath("$[3].year").value(2018))
			.andExpect(jsonPath("$[3].memories[0].title").value("Feb 28 memory"));

		mockMvc.perform(get("/api/memories/on-this-day")
				.param("date", "2024-02-29")
				.header("Authorization", "Bearer " + account.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].year").value(2020))
			.andExpect(jsonPath("$[0].memories[0].title").value("Leap day memory"));

		mockMvc.perform(get("/api/memories/on-this-day")
				.param("date", "2025-02-28"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void resurfacingEndpointsRequireValidClientDate() throws Exception {
		TestAccount account = createTestAccount();

		mockMvc.perform(get("/api/memories/on-this-day")
				.header("Authorization", "Bearer " + account.token()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.date").value("Required parameter is missing"));

		mockMvc.perform(get("/api/memories/resurface")
				.param("date", "not-a-date")
				.header("Authorization", "Bearer " + account.token()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.date").value("Invalid parameter value"));
	}

	@Test
	void resurfacePrefersOnThisDayIsStableForDateAndAvoidsPreviousMemoryWhenPossible() throws Exception {
		TestAccount account = createTestAccount();
		createMemory(account, "On this day", "2018-07-01");
		createMemory(account, "Fallback one", "2018-01-01");
		createMemory(account, "Fallback two", "2019-01-01");

		var firstResponse = mockMvc.perform(get("/api/memories/resurface")
				.param("date", "2025-07-01")
				.header("Authorization", "Bearer " + account.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("On this day"))
			.andReturn();
		long surfacedId = objectMapper.readTree(firstResponse.getResponse().getContentAsString())
				.path("id").asLong();

		mockMvc.perform(get("/api/memories/resurface")
				.param("date", "2025-07-01")
				.header("Authorization", "Bearer " + account.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(surfacedId));

		mockMvc.perform(get("/api/memories/resurface")
				.param("date", "2025-07-02")
				.header("Authorization", "Bearer " + account.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(surfacedId)))
			.andExpect(jsonPath("$.memoryDate").value(org.hamcrest.Matchers.anyOf(
					org.hamcrest.Matchers.is("2018-01-01"),
					org.hamcrest.Matchers.is("2019-01-01"))));
	}

	@Test
	void resurfaceAvoidsPreviousFallbackWhenItIsTheNextDaysOnThisDayMemory() throws Exception {
		TestAccount account = createTestAccount();
		createMemory(account, "Next day's on-this-day", "2018-07-02");
		createMemory(account, "Alternative fallback", "2018-06-01");

		var previousDayResponse = mockMvc.perform(get("/api/memories/resurface")
				.param("date", "2025-07-01")
				.header("Authorization", "Bearer " + account.token()))
			.andExpect(status().isOk())
			.andReturn();
		long previousMemoryId = objectMapper.readTree(previousDayResponse.getResponse().getContentAsString())
				.path("id").asLong();

		mockMvc.perform(get("/api/memories/resurface")
				.param("date", "2025-07-02")
				.header("Authorization", "Bearer " + account.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(previousMemoryId)));
	}

	@Test
	void resurfaceReturnsNoContentWhenNoMemoryIsAtLeastOneYearOld() throws Exception {
		TestAccount account = createTestAccount();
		createMemory(account, "Recent memory", LocalDate.now().toString());

		mockMvc.perform(get("/api/memories/resurface")
				.param("date", LocalDate.now().plusDays(1).toString())
				.header("Authorization", "Bearer " + account.token()))
			.andExpect(status().isNoContent());
	}

	@Test
	void resurfaceFrequencyCanBeReadAndUpdatedAndControlsResurfacing() throws Exception {
		TestAccount account = createTestAccount();
		createMemory(account, "Old memory", "2018-07-01");

		mockMvc.perform(get("/api/users/me/settings")
				.header("Authorization", "Bearer " + account.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.resurfaceFrequency").value("DAILY"));

		mockMvc.perform(put("/api/users/me/settings")
				.header("Authorization", "Bearer " + account.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"resurfaceFrequency":"WEEKLY"}
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.resurfaceFrequency").value("WEEKLY"));

		mockMvc.perform(get("/api/memories/resurface")
				.param("date", "2025-07-01")
				.header("Authorization", "Bearer " + account.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Old memory"));

		mockMvc.perform(get("/api/memories/resurface")
				.param("date", "2025-07-07")
				.header("Authorization", "Bearer " + account.token()))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/memories/resurface")
				.param("date", "2025-07-08")
				.header("Authorization", "Bearer " + account.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Old memory"));

		mockMvc.perform(put("/api/users/me/settings")
				.header("Authorization", "Bearer " + account.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"resurfaceFrequency":"OFF"}
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.resurfaceFrequency").value("OFF"));

		mockMvc.perform(get("/api/memories/resurface")
				.param("date", "2025-07-08")
				.header("Authorization", "Bearer " + account.token()))
			.andExpect(status().isNoContent());

		mockMvc.perform(put("/api/users/me/settings")
				.header("Authorization", "Bearer " + account.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"resurfaceFrequency":"DAILY"}
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.resurfaceFrequency").value("DAILY"));

		mockMvc.perform(get("/api/memories/resurface")
				.param("date", "2025-07-09")
				.header("Authorization", "Bearer " + account.token()))
			.andExpect(status().isOk());

		TestAccount otherAccount = createTestAccount();
		mockMvc.perform(get("/api/users/me/settings")
				.header("Authorization", "Bearer " + otherAccount.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.resurfaceFrequency").value("DAILY"));
	}

	@Test
	void settingsUpdatesValidateFrequencyAndRequireAuthentication() throws Exception {
		TestAccount account = createTestAccount();

		mockMvc.perform(put("/api/users/me/settings")
				.header("Authorization", "Bearer " + account.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"resurfaceFrequency":null}
						"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.resurfaceFrequency").value("Resurface frequency is required"));

		mockMvc.perform(get("/api/users/me/settings"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(put("/api/users/me/settings")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"resurfaceFrequency":"OFF"}
						"""))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void memoriesCanBePagedAndSorted() throws Exception {
		TestAccount account = createTestAccount();
		saveMemory(account.user(), "Charlie", "Third");
		saveMemory(account.user(), "Alpha", "First");
		saveMemory(account.user(), "Bravo", "Second");

		mockMvc.perform(get("/api/memories")
				.param("page", "0")
				.param("size", "2")
				.param("sort", "title,asc")
				.header("Authorization", "Bearer " + account.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.totalPages").value(2))
			.andExpect(jsonPath("$.number").value(0))
			.andExpect(jsonPath("$.size").value(2))
			.andExpect(jsonPath("$.content[0].title").value("Alpha"))
			.andExpect(jsonPath("$.content[1].title").value("Bravo"));

		mockMvc.perform(get("/api/memories")
				.param("page", "1")
				.param("size", "2")
				.param("sort", "title,asc")
				.header("Authorization", "Bearer " + account.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].title").value("Charlie"));
	}

	@Test
	void memoryDateDefaultsToTodayAndIsReturnedWithTimestamps() throws Exception {
		TestAccount account = createTestAccount();

		mockMvc.perform(post("/api/memories")
				.header("Authorization", "Bearer " + account.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"Today"}
						"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.memoryDate").value(LocalDate.now().toString()))
			.andExpect(jsonPath("$.createdAt").isNotEmpty())
			.andExpect(jsonPath("$.updatedAt").isNotEmpty());
	}

	@Test
	void memoryListDefaultsToMemoryDateThenCreatedAtDescending() throws Exception {
		TestAccount account = createTestAccount();
		mockMvc.perform(post("/api/memories")
				.header("Authorization", "Bearer " + account.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"Older event","memoryDate":"2020-01-01"}
						"""))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/memories")
				.header("Authorization", "Bearer " + account.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"Newer event","memoryDate":"2024-01-01"}
						"""))
			.andExpect(status().isCreated());

		mockMvc.perform(get("/api/memories")
				.header("Authorization", "Bearer " + account.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].title").value("Newer event"))
			.andExpect(jsonPath("$.content[1].title").value("Older event"));
	}

	@Test
	void tagsAreNormalizedOwnerScopedAndCounted() throws Exception {
		TestAccount owner = createTestAccount();
		TestAccount otherAccount = createTestAccount();

		var created = mockMvc.perform(post("/api/memories")
				.header("Authorization", "Bearer " + owner.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"Tagged memory","tags":[" Family ","JAPAN","family"]}
						"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.tags.length()").value(2))
			.andExpect(jsonPath("$.tags[0]").value("family"))
			.andExpect(jsonPath("$.tags[1]").value("japan"))
			.andReturn();
		long memoryId = objectMapper.readTree(created.getResponse().getContentAsString())
				.path("id").asLong();

		mockMvc.perform(post("/api/memories")
				.header("Authorization", "Bearer " + owner.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"Another tagged memory","tags":["FAMILY"]}
						"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.tags[0]").value("family"));

		mockMvc.perform(post("/api/memories")
				.header("Authorization", "Bearer " + otherAccount.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"Private tag","tags":["family"]}
						"""))
			.andExpect(status().isCreated());

		mockMvc.perform(get("/api/tags")
				.header("Authorization", "Bearer " + owner.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].name").value("family"))
			.andExpect(jsonPath("$[0].memoryCount").value(2))
			.andExpect(jsonPath("$[1].name").value("japan"))
			.andExpect(jsonPath("$[1].memoryCount").value(1))
			.andExpect(jsonPath("$.length()").value(2));

		mockMvc.perform(put("/api/memories/{id}", memoryId)
				.header("Authorization", "Bearer " + owner.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"Updated tagged memory","tags":["TRAVEL"]}
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tags[0]").value("travel"));

		mockMvc.perform(get("/api/tags")
				.header("Authorization", "Bearer " + owner.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].name").value("family"))
			.andExpect(jsonPath("$[0].memoryCount").value(1))
			.andExpect(jsonPath("$[1].name").value("japan"))
			.andExpect(jsonPath("$[1].memoryCount").value(0))
			.andExpect(jsonPath("$[2].name").value("travel"))
			.andExpect(jsonPath("$[2].memoryCount").value(1))
			.andExpect(jsonPath("$.length()").value(3));

		mockMvc.perform(delete("/api/memories/{id}", memoryId)
				.header("Authorization", "Bearer " + owner.token()))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/tags")
				.header("Authorization", "Bearer " + owner.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].name").value("family"))
			.andExpect(jsonPath("$[0].memoryCount").value(1))
			.andExpect(jsonPath("$[1].name").value("japan"))
			.andExpect(jsonPath("$[1].memoryCount").value(0))
			.andExpect(jsonPath("$[2].name").value("travel"))
			.andExpect(jsonPath("$[2].memoryCount").value(0))
			.andExpect(jsonPath("$.length()").value(3));

		mockMvc.perform(get("/api/tags")
				.header("Authorization", "Bearer " + otherAccount.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].name").value("family"))
			.andExpect(jsonPath("$[0].memoryCount").value(1));

		mockMvc.perform(get("/api/tags"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void tagValidationEnforcesNameLengthAndTagCount() throws Exception {
		TestAccount account = createTestAccount();

		mockMvc.perform(post("/api/memories")
				.header("Authorization", "Bearer " + account.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"Too many tags","tags":["one","two","three","four","five","six","seven","eight","nine","ten","eleven"]}
						"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.tags").value("A memory can have at most 10 tags"));

		mockMvc.perform(post("/api/memories")
				.header("Authorization", "Bearer " + account.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"Long tag","tags":["%s"]}
						""".formatted("x".repeat(31))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.tags").value("Tag name must be at most 30 characters"));

		mockMvc.perform(post("/api/memories")
				.header("Authorization", "Bearer " + account.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"Blank tag","tags":["   "]}
						"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.tags").value("Tag name is required"));
	}

	@Test
	void memoryCrudIsAuthenticatedAndScopedToOwner() throws Exception {
		TestAccount owner = createTestAccount();
		TestAccount otherAccount = createTestAccount();

		var created = mockMvc.perform(post("/api/memories")
				.header("Authorization", "Bearer " + owner.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"First memory","content":"A detail"}
						"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.title").value("First memory"))
			.andExpect(jsonPath("$.content").value("A detail"))
			.andExpect(jsonPath("$.memoryDate").value(LocalDate.now().toString()))
			.andExpect(jsonPath("$.createdAt").isNotEmpty())
			.andExpect(jsonPath("$.updatedAt").isNotEmpty())
			.andExpect(jsonPath("$.user").doesNotExist())
			.andReturn();
		long memoryId = objectMapper.readTree(created.getResponse().getContentAsString())
				.path("id").asLong();

		mockMvc.perform(get("/api/memories/{id}", memoryId)
				.header("Authorization", "Bearer " + owner.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("First memory"));

		mockMvc.perform(put("/api/memories/{id}", memoryId)
				.header("Authorization", "Bearer " + owner.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"Updated memory","content":null}
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Updated memory"))
			.andExpect(jsonPath("$.content").doesNotExist());

		mockMvc.perform(get("/api/memories/{id}", memoryId)
				.header("Authorization", "Bearer " + otherAccount.token()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.status").doesNotExist());

		mockMvc.perform(put("/api/memories/{id}", memoryId)
				.header("Authorization", "Bearer " + otherAccount.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"Not yours","content":"No"}
						"""))
			.andExpect(status().isNotFound());

		mockMvc.perform(delete("/api/memories/{id}", memoryId)
				.header("Authorization", "Bearer " + otherAccount.token()))
			.andExpect(status().isNotFound());

		mockMvc.perform(delete("/api/memories/{id}", memoryId)
				.header("Authorization", "Bearer " + owner.token()))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/memories/{id}", memoryId)
				.header("Authorization", "Bearer " + owner.token()))
			.andExpect(status().isNotFound());
	}

	@Test
	void memoryWriteRequiresNonBlankTitle() throws Exception {
		TestAccount account = createTestAccount();

		mockMvc.perform(post("/api/memories")
				.header("Authorization", "Bearer " + account.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":" ","content":"Missing title"}
						"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").doesNotExist())
			.andExpect(jsonPath("$.fieldErrors.title").value("Title is required"));

		mockMvc.perform(post("/api/memories")
				.header("Authorization", "Bearer " + account.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"%s"}
						""".formatted("x".repeat(151))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.title").value("Title must be at most 150 characters"));

		mockMvc.perform(post("/api/memories")
				.header("Authorization", "Bearer " + account.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"Too much content","content":"%s"}
						""".formatted("x".repeat(10001))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.content").value("Content must be at most 10000 characters"));

		mockMvc.perform(post("/api/memories")
				.header("Authorization", "Bearer " + account.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("null"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Request body is required"));

		mockMvc.perform(post("/api/memories")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"Unauthenticated"}
						"""))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void currentUserEndpointReturnsOnlySafeProfileFields() throws Exception {
		TestAccount account = createTestAccount();

		mockMvc.perform(get("/api/users/me")
				.header("Authorization", "Bearer " + account.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(account.user().getId()))
			.andExpect(jsonPath("$.username").value(account.user().getUsername()))
			.andExpect(jsonPath("$.email").value(account.user().getEmail()))
			.andExpect(jsonPath("$.password").doesNotExist())
			.andExpect(jsonPath("$.memories").doesNotExist());

		mockMvc.perform(get("/api/users/me"))
			.andExpect(status().isUnauthorized());
	}

	private TestAccount createTestAccount() throws Exception {
		String suffix = UUID.randomUUID().toString();
		String username = "memory-test-" + suffix;
		String email = suffix + "@example.test";
		String password = createTestJwtSecret();

		var result = mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username":"%s","email":"%s","password":"%s"}
						""".formatted(username, email, password)))
			.andExpect(status().isCreated())
			.andReturn();

		String token = objectMapper.readTree(result.getResponse().getContentAsString())
				.path("token").asText();
		User user = userRepository.findByEmail(email).orElseThrow();
		return new TestAccount(token, user);
	}

	private void createMemory(
			TestAccount account, String title, String content, String memoryDate, String tag) throws Exception {
		mockMvc.perform(post("/api/memories")
				.header("Authorization", "Bearer " + account.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"%s","content":"%s","memoryDate":"%s","tags":["%s"]}
						""".formatted(title, content, memoryDate, tag)))
			.andExpect(status().isCreated());
	}

	private void createMemory(TestAccount account, String title, String memoryDate) throws Exception {
		mockMvc.perform(post("/api/memories")
				.header("Authorization", "Bearer " + account.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"%s","memoryDate":"%s"}
						""".formatted(title, memoryDate)))
			.andExpect(status().isCreated());
	}

	private void saveMemory(User user, String title, String content) {
		memoryRepository.saveAndFlush(Memory.builder()
				.title(title)
				.content(content)
				.user(user)
				.build());
	}

	private record TestAccount(String token, User user) {
	}

	@Test
	void malformedBearerTokenReturnsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/private-test")
				.header("Authorization", "Bearer not-a-valid-token"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Invalid bearer token"))
			.andExpect(jsonPath("$.fieldErrors").isMap())
			.andExpect(jsonPath("$.status").doesNotExist());
	}

}
