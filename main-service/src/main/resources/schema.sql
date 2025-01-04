CREATE TABLE IF NOT EXISTS users(
  user_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  email VARCHAR(254) NOT NULL,
  name VARCHAR(250) NOT NULL
);

CREATE TABLE IF NOT EXISTS categories(
  category_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  name VARCHAR(50) NOT NULL
);

