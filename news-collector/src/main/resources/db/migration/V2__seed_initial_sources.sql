-- Seed initial publishers and RSS endpoints

-- 1) PUBLISHERS
-- BBC
INSERT INTO content.publishers (name, country_code, language_code, website_url)
VALUES ('BBC News', 'GB', 'en-gb', 'https://www.bbc.com')
ON CONFLICT (LOWER(name)) DO UPDATE
    SET country_code = EXCLUDED.country_code,
        language_code = EXCLUDED.language_code,
        website_url = EXCLUDED.website_url;

-- NPR
INSERT INTO content.publishers (name, country_code, language_code, website_url)
VALUES ('NPR', 'US', 'en', 'https://www.npr.org')
ON CONFLICT (LOWER(name)) DO UPDATE
    SET country_code = EXCLUDED.country_code,
        language_code = EXCLUDED.language_code,
        website_url = EXCLUDED.website_url;

-- The Guardian
INSERT INTO content.publishers (name, country_code, language_code, website_url)
VALUES ('The Guardian', 'GB', 'en', 'https://www.theguardian.com')
ON CONFLICT (LOWER(name)) DO UPDATE
    SET country_code = EXCLUDED.country_code,
        language_code = EXCLUDED.language_code,
        website_url = EXCLUDED.website_url;

-- Al Jazeera
INSERT INTO content.publishers (name, country_code, language_code, website_url)
VALUES ('Al Jazeera', NULL, 'en', 'https://www.aljazeera.com')
ON CONFLICT (LOWER(name)) DO UPDATE
    SET language_code = EXCLUDED.language_code,
        website_url = EXCLUDED.website_url;

-- Euronews
INSERT INTO content.publishers (name, country_code, language_code, website_url)
VALUES ('Euronews', 'EU', 'en', 'https://www.euronews.com')
ON CONFLICT (LOWER(name)) DO UPDATE
    SET country_code = EXCLUDED.country_code,
        language_code = EXCLUDED.language_code,
        website_url = EXCLUDED.website_url;

-- France 24
INSERT INTO content.publishers (name, country_code, language_code, website_url)
VALUES ('France 24', 'FR', 'en', 'https://www.france24.com')
ON CONFLICT (LOWER(name)) DO UPDATE
    SET country_code = EXCLUDED.country_code,
        language_code = EXCLUDED.language_code,
        website_url = EXCLUDED.website_url;

-- ABC News (Australia)
INSERT INTO content.publishers (name, country_code, language_code, website_url)
VALUES ('ABC News (Australia)', 'AU', 'en', 'https://www.abc.net.au')
ON CONFLICT (LOWER(name)) DO UPDATE
    SET country_code = EXCLUDED.country_code,
        language_code = EXCLUDED.language_code,
        website_url = EXCLUDED.website_url;

-- 2) SOURCE ENDPOINTS (RSS feeds)

-- BBC publisher id
WITH bbc AS (
    SELECT id FROM content.publishers WHERE LOWER(name) = LOWER('BBC News')
)
INSERT INTO content.source_endpoints (publisher_id, kind, display_name, rss_url, enabled, fetch_interval_minutes)
SELECT bbc.id, 'RSS', 'BBC News - Top Stories (Global)', 'https://feeds.bbci.co.uk/news/rss.xml', TRUE, 15
FROM bbc
ON CONFLICT (publisher_id, kind, COALESCE(rss_url, ''), COALESCE(api_provider, ''), COALESCE(api_query, ''))
    DO NOTHING;

WITH bbc AS (
    SELECT id FROM content.publishers WHERE LOWER(name) = LOWER('BBC News')
)
INSERT INTO content.source_endpoints (publisher_id, kind, display_name, rss_url, enabled, fetch_interval_minutes)
SELECT bbc.id, 'RSS', 'BBC News - UK', 'https://feeds.bbci.co.uk/news/uk/rss.xml', TRUE, 15
FROM bbc
ON CONFLICT (publisher_id, kind, COALESCE(rss_url, ''), COALESCE(api_provider, ''), COALESCE(api_query, ''))
    DO NOTHING;

