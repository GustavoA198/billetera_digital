CREATE FUNCTION fn_audit_log_from_wallet_status() RETURNS trigger
LANGUAGE plpgsql AS $$
    DECLARE
        v_user_id uuid;
    BEGIN
        v_user_id := NULLIF(current_setting('app.current_user_id', true), '')::uuid;
        IF v_user_id IS NULL THEN
                RAISE EXCEPTION 'No se puede auditar el cambio: falta definir app.current_user_id';
        END IF;
        INSERT INTO audit_log (table_name, record_id, action, old_value, new_value, changed_by)
        VALUES ('wallet', OLD.id, TG_OP, OLD.status::text, NEW.status::text, v_user_id);
        RETURN NULL;
    END;
$$;

-- triger para guardar en audit_log los cambios en la tabla wallet.status
CREATE TRIGGER trg_audit_log_from_wallet_status
AFTER UPDATE OF status ON wallet
FOR EACH ROW
WHEN (OLD.status IS DISTINCT FROM NEW.status)
EXECUTE FUNCTION fn_audit_log_from_wallet_status();