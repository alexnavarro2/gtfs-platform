import { QueryClient } from '@tanstack/react-query';

// Instancia compartida: useAppStore la usa para limpiar el caché en cada
// cambio de sesión (login/registro/logout) — ver setAuth/clearAuth. Si cada
// módulo creara su propio QueryClient no compartirían caché ni podrían
// limpiarse entre sí.
export const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } },
});
