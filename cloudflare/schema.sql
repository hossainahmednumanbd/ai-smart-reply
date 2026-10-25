CREATE TABLE IF NOT EXISTS api_keys (
  user_id TEXT PRIMARY KEY,
  provider TEXT NOT NULL CHECK(provider IN ('gemini','openai')),
  secret TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS usage (
  bucket TEXT PRIMARY KEY,
  count INTEGER NOT NULL DEFAULT 0
);
