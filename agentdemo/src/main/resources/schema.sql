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
