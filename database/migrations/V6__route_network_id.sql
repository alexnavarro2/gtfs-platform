-- Permite asociar una ruta a una red (network_id de routes.txt) para poder
-- referenciarla desde fare_leg_rules.txt sin necesitar networks.txt/
-- route_networks.txt aparte (el spec permite declarar network_id directo en
-- routes.txt cuando no hay múltiples rutas compartiendo una red).
ALTER TABLE route ADD COLUMN network_id TEXT;
