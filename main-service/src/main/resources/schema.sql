CREATE TABLE IF NOT EXISTS users(
  user_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  email VARCHAR(254) NOT NULL,
  name VARCHAR(250) NOT NULL
);

CREATE TABLE IF NOT EXISTS categories(
  category_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  name VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS events(
    event_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    annotation varchar(2000),
    category_id BIGINT NOT NULL references categories(category_id),
    confirmed_requests int,
    created_on timestamp,
    description varchar(7000),
    event_date timestamp NOT NULL,
    initiator_id BIGINT NOT NULL references users(user_id),
    paid BOOLEAN,
    participant_limit int,
    state varchar(50) NOT NULL,
    request_moderation BOOLEAN,
    title varchar(120),
    lat FLOAT,
    lon FLOAT
);

