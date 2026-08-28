-- app_user ya existía como catálogo de referencia (created_by/updated_by en
-- feed, stop, etc.) pero nunca se usó para autenticar: no había columna de
-- contraseña ni login. Se agrega para soportar registro/login reales.
ALTER TABLE app_user ADD password_hash NVARCHAR(MAX) NOT NULL;
