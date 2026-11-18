package com.billetera_digital.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.billetera_digital.BaseIntegrationTest;

/**
 * Pruebas de integración contra un PostgreSQL real (Testcontainers).
 *
 * <p>Los tests corren contra el esquema que dejan las migraciones de Flyway, no contra
 * una copia del esquema: si una migración y el código se desynchronizan, el test falla.
 *
 * <p>Reglas de aislamiento:
 * <ul>
 * <li>Los datos de prueba se crean con identificadores únicos (correo, llave de
 * idempotencia), así que los tests no se pisan entre sí.</li>
 * <li>Las operaciones que deben fallar se ejecutan dentro de una transacción que se
 * revierte, de modo que la base queda como estaba y se puede verificar que el dato
 * original no se movió.</li>
 * </ul>
 *
 * <p>Cada test afirma <em>una</em> regla y no "algo falló": además del tipo de
 * excepción, se comprueba el nombre de la restricción que saltó, que es lo que
 * PostgreSQL devuelve en el mensaje. Si un INSERT dejara de ser válido por otra razón
 * (una columna mal escrita, un tipo equivocado), el test cae.
 */
class DatabaseIntegrationTests extends BaseIntegrationTest {

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private TransactionTemplate transaccion;

	private Escenario escenario;

	@BeforeEach
	void prepararEscenario() {
		this.transaccion = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
		this.escenario = crearEscenario();
	}

	// ------------------------------------------------------------------
	// Migraciones
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Las cuatro migraciones están aplicadas y son correctas")
	void lasMigracionesEstanAplicadas() {
		List<Map<String, Object>> historial = jdbcTemplate.queryForList(
				"SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank");

		assertThat(historial).hasSize(4);
		assertThat(historial).allSatisfy(fila -> assertThat(fila.get("success")).isEqualTo(Boolean.TRUE));
	}

	// ------------------------------------------------------------------
	// Disparador 1: ledger_entry es append-only
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Disparador 1: un UPDATE sobre un asiento lanza excepción")
	void disparador1_noPermiteActualizarUnAsiento() {
		assertThatThrownBy(() -> enTransaccion(() -> jdbcTemplate.update(
				"UPDATE ledger_entry SET amount = ? WHERE id = ?", new BigDecimal("999.00"), escenario.ledgerEntryId())))
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("No se puede eliminar o modificar");

		assertThat(montoDelAsiento()).isEqualByComparingTo("1500.00");
	}

	@Test
	@DisplayName("Disparador 1: un DELETE sobre un asiento lanza excepción")
	void disparador1_noPermiteBorrarUnAsiento() {
		assertThatThrownBy(() -> enTransaccion(
				() -> jdbcTemplate.update("DELETE FROM ledger_entry WHERE id = ?", escenario.ledgerEntryId())))
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("No se puede eliminar o modificar");

		assertThat(cantidadDeAsientos()).isEqualTo(1);
	}

	@Test
	@DisplayName("Disparador 1: un TRUNCATE sobre ledger_entry lanza excepción")
	void disparador1_noPermiteTruncarLosAsientos() {
		assertThatThrownBy(() -> enTransaccion(() -> jdbcTemplate.execute("TRUNCATE ledger_entry")))
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("No se puede eliminar o modificar");

		assertThat(cantidadDeAsientos()).isEqualTo(1);
	}

	// ------------------------------------------------------------------
	// Disparador 2: cada cambio de wallet.status queda auditado
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Disparador 2: un cambio de status queda auditado con el responsable")
	void disparador2_cambioDeStatusQuedaAuditado() {
		enTransaccion(() -> {
			definirResponsable(escenario.userId());
			jdbcTemplate.update("UPDATE wallet SET status = 'BLOCKED' WHERE id = ?", escenario.walletId());
		});

		Map<String, Object> auditoria = jdbcTemplate.queryForMap(
				"SELECT table_name, record_id, action, old_value, new_value, changed_by FROM audit_log WHERE record_id = ?",
				escenario.walletId());

		assertThat(auditoria.get("table_name")).isEqualTo("wallet");
		assertThat(auditoria.get("record_id")).isEqualTo(escenario.walletId());
		assertThat(auditoria.get("action")).isEqualTo("UPDATE");
		assertThat(auditoria.get("old_value")).isEqualTo("ACTIVE");
		assertThat(auditoria.get("new_value")).isEqualTo("BLOCKED");
		assertThat(auditoria.get("changed_by")).isEqualTo(escenario.userId());
		assertThat(estadoDeLaBilletera()).isEqualTo("BLOCKED");
	}