WITH bbc AS (
    SELECT id FROM content.publishers WHERE LOWER(name) = LOWER('BBC News')
)
INSERT INTO content.source_endpoints (publisher_id, kind, display_name, rss_url, enabled, fetch_interval_minutes)
SELECT bbc.id, 'RSS', 'BBC News - World', 'https://feeds.bbci.co.uk/news/world/rss.xml', TRUE, 15
FROM bbc
ON CONFLICT (publisher_id, kind, COALESCE(rss_url, ''), COALESCE(api_provider, ''), COALESCE(api_query, ''))
    DO NOTHING;

WITH bbc AS (
    SELECT id FROM content.publishers WHERE LOWER(name) = LOWER('BBC News')
)
INSERT INTO content.source_endpoints (publisher_id, kind, display_name, rss_url, enabled, fetch_interval_minutes)
SELECT bbc.id, 'RSS', 'BBC News - Africa', 'https://feeds.bbci.co.uk/news/world/africa/rss.xml', TRUE, 15
FROM bbc
ON CONFLICT (publisher_id, kind, COALESCE(rss_url, ''), COALESCE(api_provider, ''), COALESCE(api_query, ''))
    DO NOTHING;

WITH bbc AS (
    SELECT id FROM content.publishers WHERE LOWER(name) = LOWER('BBC News')
)
INSERT INTO content.source_endpoints (publisher_id, kind, display_name, rss_url, enabled, fetch_interval_minutes)
SELECT bbc.id, 'RSS', 'BBC News - Asia', 'https://feeds.bbci.co.uk/news/world/asia/rss.xml', TRUE, 15
FROM bbc
ON CONFLICT (publisher_id, kind, COALESCE(rss_url, ''), COALESCE(api_provider, ''), COALESCE(api_query, ''))
    DO NOTHING;

WITH bbc AS (
    SELECT id FROM content.publishers WHERE LOWER(name) = LOWER('BBC News')
)
INSERT INTO content.source_endpoints (publisher_id, kind, display_name, rss_url, enabled, fetch_interval_minutes)
SELECT bbc.id, 'RSS', 'BBC News - Europe', 'https://feeds.bbci.co.uk/news/world/europe/rss.xml', TRUE, 15
FROM bbc
ON CONFLICT (publisher_id, kind, COALESCE(rss_url, ''), COALESCE(api_provider, ''), COALESCE(api_query, ''))
    DO NOTHING;

WITH bbc AS (
    SELECT id FROM content.publishers WHERE LOWER(name) = LOWER('BBC News')
)
INSERT INTO content.source_endpoints (publisher_id, kind, display_name, rss_url, enabled, fetch_interval_minutes)
SELECT bbc.id, 'RSS', 'BBC News - Latin America', 'https://feeds.bbci.co.uk/news/world/latin_america/rss.xml', TRUE, 15
FROM bbc
ON CONFLICT (publisher_id, kind, COALESCE(rss_url, ''), COALESCE(api_provider, ''), COALESCE(api_query, ''))
    DO NOTHING;

WITH bbc AS (
    SELECT id FROM content.publishers WHERE LOWER(name) = LOWER('BBC News')
)
INSERT INTO content.source_endpoints (publisher_id, kind, display_name, rss_url, enabled, fetch_interval_minutes)
SELECT bbc.id, 'RSS', 'BBC News - Middle East', 'https://feeds.bbci.co.uk/news/world/middle_east/rss.xml', TRUE, 15
FROM bbc
ON CONFLICT (publisher_id, kind, COALESCE(rss_url, ''), COALESCE(api_provider, ''), COALESCE(api_query, ''))
    DO NOTHING;

WITH bbc AS (
    SELECT id FROM content.publishers WHERE LOWER(name) = LOWER('BBC News')
)
INSERT INTO content.source_endpoints (publisher_id, kind, display_name, rss_url, enabled, fetch_interval_minutes)
SELECT bbc.id, 'RSS', 'BBC News - US & Canada', 'https://feeds.bbci.co.uk/news/world/us_and_canada/rss.xml', TRUE, 15
FROM bbc
ON CONFLICT (publisher_id, kind, COALESCE(rss_url, ''), COALESCE(api_provider, ''), COALESCE(api_query, ''))
    DO NOTHING;

