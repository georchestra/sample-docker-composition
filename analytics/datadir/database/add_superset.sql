CREATE USER superset WITH ENCRYPTED PASSWORD 'superset';
CREATE SCHEMA AUTHORIZATION superset;
ALTER ROLE superset SET search_path = superset;
