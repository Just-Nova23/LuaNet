-- Run daily from the control-plane maintenance job.
DELETE FROM security_audit WHERE occurred_at < now() - interval '30 days';

