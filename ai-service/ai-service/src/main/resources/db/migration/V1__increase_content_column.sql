-- Migration: Increase conversation_memory.content column from varchar(3000) to TEXT
-- Database: ai_db (PostgreSQL)
-- Reason: AI scheduling responses (mode 2) exceed 3000 chars, causing PSQLException
-- Run this manually on your PostgreSQL database, OR restart ai-service and Hibernate ddl-auto:update will handle it

ALTER TABLE conversation_memory
ALTER COLUMN content TYPE TEXT;
