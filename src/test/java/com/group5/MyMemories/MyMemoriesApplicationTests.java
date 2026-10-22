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
	void protectedRequestsDoNotCreateHttpSession() throws Exception {
		var result = mockMvc.perform(get("/api/private-test"))
			.andExpect(status().isUnauthorized())
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
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.message").value("Validation failed"))
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
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.fieldErrors.username").value("Username already exists"));
	}

	@Test
	void nullAndMalformedLoginBodiesReturnBadRequest() throws Exception {
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("null"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.message").value("Request body is required"));

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
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
			.andExpect(jsonPath("$.status").value(401))
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
			.andExpect(jsonPath("$.status").value(404));

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
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.fieldErrors.title").value("Title is required"));

		mockMvc.perform(post("/api/memories")
				.header("Authorization", "Bearer " + account.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"%s"}
						""".formatted("x".repeat(256))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.title").value("Title must be at most 255 characters"));

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
			.andExpect(status().isUnauthorized());
	}

}
