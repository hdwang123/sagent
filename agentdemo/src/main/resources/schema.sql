create table products (
    id bigint primary key,
    name varchar(100) not null,
    category varchar(50) not null,
    price decimal(10, 2) not null,
    stock integer not null
);

create table approval_records (
    id varchar(36) primary key,
    user_id varchar(64) not null,
    tool_name varchar(64) not null,
    args_json varchar(2048) not null,
    status varchar(16) not null default 'PENDING',
    result varchar(4096),
    auto_response varchar(4096),
    create_time timestamp not null default current_timestamp,
    update_time timestamp
);

-- 审计日志表
CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_id VARCHAR(128),
    operation_detail TEXT,
    status VARCHAR(16) NOT NULL,
    error_message TEXT,
    duration_ms BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_user_created ON audit_log(user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_audit_operation_created ON audit_log(operation_type, created_at);
CREATE INDEX IF NOT EXISTS idx_audit_resource_created ON audit_log(resource_type, resource_id, created_at);

-- 成本记录表
CREATE TABLE IF NOT EXISTS cost_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    model_name VARCHAR(64) NOT NULL,
    input_tokens BIGINT NOT NULL,
    output_tokens BIGINT NOT NULL,
    total_tokens BIGINT NOT NULL,
    cost_cny DECIMAL(10, 6) NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    conversation_id VARCHAR(128),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cost_user_created ON cost_record(user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_cost_model_created ON cost_record(model_name, created_at);
