-- Phoenix schema v1. Timestamps are ISO-8601 UTC strings. Keep statements simple: the migration runner
-- splits on semicolons at line ends.

CREATE TABLE job_groups (
    id         INTEGER PRIMARY KEY,
    company    TEXT NOT NULL,
    norm_title TEXT NOT NULL,
    minhash    BLOB,
    created_at TEXT NOT NULL
);

CREATE TABLE jobs (
    id             INTEGER PRIMARY KEY,
    source         TEXT NOT NULL,
    company        TEXT NOT NULL,
    ext_id         TEXT NOT NULL,
    url            TEXT,
    title          TEXT NOT NULL,
    location       TEXT,
    group_id       INTEGER REFERENCES job_groups(id),
    state          TEXT NOT NULL,
    jd_text        TEXT,
    jd_hash        TEXT,
    prescore       INTEGER,
    first_seen     TEXT NOT NULL,
    last_seen      TEXT NOT NULL,
    closed_at      TEXT,
    closing_date   TEXT,
    next_action_at TEXT,
    UNIQUE (source, company, ext_id)
);

CREATE INDEX idx_jobs_state_next_action ON jobs (state, next_action_at);
CREATE INDEX idx_jobs_group ON jobs (group_id);
CREATE INDEX idx_jobs_company_ext ON jobs (company, ext_id);
CREATE INDEX idx_jobs_ext ON jobs (ext_id);

CREATE TABLE scores (
    id             INTEGER PRIMARY KEY,
    job_id         INTEGER NOT NULL REFERENCES jobs(id),
    scorer         TEXT NOT NULL,
    score          REAL NOT NULL,
    fit_pct        INTEGER NOT NULL,
    rationale      TEXT,
    rubric_version TEXT,
    scored_at      TEXT NOT NULL
);

CREATE INDEX idx_scores_job ON scores (job_id);

CREATE TABLE applications (
    id              INTEGER PRIMARY KEY,
    job_id          INTEGER NOT NULL UNIQUE REFERENCES jobs(id),
    state           TEXT NOT NULL,
    attempts        INTEGER NOT NULL DEFAULT 0,
    blocker_type    TEXT,
    blocker_detail  TEXT,
    resume_version  TEXT,
    submitted_at    TEXT,
    confirmation_ref TEXT,
    note            TEXT
);

CREATE TABLE events (
    id         INTEGER PRIMARY KEY,
    job_id     INTEGER NOT NULL REFERENCES jobs(id),
    from_state TEXT,
    to_state   TEXT NOT NULL,
    actor      TEXT NOT NULL,
    note       TEXT,
    at         TEXT NOT NULL
);

CREATE INDEX idx_events_job ON events (job_id);

CREATE TABLE runs (
    id          INTEGER PRIMARY KEY,
    kind        TEXT NOT NULL,
    status      TEXT NOT NULL,
    started_at  TEXT NOT NULL,
    finished_at TEXT,
    checkpoint  TEXT
);

CREATE TABLE asks (
    id          INTEGER PRIMARY KEY,
    job_id      INTEGER REFERENCES jobs(id),
    kind        TEXT NOT NULL,
    prompt      TEXT NOT NULL,
    options     TEXT,
    priority    INTEGER NOT NULL DEFAULT 0,
    status      TEXT NOT NULL,
    answer      TEXT,
    channel     TEXT,
    created_at  TEXT NOT NULL,
    due_at      TEXT,
    answered_at TEXT
);

CREATE INDEX idx_asks_status ON asks (status);

CREATE TABLE approvals (
    id          INTEGER PRIMARY KEY,
    scope       TEXT NOT NULL,
    granted_via TEXT NOT NULL,
    user_words  TEXT NOT NULL,
    granted_at  TEXT NOT NULL,
    expires_at  TEXT NOT NULL,
    consumed_at TEXT
);

CREATE INDEX idx_approvals_scope ON approvals (scope);

CREATE TABLE answers (
    id            INTEGER PRIMARY KEY,
    question_norm TEXT NOT NULL UNIQUE,
    answer        TEXT NOT NULL,
    source        TEXT,
    updated_at    TEXT NOT NULL
);

CREATE TABLE referrals (
    id           INTEGER PRIMARY KEY,
    name         TEXT NOT NULL,
    connection   TEXT,
    company      TEXT,
    job_ref      TEXT,
    status       TEXT,
    last_contact TEXT,
    follow_up    TEXT,
    note         TEXT
);

CREATE TABLE source_health (
    source               TEXT NOT NULL,
    board                TEXT NOT NULL,
    last_ok              TEXT,
    last_count           INTEGER,
    consecutive_failures INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (source, board)
);