	@Test
	@DisplayName("Disparador 2: un UPDATE que no cambia el status no se audita")
	void disparador2_updateSinCambioDeStatusNoSeAudita() {
		enTransaccion(() -> {
			definirResponsable(escenario.userId());
			jdbcTemplate.update("UPDATE wallet SET status = 'ACTIVE' WHERE id = ?", escenario.walletId());
		});

		assertThat(cantidadDeAuditorias()).isZero();
	}

	@Test
	@DisplayName("Disparador 2: un UPDATE que ni siquiera menciona el status no se audita")
	void disparador2_updateDeOtroCampoNoSeAudita() {
		enTransaccion(() -> {
			definirResponsable(escenario.userId());
			jdbcTemplate.update("UPDATE wallet SET balance = ? WHERE id = ?", new BigDecimal("1600.00"),
					escenario.walletId());
		});

		assertThat(cantidadDeAuditorias()).isZero();
		assertThat(saldoDeLaBilletera()).isEqualByComparingTo("1600.00");
	}

	@Test
	@DisplayName("Disparador 2: sin responsable definido la auditoría falla")
	void disparador2_sinResponsableLaAuditoriaFalla() {
		assertThatThrownBy(() -> enTransaccion(
				() -> jdbcTemplate.update("UPDATE wallet SET status = 'BLOCKED' WHERE id = ?", escenario.walletId())))
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("falta definir app.current_user_id");

		assertThat(cantidadDeAuditorias()).isZero();
		assertThat(estadoDeLaBilletera()).isEqualTo("ACTIVE");
	}

	@Test
	@DisplayName("Disparador 2: un INSERT de billetera no se audita")
	void disparador2_insertDeBilleteraNoSeAudita() {
		UUID walletId = crearBilletera(crearUsuario(), new BigDecimal("0.00"));

		assertThat(cantidadDeAuditorias(walletId)).isZero();
	}

	// ------------------------------------------------------------------
	// Restricciones: saldo, monto y correo
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Restricción: el saldo de una billetera no puede ser negativo")
	void elSaldoNoPuedeSerNegativo() {
		assertThatThrownBy(() -> enTransaccion(() -> jdbcTemplate.update(
				"UPDATE wallet SET balance = ? WHERE id = ?", new BigDecimal("-0.01"), escenario.walletId())))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("wallet_balance_check");

		assertThat(saldoDeLaBilletera()).isEqualByComparingTo("1500.00");
	}

	@Test
	@DisplayName("Restricción: el monto de una operación tiene que ser mayor que cero")
	void elMontoNoPuedeSerCero() {
		assertThatThrownBy(() -> enTransaccion(() -> jdbcTemplate.update(
				"INSERT INTO operation (type, target_wallet_id, amount, status, idempotency_key, request_hash, initiated_by) "
						+ "VALUES ('DEPOSIT', ?, 0.00, 'COMPLETED', ?, ?, ?)",
				escenario.walletId(), "cero-" + UUID.randomUUID(), "hash", escenario.userId())))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("operation_amount_check");
	}

	@Test
	@DisplayName("Restricción: el correo de un usuario no puede repetirse")
	void elCorreoNoPuedeRepetirse() {
		assertThatThrownBy(() -> enTransaccion(() -> jdbcTemplate.update(
				"INSERT INTO app_user (email, full_name, password_hash, role) VALUES (?, ?, ?, 'CUSTOMER')",
				escenario.email(), "Otro Usuario", "hash-de-prueba")))
				.isInstanceOf(DuplicateKeyException.class)
				.hasMessageContaining("app_user_email_key");
	}

	// ------------------------------------------------------------------
	// Restricción: idempotencia
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Restricción: la llave de idempotencia no puede repetirse para el mismo usuario")
	void laLlaveDeIdempotenciaNoPuedeRepetirse() {
		assertThatThrownBy(() -> enTransaccion(() -> jdbcTemplate.update(
				"INSERT INTO operation (type, target_wallet_id, amount, status, idempotency_key, request_hash, initiated_by) "
						+ "VALUES ('DEPOSIT', ?, ?, 'COMPLETED', ?, ?, ?)",
				escenario.walletId(), new BigDecimal("10.00"), escenario.idempotencyKey(), "hash", escenario.userId())))
				.isInstanceOf(DuplicateKeyException.class)
				.hasMessageContaining("operation_initiated_by_idempotency_key_unique");
	}

	@Test
	@DisplayName("Restricción: una operación COMPLETED sin código de rechazo sí se guarda")
	void unaOperacionCompletedSinCodigoDeRechazoSeGuarda() {
		UUID operationId = crearOperacionDelEscenario("DEPOSIT", null, escenario.walletId(), new BigDecimal("25.00"),
				"COMPLETED", null);

		assertThat(operationId).isNotNull();
		assertThat(jdbcTemplate.queryForObject("SELECT rejection_code IS NULL FROM operation WHERE id = ?", Boolean.class,
				operationId)).isTrue();
		assertThat(jdbcTemplate.queryForObject("SELECT status::text FROM operation WHERE id = ?", String.class, operationId))
				.isEqualTo("COMPLETED");
	}

