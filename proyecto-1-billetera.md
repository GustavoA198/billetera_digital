# Proyecto 1 — Billetera digital (monolito por capas)

> Especificación de entrega. Lo que dice aquí es el contrato: se hace como está escrito, no como ya lo sabes hacer.
> Si algo te parece mejor de otra forma, lo discutimos **antes** de programarlo y lo dejamos escrito en la sección 10.

## 1. Qué vas a construir

Una billetera digital pequeña: los clientes se registran, un cajero les recarga o retira efectivo, y los clientes se transfieren dinero entre ellos. Un administrador crea cajeros y bloquea billeteras.

Es pequeño a propósito. La dificultad no está en la cantidad de pantallas sino en que **mover dinero no admite errores**: dos transferencias al mismo tiempo no pueden dejar un saldo negativo, un reintento no puede cobrar dos veces y un fallo a mitad de camino no puede dejar el dinero "en el aire".

## 2. Qué vas a aprender (y qué te van a preguntar en entrevista)

| Tema | Lo que debes dominar al terminar |
|---|---|
| Transacciones | `@Transactional`, cuándo hace reversión (rollback) y cuándo no, propagación, niveles de aislamiento |
| Concurrencia | Bloqueo pesimista y bloqueo optimista, interbloqueos (deadlocks) y cómo evitarlos |
| Idempotencia | Que repetir la misma petición no repita el efecto |
| Manejo de errores | Jerarquía propia de excepciones y una sola clase que las traduce a respuestas HTTP |
| Seguridad | Spring Security, JSON Web Token (JWT) de acceso y de refresco, roles, contraseñas cifradas |
| Base de datos | Migraciones, restricciones, índices justificados con mediciones, disparadores (triggers), identificadores UUID versión 7 |
| Angular | Guardas, interceptores, formularios reactivos, RxJS, servicios, pipes |
| Entrega | Docker Compose, integración continua con GitHub Actions |

## 3. Tecnologías (fijas, no se cambian)

- **Backend:** Java 21, Spring Boot (versión estable actual), Spring Web, Spring Data JPA, Spring Security, Spring Validation.
- **Base de datos:** PostgreSQL 18 (trae la función `uuidv7()`), migraciones con Flyway.
- **Pruebas:** JUnit 5, Mockito y Testcontainers (levanta un PostgreSQL real en Docker para las pruebas de integración).
- **Frontend:** Angular (versión estable actual), componentes independientes (standalone), formularios reactivos, RxJS.
- **Entrega:** Docker, Docker Compose, GitHub Actions.

Todo corre en tu computador. Costo: cero.

## 4. Usuarios y lo que hace cada uno

| Rol | Cómo nace | Qué puede hacer |
|---|---|---|
| `ADMIN` | Lo crea la aplicación al arrancar (sección 7.5). Nunca con un `INSERT` manual | Crear cajeros, bloquear y desbloquear billeteras, ver la bitácora de auditoría |
| `TELLER` (cajero) | Lo crea un `ADMIN` | Registrar recargas y retiros en efectivo sobre la billetera de un cliente |
| `CUSTOMER` (cliente) | Se registra solo; su billetera se crea en la misma transacción | Ver saldo y movimientos, transferir a otro cliente buscándolo por celular |

## 5. Reglas de negocio

Cada regla tiene un código de error. El frontend muestra el mensaje según ese código, no según el texto.

