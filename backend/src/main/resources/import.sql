-- Dados iniciais carregados no H2 a cada subida da aplicacao.
-- Os ids sao gerados pelo banco (IDENTITY): usuarios 1..3, transacoes 1..45.

-- Usuarios (3)
INSERT INTO users (name, email) VALUES ('Ana Souza', 'ana.souza@example.com');
INSERT INTO users (name, email) VALUES ('Bruno Lima', 'bruno.lima@example.com');
INSERT INTO users (name, email) VALUES ('Carla Mendes', 'carla.mendes@example.com');

-- Transacoes PIX (15)
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (1, 'PIX', 4827.52, TIMESTAMP '2026-10-26 17:18:36');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (2, 'PIX', 4373.74, TIMESTAMP '2026-09-05 19:04:40');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (3, 'PIX', 1646.37, TIMESTAMP '2026-08-23 18:40:49');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (1, 'PIX', 4068.32, TIMESTAMP '2026-11-09 20:54:42');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (2, 'PIX', 4168.13, TIMESTAMP '2026-10-07 14:47:12');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (3, 'PIX', 663.64, TIMESTAMP '2026-10-09 21:05:07');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (1, 'PIX', 1287.04, TIMESTAMP '2026-11-05 13:34:56');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (2, 'PIX', 2349.24, TIMESTAMP '2026-11-11 08:07:07');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (3, 'PIX', 61.30, TIMESTAMP '2026-09-03 10:32:52');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (1, 'PIX', 1488.45, TIMESTAMP '2026-09-29 10:24:18');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (2, 'PIX', 428.18, TIMESTAMP '2026-10-28 14:44:48');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (3, 'PIX', 2452.38, TIMESTAMP '2026-11-03 10:19:17');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (1, 'PIX', 27.76, TIMESTAMP '2026-09-08 20:34:59');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (2, 'PIX', 289.18, TIMESTAMP '2026-09-10 21:54:35');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (3, 'PIX', 1187.93, TIMESTAMP '2026-11-04 20:11:37');

-- Transacoes BOLETO (15)
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (1, 'BOLETO', 2004.73, TIMESTAMP '2026-10-22 11:03:48');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (2, 'BOLETO', 4473.64, TIMESTAMP '2026-08-26 20:47:25');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (3, 'BOLETO', 4037.79, TIMESTAMP '2026-08-24 20:00:44');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (1, 'BOLETO', 3934.63, TIMESTAMP '2026-10-02 10:31:53');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (2, 'BOLETO', 3286.02, TIMESTAMP '2026-08-30 21:36:10');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (3, 'BOLETO', 3050.31, TIMESTAMP '2026-11-13 13:45:08');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (1, 'BOLETO', 2959.48, TIMESTAMP '2026-09-11 20:08:19');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (2, 'BOLETO', 2207.35, TIMESTAMP '2026-10-16 12:11:32');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (3, 'BOLETO', 1601.58, TIMESTAMP '2026-11-04 17:05:10');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (1, 'BOLETO', 188.99, TIMESTAMP '2026-10-29 13:13:46');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (2, 'BOLETO', 2031.82, TIMESTAMP '2026-10-26 20:49:21');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (3, 'BOLETO', 696.20, TIMESTAMP '2026-09-22 15:36:28');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (1, 'BOLETO', 4816.24, TIMESTAMP '2026-10-10 16:42:45');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (2, 'BOLETO', 1298.45, TIMESTAMP '2026-10-22 11:59:59');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (3, 'BOLETO', 3123.20, TIMESTAMP '2026-09-13 17:00:56');

-- Transacoes CARTAO (15)
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (1, 'CARTAO', 4925.14, TIMESTAMP '2026-10-15 20:34:24');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (2, 'CARTAO', 4636.50, TIMESTAMP '2026-10-06 17:56:40');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (3, 'CARTAO', 2917.62, TIMESTAMP '2026-09-18 13:37:33');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (1, 'CARTAO', 29.55, TIMESTAMP '2026-08-21 21:01:25');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (2, 'CARTAO', 4597.67, TIMESTAMP '2026-09-25 18:49:25');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (3, 'CARTAO', 1330.24, TIMESTAMP '2026-09-07 18:08:23');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (1, 'CARTAO', 2427.59, TIMESTAMP '2026-10-12 11:41:41');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (2, 'CARTAO', 1331.99, TIMESTAMP '2026-09-30 11:33:05');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (3, 'CARTAO', 2146.84, TIMESTAMP '2026-09-18 16:49:05');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (1, 'CARTAO', 3944.82, TIMESTAMP '2026-10-17 14:20:23');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (2, 'CARTAO', 2910.95, TIMESTAMP '2026-09-03 16:58:20');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (3, 'CARTAO', 3280.83, TIMESTAMP '2026-10-16 18:55:31');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (1, 'CARTAO', 3757.12, TIMESTAMP '2026-11-06 18:32:46');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (2, 'CARTAO', 3319.47, TIMESTAMP '2026-09-02 16:13:42');
INSERT INTO transactions (user_id, type, amount, created_at) VALUES (3, 'CARTAO', 2575.08, TIMESTAMP '2026-11-03 17:06:57');
