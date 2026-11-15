CREATE FUNCTION fn_updated_delete_blocked() RETURNS trigger
LANGUAGE plpgsql AS $$
    BEGIN
        RAISE EXCEPTION 'No se puede eliminar o modificar registros en la tabla ledger_entry';
    END;
$$;

-- triger para bloquear la eliminacion o modificacion de registros en la tabla ledger_entry (delete o update)
CREATE TRIGGER trg_updated_delete_blocked
BEFORE DELETE OR UPDATE ON ledger_entry
FOR EACH ROW
EXECUTE FUNCTION fn_updated_delete_blocked();

-- triger para bloquear la eliminacion o modificacion de registros en la tabla ledger_entry (truncate)
CREATE TRIGGER trg_truncate_blocked
BEFORE TRUNCATE ON ledger_entry
FOR EACH STATEMENT
EXECUTE FUNCTION fn_updated_delete_blocked();
