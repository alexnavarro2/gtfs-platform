-- Institución y puesto se piden en el registro (contexto de para qué
-- organización/rol usa la plataforma cada usuario); nullable porque el único
-- usuario existente hoy se creó antes de este campo y no tiene con qué
-- rellenarlo retroactivamente — el registro nuevo sí los exige (ver
-- AuthController.RegisterRequest).
ALTER TABLE app_user ADD institution NVARCHAR(MAX);
ALTER TABLE app_user ADD job_title NVARCHAR(MAX);