	@Test
	@DisplayName("Restricción: una operación REJECTED sin código de rechazo no se guarda")
	void unaOperacionRejectedSinCodigoDeRechazoNoSeGuarda() {
		assertThatThrownBy(() -> enTransaccion(() -> jdbcTemplate.update(
				"INSERT INTO operation (type, target_wallet_id, amount, status, idempotency_key, request_hash, initiated_by) "
						+ "VALUES ('DEPOSIT', ?, ?, 'REJECTED', ?, ?, ?)",
				escenario.walletId(), new BigDecimal("25.00"), "rechazada-" + UUID.randomUUID(), "hash", escenario.userId())))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("operation_rejection_code_by_status");
	}

	// ------------------------------------------------------------------
	// Restricción: las billeteras según el tipo de operación
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Restricción: una recarga no puede tener billetera de origen")
	void unaRecargaConBilleteraDeOrigenNoSeGuarda() {
		assertThatThrownBy(() -> enTransaccion(() -> jdbcTemplate.update(
				"INSERT INTO operation (type, source_wallet_id, target_wallet_id, amount, status, idempotency_key, request_hash, initiated_by) "
						+ "VALUES ('DEPOSIT', ?, ?, ?, 'COMPLETED', ?, ?, ?)",
				escenario.walletId(), escenario.walletId(), new BigDecimal("10.00"), "recarga-" + UUID.randomUUID(), "hash",
				escenario.userId())))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("operation_wallets_by_type_check");
	}

	@Test
	@DisplayName("Restricción: una transferencia necesita billetera de origen y de destino")
	void unaTransferenciaSinBilleteraDeOrigenNoSeGuarda() {
		assertThatThrownBy(() -> enTransaccion(() -> jdbcTemplate.update(
				"INSERT INTO operation (type, source_wallet_id, target_wallet_id, amount, status, idempotency_key, request_hash, initiated_by) "
						+ "VALUES ('TRANSFER', NULL, ?, ?, 'COMPLETED', ?, ?, ?)",
				escenario.walletId(), new BigDecimal("10.00"), "transferencia-" + UUID.randomUUID(), "hash",
				escenario.userId())))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("operation_wallets_by_type_check");
	}

	@Test
	@DisplayName("Restricción: una transferencia sin billetera de destino no se guarda")
	void unaTransferenciaSinBilleteraDeDestinoNoSeGuarda() {
		assertThatThrownBy(() -> enTransaccion(() -> jdbcTemplate.update(
				"INSERT INTO operation (type, source_wallet_id, target_wallet_id, amount, status, idempotency_key, request_hash, initiated_by) "
						+ "VALUES ('TRANSFER', ?, NULL, ?, 'COMPLETED', ?, ?, ?)",
				escenario.walletId(), new BigDecimal("10.00"), "transferencia-" + UUID.randomUUID(), "hash",
				escenario.userId())))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("operation_wallets_by_type_check");
	}

	@Test
	@DisplayName("Restricción: una recarga sin billetera de origen sí se guarda")
	void unaRecargaSinBilleteraDeOrigenSeGuarda() {
		UUID operationId = crearOperacionDelEscenario("DEPOSIT", null, escenario.walletId(), new BigDecimal("25.00"),
				"COMPLETED", null);

		assertThat(operationId).isNotNull();
	}

	@Test
	@DisplayName("Restricción: una transferencia con las dos billeteras sí se guarda")
	void unaTransferenciaConLasDosBilleterasSeGuarda() {
		UUID walletDestino = crearBilletera(crearUsuario(), new BigDecimal("100.00"));

		UUID operationId = crearOperacionDelEscenario("TRANSFER", escenario.walletId(), walletDestino,
				new BigDecimal("25.00"), "COMPLETED", null);

		assertThat(operationId).isNotNull();
	}

	// ------------------------------------------------------------------
	// Utilidades
	// ------------------------------------------------------------------

	/**
	 * Ejecuta el cuerpo dentro de una transacción. Si el cuerpo falla, la transacción se
	 * revierte y la excepción se propaga tal cual.
	 */
	private void enTransaccion(Runnable cuerpo) {
		transaccion.executeWithoutResult(status -> cuerpo.run());
	}

	/**
	 * Define el usuario de la aplicación responsable del cambio. El disparador de
	 * auditoría lo lee con {@code current_setting('app.current_user_id', true)}.
	 *
	 * <p>El alcance es el de la transacción: al confirmarla o revertirla, el valor se
	 * pierde. Por eso ningún test puede dejarlo puesto por accidente.
	 */
	private void definirResponsable(UUID userId) {
		jdbcTemplate.queryForObject("SELECT set_config('app.current_user_id', ?, true)", String.class, userId.toString());
	}

