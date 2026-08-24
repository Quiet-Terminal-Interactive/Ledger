CREATE TABLE activities (
    id uuid NOT NULL,
    activity character varying(255) NOT NULL,
    metadata oid,
    "timestamp" bigint NOT NULL,
    task_id uuid NOT NULL,
    user_id uuid,
    CONSTRAINT activities_pkey PRIMARY KEY (id),
    CONSTRAINT activities_activity_check CHECK (((activity)::text = ANY ((ARRAY['CREATED'::character varying, 'CLAIMED'::character varying, 'ASSIGNED'::character varying, 'UNASSIGNED'::character varying, 'BLOCKED'::character varying, 'UNBLOCKED'::character varying, 'COMPLETED'::character varying, 'TITLE_UPDATED'::character varying, 'DESCRIPTION_UPDATED'::character varying, 'DUE_DATE_UPDATED'::character varying, 'STATUS_UPDATED'::character varying])::text[])))
);

CREATE TABLE budget_entries (
    id uuid NOT NULL,
    amount numeric(12,2) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    description character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    type character varying(255) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    created_by uuid NOT NULL,
    CONSTRAINT budget_entries_pkey PRIMARY KEY (id),
    CONSTRAINT budget_entries_type_check CHECK (((type)::text = ANY ((ARRAY['INCOME'::character varying, 'EXPENSE'::character varying])::text[])))
);

CREATE TABLE sessions (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    user_agent character varying(255),
    user_id uuid NOT NULL,
    CONSTRAINT sessions_pkey PRIMARY KEY (id)
);

CREATE TABLE tasks (
    id uuid NOT NULL,
    description character varying(255),
    due_date date,
    status character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    CONSTRAINT tasks_pkey PRIMARY KEY (id),
    CONSTRAINT tasks_status_check CHECK (((status)::text = ANY ((ARRAY['COMPLETED'::character varying, 'INPROGRESS'::character varying, 'NOTSTARTED'::character varying, 'BLOCKED'::character varying])::text[])))
);

CREATE TABLE task_assignees (
    task_id uuid NOT NULL,
    user_id uuid NOT NULL,
    CONSTRAINT task_assignees_pkey PRIMARY KEY (task_id, user_id)
);

CREATE TABLE task_blocks (
    task_id uuid NOT NULL,
    blocker_id uuid NOT NULL,
    CONSTRAINT task_blocks_pkey PRIMARY KEY (task_id, blocker_id)
);

CREATE TABLE uploads (
    id uuid NOT NULL,
    bucket character varying(255) NOT NULL,
    content_type character varying(255),
    file_name character varying(255) NOT NULL,
    object_key character varying(255) NOT NULL,
    size_bytes bigint NOT NULL,
    uploaded_at timestamp(6) with time zone NOT NULL,
    uploaded_by uuid NOT NULL,
    CONSTRAINT uploads_pkey PRIMARY KEY (id),
    CONSTRAINT uploads_object_key_key UNIQUE (object_key)
);

CREATE TABLE roles (
    id uuid NOT NULL,
    name character varying(255) NOT NULL,
    built_in boolean NOT NULL,
    CONSTRAINT roles_pkey PRIMARY KEY (id),
    CONSTRAINT roles_name_key UNIQUE (name)
);

CREATE TABLE role_permissions (
    role_id uuid NOT NULL,
    permission character varying(255) NOT NULL,
    CONSTRAINT role_permissions_pkey PRIMARY KEY (role_id, permission),
    CONSTRAINT role_permissions_permission_check CHECK (((permission)::text = ANY ((ARRAY[
        'TASKS_READ'::character varying, 'TASKS_WRITE'::character varying,
        'BUDGET_READ'::character varying, 'BUDGET_WRITE'::character varying,
        'WIKI_READ'::character varying, 'WIKI_WRITE'::character varying,
        'FILES_READ'::character varying, 'FILES_WRITE'::character varying,
        'MAIL_READ'::character varying, 'SEARCH_READ'::character varying, 'REPOS_READ'::character varying,
        'USERS_MANAGE'::character varying, 'ROLES_MANAGE'::character varying
    ])::text[])))
);

CREATE TABLE users (
    id uuid NOT NULL,
    first_name character varying(255) NOT NULL,
    last_name character varying(255) NOT NULL,
    role_id uuid NOT NULL,
    CONSTRAINT users_pkey PRIMARY KEY (id)
);

