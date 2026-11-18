-- 申报草稿版本修复（本地联调暴露）：
-- 1) 草稿还没有提交，submitted_by/submitted_at 允许为空；
-- 2) “版本内容不可变”仅约束已提交版本，草稿允许在提交前更新 form_data/content_hash。

ALTER TABLE application_version
    ALTER COLUMN submitted_by DROP NOT NULL,
    ALTER COLUMN submitted_at DROP NOT NULL;

ALTER TABLE application_version
    ADD CONSTRAINT ck_application_version_submitted
    CHECK (status = 'DRAFT' OR (submitted_by IS NOT NULL AND submitted_at IS NOT NULL));

CREATE OR REPLACE FUNCTION protect_versioned_content()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION '% is versioned and cannot be deleted', TG_TABLE_NAME
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;

    IF TG_TABLE_NAME = 'application_version' THEN
        -- 草稿版本在提交前允许被覆盖；已提交版本的内容永久不可变
        IF OLD.status <> 'DRAFT'
           AND (NEW.form_data IS DISTINCT FROM OLD.form_data
                OR NEW.content_hash IS DISTINCT FROM OLD.content_hash) THEN
            RAISE EXCEPTION 'application_version content is immutable'
                USING ERRCODE = 'integrity_constraint_violation';
        END IF;
    ELSIF TG_TABLE_NAME = 'project_baseline'
       AND (NEW.snapshot IS DISTINCT FROM OLD.snapshot
            OR NEW.snapshot_hash IS DISTINCT FROM OLD.snapshot_hash) THEN
        RAISE EXCEPTION 'project_baseline snapshot is immutable'
            USING ERRCODE = 'integrity_constraint_violation';
    ELSIF TG_TABLE_NAME = 'acceptance_version'
       AND (NEW.snapshot IS DISTINCT FROM OLD.snapshot
            OR NEW.snapshot_hash IS DISTINCT FROM OLD.snapshot_hash) THEN
        RAISE EXCEPTION 'acceptance_version snapshot is immutable'
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;

    RETURN NEW;
END;
$$;