| # | Regla | Si se incumple |
|---|---|---|
| R1 | Una transferencia es atómica: débito, crédito, los dos asientos del libro y el registro de la operación se guardan todos o no se guarda ninguno | — |
| R2 | El saldo nunca queda negativo | `422 INSUFFICIENT_FUNDS` |
| R3 | No se puede transferir a la propia billetera | `422 SAME_WALLET` |
| R4 | Una billetera bloqueada no envía ni recibe | `422 WALLET_BLOCKED` |
| R5 | El monto es mayor que cero y tiene máximo 2 decimales | `400 VALIDATION_ERROR` |
| R6 | Un cliente no puede enviar más de 2.000.000 de pesos por día calendario (hora de Colombia) | `422 DAILY_LIMIT_EXCEEDED` |
| R7 | Toda operación de dinero exige la cabecera `Idempotency-Key`. Misma llave y mismo cuerpo: se devuelve la respuesta original sin mover dinero otra vez. Misma llave y cuerpo distinto: error | `409 IDEMPOTENCY_KEY_REUSED` |
| R8 | Una transferencia rechazada por regla de negocio **queda registrada** con estado `REJECTED` y su código, aunque la transacción principal se haya revertido | — |
| R9 | El libro de movimientos solo crece: nadie puede modificar ni borrar un asiento, ni siquiera desde la base de datos | Error del disparador |
| R10 | Para toda billetera: saldo = suma de créditos − suma de débitos del libro | Prueba de conciliación |
| R11 | Tras 5 intentos fallidos de inicio de sesión la cuenta se bloquea 15 minutos | `423 ACCOUNT_LOCKED` |

## 6. Modelo de datos

Todas las llaves primarias son UUID versión 7. Todo dinero es `NUMERIC(18,2)` en la base y `BigDecimal` en Java.

| Tabla | Columnas clave | Restricciones obligatorias |
|---|---|---|
| `app_user` | `id`, `email`, `phone`, `full_name`, `password_hash`, `role`, `must_change_password`, `failed_login_attempts`, `locked_until`, `created_at` | `email` y `phone` únicos |
| `wallet` | `id`, `user_id`, `balance`, `status` (`ACTIVE`/`BLOCKED`), `version`, `created_at` | `user_id` único, `CHECK (balance >= 0)` |
| `operation` | `id`, `type` (`TRANSFER`/`DEPOSIT`/`WITHDRAWAL`), `source_wallet_id`, `target_wallet_id`, `amount`, `status` (`COMPLETED`/`REJECTED`), `rejection_code`, `idempotency_key`, `request_hash`, `initiated_by`, `created_at` | Único `(initiated_by, idempotency_key)`, `CHECK (amount > 0)` |
| `ledger_entry` | `id`, `operation_id`, `wallet_id`, `direction` (`DEBIT`/`CREDIT`), `amount`, `balance_after`, `created_at` | Solo inserción (disparador) |
| `refresh_token` | `id`, `user_id`, `token_hash`, `expires_at`, `revoked_at`, `replaced_by` | Se guarda el hash, nunca el token |
| `audit_log` | `id`, `table_name`, `record_id`, `action`, `old_value`, `new_value`, `changed_by`, `changed_at` | La llena un disparador |

Una transferencia genera **dos** asientos (un débito y un crédito). Una recarga o un retiro genera **uno**.

## 7. Requisitos técnicos

### 7.1 Estructura por capas

- `controller` → `service` → `repository`. Una capa solo conoce a la de abajo.
- Los controladores reciben y devuelven objetos de transferencia de datos (DTO, Data Transfer Object) escritos como `record`. **Ninguna entidad JPA sale por la API.**
- Las reglas R2 a R6 se implementan con el patrón Estrategia (Strategy): una interfaz `TransferRule` con un método `validate(...)`, una clase por regla, y el servicio recibe la lista completa inyectada. Agregar una regla nueva no debe obligarte a tocar el servicio.

### 7.2 Excepciones

- Una clase abstracta `BusinessException` con tres datos: `code` (texto como `INSUFFICIENT_FUNDS`), `httpStatus` y `message`.
- Una subclase por cada error de negocio de la sección 5.
- **Una sola** clase con `@RestControllerAdvice` traduce todo a una respuesta con el formato estándar Problem Details (documento RFC 9457; Spring trae la clase `ProblemDetail`), agregando los campos `code` y `traceId`.
- Las validaciones de entrada (`@Valid`) devuelven la lista de campos con error en el mismo formato.
- Un error inesperado devuelve `500` con un mensaje genérico. La traza completa va al registro (log), nunca al cliente.

### 7.3 Transacciones y concurrencia

- `@Transactional` va en la capa de servicio, nunca en los controladores.
- R8 se resuelve con una transacción independiente (`Propagation.REQUIRES_NEW`) en **otro** componente. Debes poder explicar por qué no funciona si el método está en la misma clase.
- Concurrencia, primera versión: bloqueo pesimista (`SELECT ... FOR UPDATE`) sobre las dos billeteras, **siempre tomando los bloqueos en orden de identificador** para evitar interbloqueos.
- Concurrencia, segunda versión (en una rama aparte): bloqueo optimista con `@Version` y reintento. En el README comparas las dos con tus propias mediciones.

