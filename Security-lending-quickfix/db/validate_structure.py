"""Static structural checks only; does not replace execution on MySQL."""
from pathlib import Path
import re
base=Path(__file__).resolve().parent
count=0
for file in sorted(base.glob('0[1-4]_*.sql')):
    sql=file.read_text()
    tables={}
    for match in re.finditer(r'CREATE TABLE (\w+) \((.*?)\) ENGINE=InnoDB;',sql,re.S):
        name,body=match.groups()
        assert name not in tables, (file,name,'duplicate table')
        columns={m.group(1):m.group(2).upper() for m in re.finditer(r'^\s*(\w+)\s+(BIGINT|INT|SMALLINT|BOOLEAN|CHAR\(\d+\)|VARCHAR\(\d+\)|DECIMAL\(\d+,\d+\)|DATETIME\(6\)|DATE|TIME|JSON|TEXT|LONGTEXT|ENUM\([^\n]+?\))',body,re.M)}
        # Regex word boundary after punctuation is not needed for declaration extraction.
        assert columns, (name,'no columns')
        assert not re.search(r',\s*$',body),(name,'trailing comma')
        assert body.count('(')==body.count(')'),(name,'unbalanced parentheses')
        for local,target,remote in re.findall(r'FOREIGN KEY \((\w+)\) REFERENCES (\w+)\((\w+)\)',body):
            assert local in columns,(name,local,'missing source column')
            assert target in tables,(name,target,'target absent or declared later')
            assert remote in tables[target],(target,remote,'missing target column')
            assert columns[local]==tables[target][remote],(name,local,'FK type mismatch')
        assert not re.search(r'REFERENCES \w+\.',body),'cross-database FK'
        tables[name]=columns
    for table,cols in re.findall(r'CREATE INDEX \w+ ON (\w+) \((.*?)\);',sql):
        assert table in tables
        for col in cols.split(','):assert col.strip() in tables[table],(table,col)
    assert 'default_rules JSON' in sql if 'custodian' in file.name else True
    print(file.name, len(tables),'tables: structural checks passed')
    count+=len(tables)
print('Total:',count,'tables. MySQL execution still required.')
