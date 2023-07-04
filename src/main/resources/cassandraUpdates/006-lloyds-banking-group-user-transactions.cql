CREATE TABLE IF NOT EXISTS lloyds_banking_group_user_transactions (
    user_id uuid,
    saved_at timestamp,
    transactions_data text,
	PRIMARY KEY(user_id, saved_at)
) WITH CLUSTERING ORDER BY (saved_at DESC);