### 7.4 Base de datos

- Esquema creado solo con migraciones de Flyway. Prohibido `spring.jpa.hibernate.ddl-auto=update`.
- Decide si el UUID versión 7 lo genera la base (`uuidv7()`) o la aplicación, y justifica la decisión.
- Disparador 1: rechaza cualquier `UPDATE` o `DELETE` sobre `ledger_entry` (R9).
- Disparador 2: cada cambio de `wallet.status` deja una fila en `audit_log` con el valor anterior, el nuevo y **el usuario de la aplicación que lo hizo**. Pista: investiga `set_config` y `current_setting` de PostgreSQL.
- Los movimientos se consultan con paginación por cursor (la página siguiente se pide a partir del último registro visto), no con `OFFSET`.
- Índices: carga 1.000.000 de asientos con un script, mide la consulta de movimientos con `EXPLAIN ANALYZE` sin índice y con índice, y pega ambos resultados en el README. Cada índice que crees debe tener esa justificación.

### 7.5 Seguridad y superusuario

- Contraseñas con BCrypt. Nunca aparecen en registros, respuestas ni mensajes de error.
- Token de acceso de 15 minutos. Token de refresco de 7 días, de un solo uso: al usarlo se invalida y se entrega uno nuevo (rotación). Si alguien presenta un token de refresco ya usado, se revocan todos los de ese usuario.
- Autorización por rol en los métodos del servicio, además de las rutas.
- **Superusuario:** al arrancar, si no existe ningún `ADMIN`, la aplicación crea uno con los valores de las variables de entorno `ADMIN_EMAIL` y `ADMIN_PASSWORD`, marcado con `must_change_password = true`. Mientras esa marca esté activa, lo único que puede hacer es cambiar su contraseña. Con el perfil `prod`, si faltan esas variables la aplicación **no arranca**.
- Ningún secreto en el repositorio: ni contraseñas, ni la llave de firma de los tokens. Todo entra por variables de entorno. Incluye un archivo `.env.example` sin valores reales.
- Perfiles `dev`, `test` y `prod`.

### 7.6 API (prefijo `/api/v1`)

| Método y ruta | Rol | Nota |
|---|---|---|
| `POST /auth/register` | Público | Crea cliente y billetera en una transacción |
| `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout` | Público / autenticado | |
| `POST /auth/change-password` | Autenticado | |
| `GET /wallets/me` | `CUSTOMER` | Saldo y estado |
| `GET /wallets/me/movements?cursor=&limit=` | `CUSTOMER` | Paginación por cursor |
| `GET /customers/lookup?phone=` | `CUSTOMER` | Devuelve el nombre enmascarado (`Ju** Pé***`) |
| `POST /transfers` | `CUSTOMER` | Exige `Idempotency-Key` |
| `POST /teller/deposits`, `POST /teller/withdrawals` | `TELLER` | Exigen `Idempotency-Key` |
| `POST /admin/tellers` | `ADMIN` | |
| `PATCH /admin/wallets/{id}/status` | `ADMIN` | |
| `GET /admin/audit-log` | `ADMIN` | |

Documentación generada con OpenAPI (librería springdoc).

### 7.7 Pruebas

- Pruebas unitarias de los servicios y de cada regla con JUnit y Mockito. Cobertura mínima del 80 % en la capa de servicio.
- Pruebas de integración con Testcontainers para: registro, transferencia correcta, cada regla R2 a R8 y los dos disparadores.
- **Prueba de concurrencia:** una billetera con 100.000 pesos y 50 hilos transfiriendo 5.000 cada uno al mismo tiempo. Resultado esperado: exactamente 20 transferencias completadas, 30 rechazadas, saldo final 0, y R10 se cumple.
- **Prueba de idempotencia:** la misma petición enviada 10 veces en paralelo mueve el dinero una sola vez.

### 7.8 Frontend en Angular

