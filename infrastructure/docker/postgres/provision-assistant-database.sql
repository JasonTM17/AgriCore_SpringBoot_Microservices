SELECT 'CREATE DATABASE agricore_assistant'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'agricore_assistant')
\gexec
