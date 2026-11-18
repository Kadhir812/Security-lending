# Schema validation

`python db/validate_structure.py` performs static checks on the checked-in fresh
schema scripts: FK target/order, FK type, index columns, and parentheses. It
does not connect to MySQL and does not validate migrations or live database
state.
Run service persistence integration tests against disposable databases to
verify actual MySQL schemas.