WITH bbc AS (
    SELECT id FROM content.publishers WHERE LOWER(name) = LOWER('BBC News')
)
INSERT INTO content.source_endpoints (publisher_id, kind, display_name, rss_url, enabled, fetch_interval_minutes)
SELECT bbc.id, 'RSS', 'BBC News - Business', 'https://feeds.bbci.co.uk/news/business/rss.xml', TRUE, 15
FROM bbc
ON CONFLICT (publisher_id, kind, COALESCE(rss_url, ''), COALESCE(api_provider, ''), COALESCE(api_query, ''))
    DO NOTHING;

WITH bbc AS (
    SELECT id FROM content.publishers WHERE LOWER(name) = LOWER('BBC News')
)
INSERT INTO content.source_endpoints (publisher_id, kind, display_name, rss_url, enabled, fetch_interval_minutes)
SELECT bbc.id, 'RSS', 'BBC News - Politics', 'https://feeds.bbci.co.uk/news/politics/rss.xml', TRUE, 15
FROM bbc
ON CONFLICT (publisher_id, kind, COALESCE(rss_url, ''), COALESCE(api_provider, ''), COALESCE(api_query, ''))
    DO NOTHING;

WITH bbc AS (
    SELECT id FROM content.publishers WHERE LOWER(name) = LOWER('BBC News')
)
INSERT INTO content.source_endpoints (publisher_id, kind, display_name, rss_url, enabled, fetch_interval_minutes)
SELECT bbc.id, 'RSS', 'BBC News - Science & Environment', 'https://feeds.bbci.co.uk/news/science_and_environment/rss.xml', TRUE, 15
FROM bbc
ON CONFLICT (publisher_id, kind, COALESCE(rss_url, ''), COALESCE(api_provider, ''), COALESCE(api_query, ''))
    DO NOTHING;

WITH bbc AS (
    SELECT id FROM content.publishers WHERE LOWER(name) = LOWER('BBC News')
)
INSERT INTO content.source_endpoints (publisher_id, kind, display_name, rss_url, enabled, fetch_interval_minutes)
SELECT bbc.id, 'RSS', 'BBC News - Technology', 'https://feeds.bbci.co.uk/news/technology/rss.xml', TRUE, 15
FROM bbc
ON CONFLICT (publisher_id, kind, COALESCE(rss_url, ''), COALESCE(api_provider, ''), COALESCE(api_query, ''))
    DO NOTHING;

WITH bbc AS (
    SELECT id FROM content.publishers WHERE LOWER(name) = LOWER('BBC News')
)
INSERT INTO content.source_endpoints (publisher_id, kind, display_name, rss_url, enabled, fetch_interval_minutes)
SELECT bbc.id, 'RSS', 'BBC News - Health', 'https://feeds.bbci.co.uk/news/health/rss.xml', TRUE, 15
FROM bbc
ON CONFLICT (publisher_id, kind, COALESCE(rss_url, ''), COALESCE(api_provider, ''), COALESCE(api_query, ''))
    DO NOTHING;

WITH bbc AS (
    SELECT id FROM content.publishers WHERE LOWER(name) = LOWER('BBC News')
)
INSERT INTO content.source_endpoints (publisher_id, kind, display_name, rss_url, enabled, fetch_interval_minutes)
SELECT bbc.id, 'RSS', 'BBC News - Entertainment & Arts', 'https://feeds.bbci.co.uk/news/entertainment_and_arts/rss.xml', TRUE, 15
FROM bbc
ON CONFLICT (publisher_id, kind, COALESCE(rss_url, ''), COALESCE(api_provider, ''), COALESCE(api_query, ''))
    DO NOTHING;

