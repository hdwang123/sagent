create table products (
    id bigint primary key,
    name varchar(100) not null,
    category varchar(50) not null,
    price decimal(10, 2) not null,
    stock integer not null
);