CREATE TABLE user_credentials (
    id uuid NOT NULL,
    password_hash character varying(255) NOT NULL,
    user_id uuid NOT NULL,
    username character varying(255) NOT NULL,
    CONSTRAINT user_credentials_pkey PRIMARY KEY (id),
    CONSTRAINT user_credentials_username_key UNIQUE (username)
);

CREATE TABLE recovery_codes (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    code_hash character varying(255) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    used_at timestamp(6) with time zone,
    CONSTRAINT recovery_codes_pkey PRIMARY KEY (id)
);

CREATE TABLE wiki_pages (
    id uuid NOT NULL,
    content text NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    path character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    created_by uuid NOT NULL,
    CONSTRAINT wiki_pages_pkey PRIMARY KEY (id),
    CONSTRAINT wiki_pages_path_key UNIQUE (path)
);

ALTER TABLE ONLY activities
    ADD CONSTRAINT activities_task_id_fkey FOREIGN KEY (task_id) REFERENCES tasks(id),
    ADD CONSTRAINT activities_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE ONLY budget_entries
    ADD CONSTRAINT budget_entries_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id);

ALTER TABLE ONLY task_assignees
    ADD CONSTRAINT task_assignees_task_id_fkey FOREIGN KEY (task_id) REFERENCES tasks(id),
    ADD CONSTRAINT task_assignees_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE ONLY task_blocks
    ADD CONSTRAINT task_blocks_task_id_fkey FOREIGN KEY (task_id) REFERENCES tasks(id),
    ADD CONSTRAINT task_blocks_blocker_id_fkey FOREIGN KEY (blocker_id) REFERENCES tasks(id);

ALTER TABLE ONLY uploads
    ADD CONSTRAINT uploads_uploaded_by_fkey FOREIGN KEY (uploaded_by) REFERENCES users(id);

ALTER TABLE ONLY role_permissions
    ADD CONSTRAINT role_permissions_role_id_fkey FOREIGN KEY (role_id) REFERENCES roles(id);

ALTER TABLE ONLY users
    ADD CONSTRAINT users_role_id_fkey FOREIGN KEY (role_id) REFERENCES roles(id);

ALTER TABLE ONLY recovery_codes
    ADD CONSTRAINT recovery_codes_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE ONLY wiki_pages
    ADD CONSTRAINT wiki_pages_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id);

CREATE INDEX recovery_codes_user_id_idx ON recovery_codes (user_id);

CREATE INDEX tasks_search_idx ON tasks
    USING GIN (to_tsvector('english', coalesce(title, '') || ' ' || coalesce(description, '')));

CREATE INDEX wiki_pages_search_idx ON wiki_pages
    USING GIN (to_tsvector('english', coalesce(title, '') || ' ' || coalesce(content, '')));

CREATE INDEX uploads_search_idx ON uploads
    USING GIN (to_tsvector('english', regexp_replace(coalesce(file_name, ''), '[^[:alnum:]]+', ' ', 'g')));

INSERT INTO roles (id, name, built_in) VALUES
    (gen_random_uuid(), 'Admin', true),
    (gen_random_uuid(), 'Member', false);

INSERT INTO role_permissions (role_id, permission)
    SELECT id, permission
    FROM roles, unnest(ARRAY[
        'TASKS_READ', 'TASKS_WRITE', 'BUDGET_READ', 'BUDGET_WRITE', 'WIKI_READ', 'WIKI_WRITE',
        'FILES_READ', 'FILES_WRITE', 'MAIL_READ', 'SEARCH_READ', 'REPOS_READ',
        'USERS_MANAGE', 'ROLES_MANAGE'
    ]) AS permission
    WHERE roles.name = 'Admin';

INSERT INTO role_permissions (role_id, permission)
    SELECT id, permission
    FROM roles, unnest(ARRAY[
        'TASKS_READ', 'TASKS_WRITE', 'BUDGET_READ', 'BUDGET_WRITE', 'WIKI_READ', 'WIKI_WRITE',
        'FILES_READ', 'FILES_WRITE', 'MAIL_READ', 'SEARCH_READ', 'REPOS_READ'
    ]) AS permission
    WHERE roles.name = 'Member';