WITH bbc AS (
    SELECT id FROM content.publishers WHERE LOWER(name) = LOWER('BBC News')
)
INSERT INTO content.source_endpoints (publisher_id, kind, display_name, rss_url, enabled, fetch_interval_minutes)
SELECT bbc.id, 'RSS', 'BBC News - Education', 'https://feeds.bbci.co.uk/news/education/rss.xml', TRUE, 15
FROM bbc
ON CONFLICT (publisher_id, kind, COALESCE(rss_url, ''), COALESCE(api_provider, ''), COALESCE(api_query, ''))
    DO NOTHING;

-- NPR - Top Stories
WITH npr AS (
    SELECT id FROM content.publishers WHERE LOWER(name) = LOWER('NPR')
)
INSERT INTO content.source_endpoints (publisher_id, kind, display_name, rss_url, enabled, fetch_interval_minutes)
SELECT npr.id, 'RSS', 'NPR - Top Stories', 'https://feeds.npr.org/1001/rss.xml', TRUE, 15
FROM npr
ON CONFLICT (publisher_id, kind, COALESCE(rss_url, ''), COALESCE(api_provider, ''), COALESCE(api_query, ''))
    DO NOTHING;

-- The Guardian - UK News
WITH g AS (
    SELECT id FROM content.publishers WHERE LOWER(name) = LOWER('The Guardian')
)
INSERT INTO content.source_endpoints (publisher_id, kind, display_name, rss_url, enabled, fetch_interval_minutes)
SELECT g.id, 'RSS', 'The Guardian - UK News', 'https://www.theguardian.com/uk/rss', TRUE, 15
FROM g
ON CONFLICT (publisher_id, kind, COALESCE(rss_url, ''), COALESCE(api_provider, ''), COALESCE(api_query, ''))
    DO NOTHING;

-- Al Jazeera - All News
WITH aj AS (
    SELECT id FROM content.publishers WHERE LOWER(name) = LOWER('Al Jazeera')
)
INSERT INTO content.source_endpoints (publisher_id, kind, display_name, rss_url, enabled, fetch_interval_minutes)
SELECT aj.id, 'RSS', 'Al Jazeera - All News', 'https://www.aljazeera.com/xml/rss/all.xml', TRUE, 15
FROM aj
ON CONFLICT (publisher_id, kind, COALESCE(rss_url, ''), COALESCE(api_provider, ''), COALESCE(api_query, ''))
    DO NOTHING;

-- Euronews - All News
WITH eu AS (
    SELECT id FROM content.publishers WHERE LOWER(name) = LOWER('Euronews')
)
INSERT INTO content.source_endpoints (publisher_id, kind, display_name, rss_url, enabled, fetch_interval_minutes)
SELECT eu.id, 'RSS', 'Euronews - All News', 'https://www.euronews.com/rss', TRUE, 15
FROM eu
ON CONFLICT (publisher_id, kind, COALESCE(rss_url, ''), COALESCE(api_provider, ''), COALESCE(api_query, ''))
    DO NOTHING;

-- France24 - All News (English)
WITH fr AS (
    SELECT id FROM content.publishers WHERE LOWER(name) = LOWER('France 24')
)
INSERT INTO content.source_endpoints (publisher_id, kind, display_name, rss_url, enabled, fetch_interval_minutes)
SELECT fr.id, 'RSS', 'France24 - All News (English)', 'https://www.france24.com/en/rss', TRUE, 15
FROM fr
ON CONFLICT (publisher_id, kind, COALESCE(rss_url, ''), COALESCE(api_provider, ''), COALESCE(api_query, ''))
    DO NOTHING;

-- ABC Australia - Top Stories
WITH abc AS (
    SELECT id FROM content.publishers WHERE LOWER(name) = LOWER('ABC News (Australia)')
)
INSERT INTO content.source_endpoints (publisher_id, kind, display_name, rss_url, enabled, fetch_interval_minutes)
SELECT abc.id, 'RSS', 'ABC Australia - Top Stories', 'https://www.abc.net.au/news/feed/45910/rss.xml', TRUE, 15
FROM abc
ON CONFLICT (publisher_id, kind, COALESCE(rss_url, ''), COALESCE(api_provider, ''), COALESCE(api_query, ''))
    DO NOTHING;