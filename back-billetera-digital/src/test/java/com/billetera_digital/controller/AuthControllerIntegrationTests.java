package com.billetera_digital.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.billetera_digital.BaseIntegrationTest;
import com.billetera_digital.entity.AppUser;
import com.billetera_digital.repository.AppUserRepository;

/**
 * Pruebas de integración del endpoint de registro contra un PostgreSQL real.
 *
 * Cada test usa un correo y un celular propios: las tres clases de prueba de
 * integración comparten una sola base de datos, así que ningún dato puede repetirse.
 */
class AuthControllerIntegrationTests extends BaseIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private AppUserRepository appUserRepository;

	@Test
	@DisplayName("Un registro válido devuelve 201 con el usuario creado y su billetera en cero")
	void registroCorrectoDevuelve201() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "Ana@Correo.com",
								  "phone": "3001234567",
								  "fullName": "Ana Torres",
								  "password": "clave12345"
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value("ana@correo.com"))
				.andExpect(jsonPath("$.role").value("CUSTOMER"))
				.andExpect(jsonPath("$.id").isNotEmpty());

		AppUser usuario = appUserRepository.findByEmail("ana@correo.com").orElseThrow();

		BigDecimal saldo = jdbcTemplate.queryForObject("SELECT balance FROM wallet WHERE user_id = ?", BigDecimal.class,
				usuario.getId());

		assertThat(saldo).isEqualByComparingTo(BigDecimal.ZERO);
	}

	@Test
	@DisplayName("Un correo ya registrado devuelve 409 EMAIL_ALREADY_REGISTERED")
	void correoRepetidoDevuelve409() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "duplicado-correo@correo.com",
								  "phone": "3011234567",
								  "fullName": "Usuario Original",
								  "password": "clave12345"
								}
								"""))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "duplicado-correo@correo.com",
								  "phone": "3021234567",
								  "fullName": "Usuario Repetido",
								  "password": "clave12345"
								}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
	}

	@Test
	@DisplayName("Un celular ya registrado devuelve 409 PHONE_ALREADY_REGISTERED")
	void celularRepetidoDevuelve409() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "duplicado-celular@correo.com",
								  "phone": "3031234567",
								  "fullName": "Usuario Original",
								  "password": "clave12345"
								}
								"""))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "otro-correo@correo.com",
								  "phone": "3031234567",
								  "fullName": "Usuario Repetido",
								  "password": "clave12345"
								}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PHONE_ALREADY_REGISTERED"));
	}

	@Test
	@DisplayName("Datos inválidos devuelven 400 VALIDATION_ERROR con el detalle por campo")
	void datosInvalidosDevuelve400() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "correo-sin-formato",
								  "phone": "",
								  "fullName": "",
								  "password": "corta"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[*].field", hasItem("email")))
				.andExpect(jsonPath("$.errors[*].field", hasItem("phone")))
				.andExpect(jsonPath("$.errors[*].field", hasItem("fullName")))
				.andExpect(jsonPath("$.errors[*].field", hasItem("password")));
	}

	@Test
	@DisplayName("La respuesta nunca expone la contraseña ni su hash")
	void laRespuestaNoExponeLaContrasena() throws Exception {
		String cuerpo = mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "secreto@correo.com",
								  "phone": "3041234567",
								  "fullName": "Usuario Secreto",
								  "password": "clave12345"
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
				.andReturn()
				.getResponse()
				.getContentAsString();

		assertThat(cuerpo.toLowerCase()).doesNotContain("passwordhash");
		assertThat(cuerpo.toLowerCase()).doesNotContain("password");

		String hash = jdbcTemplate.queryForObject("SELECT password_hash FROM app_user WHERE email = ?", String.class,
				"secreto@correo.com");

		assertThat(hash).isNotEqualTo("clave12345");
		assertThat(hash).startsWith("$2");
	}
}