Pantallas: inicio de sesión, registro, panel del cliente (saldo y movimientos), transferir, panel del cajero, panel del administrador, cambio de contraseña obligatorio.

- Guardas de ruta por rol.
- Interceptor 1: agrega el token de acceso. Si recibe un `401`, pide un token nuevo **una sola vez** aunque haya varias peticiones en curso, y luego las repite.
- Interceptor 2: convierte el `code` del error en un mensaje para el usuario.
- Formulario de transferencia reactivo, con validadores propios para el monto. La búsqueda del destinatario por celular usa `debounceTime`, `distinctUntilChanged` y `switchMap`.
- Movimientos con botón "cargar más" usando el cursor.
- Un pipe propio para mostrar pesos colombianos y otro para enmascarar el celular.
- Los componentes no llaman a `HttpClient` directamente: todo pasa por servicios. Ninguna suscripción queda abierta al destruir un componente.
- El diseño de las pantallas te lo entrego aparte y debes seguirlo. Para pulir estilos puedes apoyarte en inteligencia artificial; la lógica la escribes tú.

### 7.9 Entrega

- `docker compose up` levanta base de datos, backend y frontend sin pasos manuales.
- GitHub Actions en cada `push` y cada solicitud de cambios (pull request): compila, corre todas las pruebas y construye las imágenes de Docker. La rama principal no acepta cambios con la verificación en rojo.
- README con: diagrama de la arquitectura, cómo correrlo, decisiones tomadas, mediciones de índices y comparación de los dos tipos de bloqueo.

## 8. Hitos

Al terminar cada hito me envías el código y lo reviso antes de que sigas. Yo señalo el problema y explico el porqué; la corrección la escribes tú.

| Hito | Entregable | Horas |
|---|---|---|
| H0 | Repositorio, Docker Compose con PostgreSQL, proyecto base, Flyway y perfiles funcionando | 2 |
| H1 | Migraciones completas: tablas, restricciones, UUID versión 7 y los dos disparadores, con sus pruebas | 4 |
| H2 | Jerarquía de excepciones, manejador global, registro, inicio de sesión, refresco con rotación, superusuario, roles | 6 |
| H3 | Transferencia con R1 a R6 y R8, reglas con el patrón Estrategia, pruebas unitarias | 5 |
| H4 | Bloqueo pesimista, idempotencia (R7), pruebas de concurrencia e idempotencia; rama con bloqueo optimista | 4 |
| H5 | Cajero, administrador, movimientos con cursor, índices medidos | 4 |
| H6 | Frontend completo | 8 |
| H7 | Integración continua, imágenes de Docker, README | 3 |
| | **Total** | **36** |

### Preguntas que debes poder responder sin mirar el código

- **H1:** ¿Por qué UUID versión 7 y no versión 4 como llave primaria? ¿Qué le pasa al índice con cada una?
- **H2:** ¿Por qué se guarda el hash del token de refresco y no el token? ¿Qué ataque detecta la rotación?
- **H3:** Si lanzo una excepción verificada (checked) dentro de un método `@Transactional`, ¿se revierte? ¿Por qué `@Transactional` no hace nada cuando un método llama a otro de la misma clase?
- **H4:** ¿Cuándo conviene bloqueo pesimista y cuándo optimista? ¿Cómo se produce un interbloqueo entre dos transferencias cruzadas y por qué ordenar los bloqueos lo evita?
- **H5:** ¿Por qué la paginación con `OFFSET` se vuelve lenta en la página 10.000 y la de cursor no?
- **H6:** ¿Qué diferencia hay entre `switchMap`, `mergeMap`, `concatMap` y `exhaustMap`, y por qué la búsqueda usa `switchMap`?

## 9. Prohibido

- `double` o `float` para dinero.
- Entidades JPA en los controladores o en las respuestas.
- `try/catch` en los controladores.
- `@Transactional` en los controladores.
- Secretos en el repositorio.
- Lógica de negocio en los componentes de Angular.
- Pedirle a una inteligencia artificial que escriba la lógica. Sí puedes preguntar conceptos, pedir revisión y pedir pistas.

## 10. Decisiones acordadas

_(Aquí anotamos cada cambio a esta especificación, con fecha y motivo.)_
