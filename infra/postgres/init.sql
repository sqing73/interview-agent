-- ============================================================================
-- AI Interview Agent - Core PostgreSQL Schema (Day 1)
-- Compatible with both Spring Data JPA (ddl-auto: validate/update) & R2DBC
-- ============================================================================

-- 1. Users Table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    full_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. Resumes Table (stores raw/extracted PDF text & optional parsed metadata)
CREATE TABLE IF NOT EXISTS resumes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    file_name VARCHAR(255) NOT NULL,
    extracted_text TEXT NOT NULL,
    parsed_metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_resumes_user_id ON resumes(user_id);

-- 3. Interview Sessions Table
-- Tracks interview stage: INTRO, TECHNICAL, BEHAVIORAL, COMPLETED
-- Also stores the asynchronous Day 5 post-interview evaluation report (JSONB)
CREATE TABLE IF NOT EXISTS interview_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    resume_id UUID REFERENCES resumes(id) ON DELETE SET NULL,
    target_role VARCHAR(255) NOT NULL DEFAULT 'Software Engineer',
    stage VARCHAR(50) NOT NULL DEFAULT 'INTRO'
        CHECK (stage IN ('INTRO', 'TECHNICAL', 'BEHAVIORAL', 'COMPLETED')),
    turn_count INTEGER NOT NULL DEFAULT 0,
    evaluation_report JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_interview_sessions_user_id ON interview_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_interview_sessions_resume_id ON interview_sessions(resume_id);
CREATE INDEX IF NOT EXISTS idx_interview_sessions_stage ON interview_sessions(stage);

-- 4. Chat Messages Table
-- Stores turn-by-turn conversation history for context hydration & evaluation
CREATE TABLE IF NOT EXISTS chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES interview_sessions(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL
        CHECK (role IN ('SYSTEM', 'USER', 'ASSISTANT')),
    stage VARCHAR(50) NOT NULL DEFAULT 'INTRO'
        CHECK (stage IN ('INTRO', 'TECHNICAL', 'BEHAVIORAL', 'COMPLETED')),
    content TEXT NOT NULL,
    trace_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_session_id_created_at
    ON chat_messages(session_id, created_at ASC);

-- Seed a default candidate user for local development & testing
INSERT INTO users (id, email, full_name)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'candidate@example.com',
    'Demo Candidate'
)
ON CONFLICT (email) DO NOTHING;