	private Escenario crearEscenario() {
		String email = "usuario-" + UUID.randomUUID() + "@test.local";
		UUID userId = jdbcTemplate.queryForObject(
				"INSERT INTO app_user (email, full_name, password_hash, role) VALUES (?, ?, ?, 'CUSTOMER') RETURNING id",
				UUID.class, email, "Usuario de Prueba", "hash-de-prueba");
		UUID walletId = crearBilletera(userId, new BigDecimal("1500.00"));
		String idempotencyKey = "dep-" + UUID.randomUUID();
		UUID operationId = crearOperacion("DEPOSIT", null, walletId, new BigDecimal("1500.00"), "COMPLETED", null,
				idempotencyKey, userId);
		UUID ledgerEntryId = crearAsiento(operationId, walletId, new BigDecimal("1500.00"));
		return new Escenario(userId, email, walletId, operationId, idempotencyKey, ledgerEntryId);
	}

	private UUID crearUsuario() {
		return jdbcTemplate.queryForObject(
				"INSERT INTO app_user (email, full_name, password_hash, role) VALUES (?, ?, ?, 'CUSTOMER') RETURNING id",
				UUID.class, "usuario-" + UUID.randomUUID() + "@test.local", "Usuario de Prueba", "hash-de-prueba");
	}

	private UUID crearBilletera(UUID userId, BigDecimal balance) {
		return jdbcTemplate.queryForObject("INSERT INTO wallet (user_id, balance) VALUES (?, ?) RETURNING id", UUID.class,
				userId, balance);
	}

	private UUID crearOperacionDelEscenario(String type, UUID sourceWallet, UUID targetWallet, BigDecimal amount, String status,
			String rejectionCode) {
		return crearOperacion(type, sourceWallet, targetWallet, amount, status, rejectionCode, "op-" + UUID.randomUUID(),
				escenario.userId());
	}

	/**
	 * Los casts {@code ?::operation_type} y {@code ?::operation_status} no son
	 * opcionales: el driver de PostgreSQL manda los parámetros {@link String} con el tipo
	 * {@code varchar}, y PostgreSQL no hace el cast implícito de {@code varchar} a un tipo
	 * ENUM. Sin los casts, el INSERT falla con {@code column "status" is of type
	 * operation_status but expression is of type character varying}.
	 */
	private UUID crearOperacion(String type, UUID sourceWallet, UUID targetWallet, BigDecimal amount, String status,
			String rejectionCode, String idempotencyKey, UUID initiatedBy) {
		return jdbcTemplate.queryForObject(
				"INSERT INTO operation (type, source_wallet_id, target_wallet_id, amount, status, rejection_code, idempotency_key, request_hash, initiated_by) "
						+ "VALUES (?::operation_type, ?, ?, ?, ?::operation_status, ?, ?, ?, ?) RETURNING id",
				UUID.class, type, sourceWallet, targetWallet, amount, status, rejectionCode, idempotencyKey,
				"hash-" + UUID.randomUUID(), initiatedBy);
	}

	private UUID crearAsiento(UUID operationId, UUID walletId, BigDecimal amount) {
		return jdbcTemplate.queryForObject(
				"INSERT INTO ledger_entry (operation_id, wallet_id, direction, amount, balance_after) "
						+ "VALUES (?, ?, 'CREDIT', ?, ?) RETURNING id",
				UUID.class, operationId, walletId, amount, amount);
	}

	private BigDecimal montoDelAsiento() {
		return jdbcTemplate.queryForObject("SELECT amount FROM ledger_entry WHERE id = ?", BigDecimal.class,
				escenario.ledgerEntryId());
	}

	private int cantidadDeAsientos() {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM ledger_entry WHERE id = ?", Integer.class,
				escenario.ledgerEntryId());
	}

	private int cantidadDeAuditorias() {
		return cantidadDeAuditorias(escenario.walletId());
	}

	private int cantidadDeAuditorias(UUID walletId) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM audit_log WHERE record_id = ?", Integer.class, walletId);
	}

	private BigDecimal saldoDeLaBilletera() {
		return jdbcTemplate.queryForObject("SELECT balance FROM wallet WHERE id = ?", BigDecimal.class, escenario.walletId());
	}

	private String estadoDeLaBilletera() {
		return jdbcTemplate.queryForObject("SELECT status::text FROM wallet WHERE id = ?", String.class, escenario.walletId());
	}

	private record Escenario(UUID userId, String email, UUID walletId, UUID operationId, String idempotencyKey,
			UUID ledgerEntryId) {
	}

}
